package za.co.jpsoft.winkerkreader.services

import dagger.hilt.android.AndroidEntryPoint
import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.*
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.ui.bottomsheets.StelHerinneringBottomSheet
import za.co.jpsoft.winkerkreader.ui.bottomsheets.VoegNotaByBottomSheet
import za.co.jpsoft.winkerkreader.utils.telephony.CallerInfoResolver
import za.co.jpsoft.winkerkreader.utils.telephony.CallerInfoResult
import za.co.jpsoft.winkerkreader.utils.work.ForegroundServiceHelper
import java.lang.ref.WeakReference
import android.content.pm.ServiceInfo
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity

class OproepDetailService : Service() {

    companion object {
        private const val TAG = "OproepDetailService"
        const val EXTRA_CALLER_ID = "caller_id"
        const val EXTRA_DISPLAY_NAME = "display_name"
        const val ACTION_CALL_ANSWERED = "za.co.jpsoft.winkerkreader.ACTION_CALL_ANSWERED"
        const val ACTION_CALL_ENDED = "za.co.jpsoft.winkerkreader.ACTION_CALL_ENDED"

        private var serviceInstance: WeakReference<OproepDetailService>? = null
        @Volatile
        var isOn = false; private set

        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return false
            val serviceName = "${context.packageName}.${OproepDetailService::class.java.simpleName}"
            return try {
                manager.getRunningServices(Int.MAX_VALUE)
                    .any { it.service.className == serviceName }
            } catch (e: Exception) {
                false
            }
        }

        private var lastProcessedNumber = ""
        private var lastProcessedTime = 0L

        fun canProcessCall(number: String): Boolean {
            synchronized(this) {
                val now = System.currentTimeMillis()
                if (now - lastProcessedTime > 30_000L) lastProcessedNumber = ""
                return if (lastProcessedNumber == number && now - lastProcessedTime < 500) false
                else {
                    lastProcessedNumber = number; lastProcessedTime = now; true
                }
            }
        }

        fun resetTrackers() {
            synchronized(this) { lastProcessedNumber = ""; lastProcessedTime = 0L }
        }

        fun dismissOnAnswered() {
            serviceInstance?.get()?.stopSelf()
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var viewAdded = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentMemberGuid: String? = null
    private var currentMemberName: String? = null
    private var currentFamilyHeadGuid: String? = null
    private var multipleMembers: List<CallerInfoResult.Member>? = null

    private var stopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        serviceInstance = WeakReference(this)
        isOn = true
        createForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CALL_ANSWERED) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Call answered — dismissing overlay")
            stopJob?.cancel()
            resetSelectionState()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CALL_ENDED) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Call ended — starting delayed dismissal timer")
            scheduleDelayedStop()
            return START_NOT_STICKY
        }

        val callerId = intent?.getStringExtra(EXTRA_CALLER_ID) ?: ""
        val displayNameExtra = intent?.getStringExtra(EXTRA_DISPLAY_NAME) ?: ""

        if (!isValidPhoneNumber(callerId) && displayNameExtra.isEmpty()) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "No valid caller id or display name, ignoring start command"
            )
            return START_NOT_STICKY
        }
        if (callerId.isNotEmpty() && !canProcessCall(callerId)) return START_NOT_STICKY

        stopJob?.cancel()
        resetSelectionState()

        serviceScope.launch {
            val lookupKey = if (callerId.isNotEmpty() && callerId != "Unknown" && callerId != "XXXXXXXXXX") {
                callerId
            } else {
                displayNameExtra
            }

            val context = this@OproepDetailService
            val result = if (lookupKey.isNotEmpty()) {
                CallerInfoResolver.resolve(lookupKey, context)
            } else {
                CallerInfoResult.Unknown
            }

            val finalName = when (result) {
                is CallerInfoResult.Member -> {
                    currentMemberGuid = result.guid
                    currentFamilyHeadGuid = result.familyHeadGuid
                    multipleMembers = null
                    buildString {
                        append(result.name)
                        if (!result.gemeente.isNullOrBlank()) append(" (${result.gemeente})")
                    }
                }

                is CallerInfoResult.Contact -> {
                    currentMemberGuid = null
                    currentFamilyHeadGuid = null
                    multipleMembers = null
                    result.name
                }
                is CallerInfoResult.MultipleMembers -> {
                    multipleMembers = result.members
                    currentMemberGuid = null
                    currentFamilyHeadGuid = null
                    null // we'll show radio group instead
                }
                CallerInfoResult.Unknown -> {
                    currentMemberGuid = null
                    currentFamilyHeadGuid = null
                    multipleMembers = null
                    // Set currentMemberName to the best available identifier for search
                    currentMemberName = when {
                        displayNameExtra.isNotEmpty() -> displayNameExtra
                        callerId.isNotEmpty() && callerId != "Unknown" && callerId != "XXXXXXXXXX" -> callerId
                        else -> null
                    }
                    // Return the name to display (same)
                    currentMemberName
                }
            }

            if (finalName == null && multipleMembers == null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "No name to display")
                withContext(Dispatchers.Main) { stopSelf() }
                return@launch
            }

            currentMemberName = finalName
            val displayNameForCaller = finalName ?: ""
            withContext(Dispatchers.Main) { showCaller(callerId, displayNameForCaller) }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopJob?.cancel()
        resetSelectionState()
        serviceScope.cancel()
        isOn = false
        serviceInstance = null
        resetTrackers()

        if (::floatingView.isInitialized && viewAdded) {
            try {
                windowManager.removeView(floatingView); viewAdded = false
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error removing floating view", e)
            }
        }

        getSharedPreferences(PREFS_USER_INFO, 0).edit { putString("CallerNumber", "XXXXXXXXXX") }
        super.onDestroy()
    }

    private fun createForegroundNotification() {
        val channelId = "WinkerkReader"
        val channelName = "Oproep Service"
        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW).apply {
            lightColor = Color.BLUE
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setOngoing(true)
            .setSmallIcon(R.drawable.img)
            .setContentTitle("Caller ID Service")
            .setContentText("Monitoring incoming calls")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build()

        try {
            ForegroundServiceHelper.startForeground(
                service = this,
                id = 2,
                notification = notification,
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(
                TAG,
                "Failed to promote OproepDetailService to foreground",
                e
            )
        }
    }

    private fun isValidPhoneNumber(phoneNumber: String) =
        phoneNumber.isNotEmpty() && phoneNumber != "XXXXXXXXXX" && phoneNumber != "Unknown"

    private fun showCaller(callerId: String, displayName: String) {
        getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE).edit {
            putString(
                "CallerNumber",
                callerId
            )
        }

        if (::floatingView.isInitialized && viewAdded) {
            try {
                windowManager.removeView(floatingView); viewAdded = false
            } catch (e: Exception) {
            }
        }

        val themedContext = ContextThemeWrapper(this, R.style.Theme_WinkerkReader)
        floatingView = LayoutInflater.from(themedContext).inflate(R.layout.oproepfloat, null)
        val callerTextView = floatingView.findViewById<TextView>(R.id.oproepnommer)
        val radioGroup = floatingView.findViewById<RadioGroup>(R.id.member_radio_group)

        val isMultiple = multipleMembers?.isNotEmpty() == true

        if (isMultiple) {
            callerTextView.visibility = View.GONE
            radioGroup.visibility = View.VISIBLE
            populateMemberList(radioGroup)
        } else {
            callerTextView.text = displayName
            callerTextView.visibility = View.VISIBLE
            radioGroup.visibility = View.GONE
        }

        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
            )
            stopSelf()
            return
        }

        createFloatingWindow()
        setupClickListeners(callerTextView)
        setupTouchListener()
        setupQuickActionButtons()
        viewAdded = true
    }

    private fun populateMemberList(radioGroup: RadioGroup) {
        val members = multipleMembers ?: return
        radioGroup.removeAllViews()
        members.forEachIndexed { index, member ->
            val radioButton = RadioButton(floatingView.context).apply {
                text = buildString {
                    append(member.name)
                    if (!member.gemeente.isNullOrBlank()) append(" (${member.gemeente})")
                }
                id = View.generateViewId()
                tag = index
            }
            radioGroup.addView(radioButton)
        }

        setActionButtonsEnabled(false)

        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            // Ignore when clearing (checkedId == -1)
            if (checkedId == -1) return@setOnCheckedChangeListener

            val radioButton = group.findViewById<RadioButton>(checkedId)
            val index = radioButton.tag as Int
            val selected = members[index]
            currentMemberGuid = selected.guid
            currentFamilyHeadGuid = selected.familyHeadGuid
            currentMemberName = selected.name
            setActionButtonsEnabled(true)
        }
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        if (!::floatingView.isInitialized) return
        floatingView.findViewById<View>(R.id.action_note)?.isEnabled = enabled
        floatingView.findViewById<View>(R.id.action_reminder)?.isEnabled = enabled
        floatingView.findViewById<View>(R.id.action_note)?.alpha = if (enabled) 1.0f else 0.4f
        floatingView.findViewById<View>(R.id.action_reminder)?.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun createFloatingWindow() {
        val screenHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val metrics = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            metrics.heightPixels
        }

        val params = createWindowLayoutParams().apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (screenHeight * 0.32f).toInt()
        }
        try {
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to add floating view", e)
            stopSelf()
        }
    }

    private fun createWindowLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    )

    private fun setupClickListeners(callerTextView: TextView) {
        floatingView.findViewById<ImageView>(R.id.close_btn)?.setOnClickListener {
            stopJob?.cancel()
            resetSelectionState()
            stopSelf()
        }
        callerTextView.setOnClickListener {
            (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(
                ClipData.newPlainText("caller_info", callerTextView.text)
            )
        }
    }

    private fun openSearchInMainApp() {
        val searchQuery = currentMemberName ?: ""
        if (searchQuery.isBlank()) return

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("search_query", searchQuery)
            putExtra("from_oproep", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun setupQuickActionButtons() {
        val hasMember = !currentMemberGuid.isNullOrBlank() || multipleMembers?.isNotEmpty() == true
        if (!hasMember) {
            // No member at all – show only the search button (if we have a name/number)
            floatingView.findViewById<View>(R.id.quick_actions_row)?.visibility = View.VISIBLE
            floatingView.findViewById<View>(R.id.action_note)?.visibility = View.GONE
            floatingView.findViewById<View>(R.id.action_reminder)?.visibility = View.GONE
            // Show search button if we have something to search
            val searchButton = floatingView.findViewById<View>(R.id.action_search)
            if (!currentMemberName.isNullOrBlank()) {
                searchButton.visibility = View.VISIBLE
                searchButton.setOnClickListener {
                    openSearchInMainApp()
                    stopJob?.cancel()
                    stopSelf()
                }
            } else {
                searchButton.visibility = View.GONE
            }
            return
        }

        floatingView.findViewById<View>(R.id.quick_actions_row)?.visibility = View.VISIBLE
        floatingView.findViewById<View>(R.id.action_search)?.visibility = View.GONE

        val isMultiple = multipleMembers?.isNotEmpty() == true
        val hasSelection = !currentMemberGuid.isNullOrBlank()
        setActionButtonsEnabled(!isMultiple || hasSelection)

        floatingView.findViewById<View>(R.id.action_note)?.setOnClickListener {
            if (currentMemberGuid.isNullOrBlank()) return@setOnClickListener
            openVoegNotaBy()
            stopJob?.cancel()
            stopSelf()
        }

        floatingView.findViewById<View>(R.id.action_reminder)?.setOnClickListener {
            if (currentMemberGuid.isNullOrBlank()) return@setOnClickListener
            openStelHerinnering()
            stopJob?.cancel()
            stopSelf()
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Quick action buttons setup. Multiple: ${multipleMembers?.size ?: 0}, selected: $currentMemberGuid"
            )
        }
    }

    private fun openVoegNotaBy() {
        if (!currentMemberGuid.isNullOrBlank()) {
            launchNoteForMember(currentMemberGuid!!, currentFamilyHeadGuid, currentMemberName)
        } else {
            if (BuildConfig.DEBUG) Log.w(TAG, "Cannot open note: no member selected")
        }
    }

    private fun openStelHerinnering() {
        if (!currentMemberGuid.isNullOrBlank()) {
            launchReminderForMember(currentMemberGuid!!, currentFamilyHeadGuid)
        } else {
            if (BuildConfig.DEBUG) Log.w(TAG, "Cannot open reminder: no member selected")
        }
    }

    private fun launchNoteForMember(guid: String, familyHeadGuid: String?, name: String?) {
        val intent = Intent(this, VoegNotaByBottomSheetLauncher::class.java).apply {
            putExtra("member_guid", guid)
            putExtra("family_head_guid", familyHeadGuid)
            putExtra("member_name", name ?: "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun launchReminderForMember(guid: String, familyHeadGuid: String?) {
        val intent = Intent(this, StelHerinneringBottomSheetLauncher::class.java).apply {
            putExtra("member_guid", guid)
            putExtra("family_head_guid", familyHeadGuid)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun resetSelectionState() {
        currentMemberGuid = null
        currentFamilyHeadGuid = null
        currentMemberName = null

        if (::floatingView.isInitialized) {
            floatingView.findViewById<RadioGroup>(R.id.member_radio_group)?.apply {
                // Detach listener to prevent triggering during clear
                setOnCheckedChangeListener(null)
                visibility = View.GONE
                clearCheck()
            }
            floatingView.findViewById<TextView>(R.id.oproepnommer)?.visibility = View.VISIBLE
            floatingView.findViewById<View>(R.id.quick_actions_row)?.visibility = View.GONE
            setActionButtonsEnabled(false)
        }
    }

    private fun setupTouchListener() {
        val params = floatingView.layoutParams as? WindowManager.LayoutParams ?: return
        floatingView.findViewById<View>(R.id.oproepfloaterbase)?.setOnTouchListener(
            FloatingViewTouchListener(params, windowManager, floatingView)
        )
    }

    private fun scheduleDelayedStop() {
        stopJob?.cancel()
        val timeoutSeconds = getTimeoutFromPrefs()
        if (timeoutSeconds <= 0) {
            stopSelf(); return
        }
        stopJob = serviceScope.launch {
            delay(timeoutSeconds * 1000L)
            withContext(Dispatchers.Main) {
                resetSelectionState()
                stopSelf()
            }
        }
    }

    private fun getTimeoutFromPrefs(): Int {
        val prefs = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)
        return (prefs.getString("oproep_timeout", "5") ?: "5").toIntOrNull() ?: 5
    }

    private class FloatingViewTouchListener(
        private val params: WindowManager.LayoutParams,
        private val windowManager: WindowManager,
        private val floatingView: View
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    return true
                }
            }
            return false
        }
    }
}

// ─── Helper Activities (unchanged) ──────────────────────────────────────────

@AndroidEntryPoint
class VoegNotaByBottomSheetLauncher : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val memberGuid = intent.getStringExtra("member_guid") ?: run { finish(); return }
        val familyHeadGuid = intent.getStringExtra("family_head_guid")
        val memberName = intent.getStringExtra("member_name") ?: ""
        val sheet = VoegNotaByBottomSheet.newInstance(memberGuid, familyHeadGuid, memberName)
        showBottomSheetAndFinishOnDismiss(sheet, VoegNotaByBottomSheet.TAG)
    }

    private fun showBottomSheetAndFinishOnDismiss(sheet: DialogFragment, tag: String) {
        sheet.show(supportFragmentManager, tag)
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?
                ) {
                    if (f == sheet) {
                        (f as? DialogFragment)?.dialog?.setOnDismissListener {
                            finish()
                            supportFragmentManager.unregisterFragmentLifecycleCallbacks(this)
                        }
                    }
                }
            },
            true
        )
    }
}

@AndroidEntryPoint
class StelHerinneringBottomSheetLauncher : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val memberGuid = intent.getStringExtra("member_guid") ?: run { finish(); return }
        val familyHeadGuid = intent.getStringExtra("family_head_guid")
        val sheet = StelHerinneringBottomSheet.newInstance(memberGuid, familyHeadGuid)
        showBottomSheetAndFinishOnDismiss(sheet, StelHerinneringBottomSheet.TAG)
    }

    private fun showBottomSheetAndFinishOnDismiss(sheet: DialogFragment, tag: String) {
        sheet.show(supportFragmentManager, tag)
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?
                ) {
                    if (f == sheet) {
                        (f as? DialogFragment)?.dialog?.setOnDismissListener {
                            finish()
                            supportFragmentManager.unregisterFragmentLifecycleCallbacks(this)
                        }
                    }
                }
            },
            true
        )
    }
}