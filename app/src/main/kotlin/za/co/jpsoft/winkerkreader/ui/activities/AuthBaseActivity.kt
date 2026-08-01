package za.co.jpsoft.winkerkreader.ui.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import za.co.jpsoft.winkerkreader.utils.AppAuthGuard
import za.co.jpsoft.winkerkreader.utils.prefs.SecurityPrefs
import javax.inject.Inject

@AndroidEntryPoint
abstract class AuthBaseActivity : BaseActivity() {

    @Inject
    lateinit var securityPrefs: SecurityPrefs

    // Changed from private to protected so MainActivityInitializer can access it
    val appAuthGuard by lazy { AppAuthGuard(this, securityPrefs) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        appAuthGuard.checkOnResume(onAuthenticated = { onResumeAfterAuth() })
    }

    open fun onResumeAfterAuth() {}
}