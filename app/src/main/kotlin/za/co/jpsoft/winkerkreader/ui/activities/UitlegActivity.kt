package za.co.jpsoft.winkerkreader.ui.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
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
import za.co.jpsoft.winkerkreader.utils.prefs.PastoralPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import javax.inject.Inject

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
    private lateinit var binding: ActivityUitlegBinding
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

class UitlegPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
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