package za.co.jpsoft.winkerkreader.ui.activities

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.utils.LocaleHelper

/**
 * Minimal base for all Activities.
 * - Applies edge‑to‑edge insets.
 * - Provides no navigation or authentication logic.
 * Activities that need app‑lock should extend [AuthBaseActivity].
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleHelper.getPersistedLanguage(newBase)
        val context = LocaleHelper.setLocale(newBase, languageCode)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply locale again before setContentView (safe)
        val languageCode = LocaleHelper.getPersistedLanguage(this)
        LocaleHelper.setLocale(this, languageCode)
        //applyRefreshRate()
    }

    private fun applyRefreshRate() {
        val prefs = getSharedPreferences("WinkerkReader_UserInfo", MODE_PRIVATE)
        val force60Hz = prefs.getBoolean("force_60hz", false)
        val layoutParams = window.attributes
        layoutParams.preferredRefreshRate = if (force60Hz) 60.0f else 0f
        window.attributes = layoutParams
    }
}