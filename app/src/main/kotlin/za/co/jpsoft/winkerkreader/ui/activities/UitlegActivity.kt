package za.co.jpsoft.winkerkreader.ui.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.models.CalendarInfo
import za.co.jpsoft.winkerkreader.databinding.ActivityUitlegBinding
import za.co.jpsoft.winkerkreader.di.UserPrefs
import za.co.jpsoft.winkerkreader.ui.fragments.UitlegFunksiesFragment
import za.co.jpsoft.winkerkreader.ui.fragments.UitlegPastoraalFragment
import za.co.jpsoft.winkerkreader.ui.fragments.UitlegVertoonFragment
import za.co.jpsoft.winkerkreader.ui.fragments.UitlegWidgetFragment
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import za.co.jpsoft.winkerkreader.utils.prefs.CalendarPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.PastoralPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.SecurityPrefs
import za.co.jpsoft.winkerkreader.utils.security.BiometricSetupHelper

@AndroidEntryPoint
class UitlegActivity : AuthBaseActivity(), UitlegCalendarSelectionListener {
    @Inject
    @UserPrefs
    lateinit var calendarPrefs: CalendarPrefs
    @Inject
    lateinit var pastoralPrefs: PastoralPrefs

    @Inject
    lateinit var calendarManager: CalendarManager

    @Inject
    lateinit var callMonitorPrefs: CallMonitorPrefs

    // 🆕 Biometric security setup
    @Inject
    override lateinit var securityPrefs: SecurityPrefs

    private lateinit var binding: ActivityUitlegBinding
    private lateinit var biometricSetupHelper: BiometricSetupHelper

    private var availableCalendars: List<CalendarInfo> = emptyList()

    private var selectedCalendarId: Long? = -1L
    private var selectedPastoralCalendarId: Long? = -1L

    private val PERMISSION_REQUEST_CALENDAR = 102
    private var retryCount = 0
    private val MAX_RETRIES = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUitlegBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🆕 Initialize biometric setup helper
        biometricSetupHelper = BiometricSetupHelper(this)

        selectedCalendarId = callMonitorPrefs.callCalendarId
        selectedPastoralCalendarId = pastoralPrefs.pastoralCalendarId

        val pagerAdapter = UitlegPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 3

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Vertoon"
                1 -> "Pastoraal"
                2 -> "Funksies"
                3 -> "Widget"
                else -> ""
            }
        }.attach()

        tryLoadCalendars()
    }

    override fun onResume() {
        super.onResume()

        // 🆕 Re-check biometric availability
        // User might have gone to Settings and set up biometric/PIN
        // Or they might have cleared security settings
        if (securityPrefs.biometricEnabled) {
            if (!biometricSetupHelper.reCheckAuthAvailability()) {
                // Biometric was enabled but is NO LONGER available
                // (user cleared security settings)
                securityPrefs.biometricEnabled = false

                if (BuildConfig.DEBUG) {
                    Log.w("UitlegActivity", "Biometric was enabled but is now unavailable")
                }

                // Notify user
                biometricSetupHelper.showInfoDialog(
                    "🔒 Sekuriteit Uitgeskakel",
                    "Jou aparaat se sekuriteit is nie meer gekonfigureer nie. " +
                            "WinkerkReader se beveiligde slot is om hierdie rede afgeskakel."
                )

                // Update UI in fragment (see UitlegVertoonFragment.kt changes)
                notifyBiometricStatusChanged(false)
            }
        }
    }

    /**
     * Called by fragments when biometric toggle state changes.
     * Validates that device auth is available before allowing enable.
     */
    fun requestBiometricEnable(): Boolean {
        // Check if device has auth configured
        if (!biometricSetupHelper.isAuthAvailable()) {
            if (BuildConfig.DEBUG) {
                Log.w("UitlegActivity", "Biometric enable requested but auth not available")
            }

            // Guide user to set up device security
            biometricSetupHelper.checkAndPromptSetup()
            return false  // Deny enable
        }

        // ✅ Auth is available – allow enable
        securityPrefs.biometricEnabled = true
        return true
    }

    /**
     * Called by fragments when biometric toggle is disabled.
     */
    fun requestBiometricDisable() {
        securityPrefs.biometricEnabled = false
    }

    /**
     * Notifies fragments that biometric status has changed (for UI updates).
     * Call this after changing biometric settings programmatically.
     */
    private fun notifyBiometricStatusChanged(enabled: Boolean) {
        val vertoonFragment = supportFragmentManager.fragments
            .filterIsInstance<UitlegVertoonFragment>()
            .firstOrNull()

        vertoonFragment?.updateBiometricToggle(enabled)
    }

    private fun tryLoadCalendars() {
        if (!hasCalendarPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                CALENDAR_PERMISSIONS,
                PERMISSION_REQUEST_CALENDAR
            )
            return
        }
        availableCalendars = calendarManager.getAvailableCalendars() ?: emptyList()
        if (BuildConfig.DEBUG) {
            Log.d("UitlegActivity", "Available calendars: ${availableCalendars.size}")
        }
        binding.viewPager.post {
            findAndSetFragments()
        }
    }

    private fun findAndSetFragments() {
        val fragments = supportFragmentManager.fragments
        var pastoraal: UitlegPastoraalFragment? = null
        var funksies: UitlegFunksiesFragment? = null
        for (fragment in fragments) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    "UitlegActivity",
                    "Fragment: ${fragment.javaClass.simpleName}, tag: ${fragment.tag}"
                )
            }
            when (fragment) {
                is UitlegPastoraalFragment -> pastoraal = fragment
                is UitlegFunksiesFragment -> funksies = fragment
            }
        }

        if (pastoraal != null && funksies != null) {
            if (availableCalendars.isNotEmpty()) {
                val adapter = createCalendarAdapter()
                pastoraal.setPastoralCalendarSpinner(adapter, selectedPastoralCalendarId ?: -1L)
                funksies.setCallCalendarSpinner(adapter, selectedCalendarId ?: -1L)
            } else {
                val emptyAdapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    listOf("Geen kalenders gevind")
                )
                emptyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                pastoraal.setPastoralCalendarSpinner(emptyAdapter, -1L)
                funksies.setCallCalendarSpinner(emptyAdapter, -1L)
            }
            retryCount = 0
        } else {
            retryCount++
            if (retryCount <= MAX_RETRIES) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "UitlegActivity",
                        "Retry $retryCount/$MAX_RETRIES: Pastoraal found: ${pastoraal != null}, Funksies found: ${funksies != null}"
                    )
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    findAndSetFragments()
                }, 500)
            } else {
                val emptyAdapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    listOf("Foute: kon fragmente nie vind nie")
                )
                emptyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                val lastTry = supportFragmentManager.fragments
                for (f in lastTry) {
                    when (f) {
                        is UitlegPastoraalFragment -> f.setPastoralCalendarSpinner(
                            emptyAdapter,
                            -1L
                        )
                        is UitlegFunksiesFragment -> f.setCallCalendarSpinner(emptyAdapter, -1L)
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CALENDAR) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                tryLoadCalendars()
            } else {
                val emptyAdapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    listOf("Toestemming benodig")
                )
                emptyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                val fragments = supportFragmentManager.fragments
                for (f in fragments) {
                    when (f) {
                        is UitlegPastoraalFragment -> f.setPastoralCalendarSpinner(
                            emptyAdapter,
                            -1L
                        )
                        is UitlegFunksiesFragment -> f.setCallCalendarSpinner(emptyAdapter, -1L)
                    }
                }
            }
        }
    }

    private fun createCalendarAdapter(): ArrayAdapter<String> {
        val calendarNames = availableCalendars.map { "${it.displayName} (${it.accountName})" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, calendarNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }

    private fun hasCalendarPermissions(): Boolean {
        return CALENDAR_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ─── UitlegCalendarSelectionListener ────────────────────────────────────
    override fun onPastoralCalendarSelected(id: Long) {
        selectedPastoralCalendarId = id
    }

    override fun onCallCalendarSelected(id: Long) {
        selectedCalendarId = id
    }

    fun getCalendarIdAtPosition(position: Int): Long {
        return if (position < availableCalendars.size) availableCalendars[position].id else -1L
    }

    fun savePastoralCalendarId() {
        pastoralPrefs.pastoralCalendarId = selectedPastoralCalendarId
    }

    fun saveCallCalendarId() {
        callMonitorPrefs.callCalendarId = selectedCalendarId
    }

    companion object {
        val CALENDAR_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
    }
}

class UitlegPagerAdapter(activity: androidx.appcompat.app.AppCompatActivity) :
    FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> UitlegVertoonFragment()
            1 -> UitlegPastoraalFragment()
            2 -> UitlegFunksiesFragment()
            3 -> UitlegWidgetFragment()
            else -> UitlegVertoonFragment()
        }
    }
}