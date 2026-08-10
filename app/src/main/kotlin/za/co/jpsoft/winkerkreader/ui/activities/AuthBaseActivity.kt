package za.co.jpsoft.winkerkreader.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import za.co.jpsoft.winkerkreader.utils.prefs.SecurityPrefs
import za.co.jpsoft.winkerkreader.utils.security.AppAuthGuard
import za.co.jpsoft.winkerkreader.utils.security.AppAuthState

@AndroidEntryPoint
abstract class AuthBaseActivity : BaseActivity() {

    @Inject
    open lateinit var securityPrefs: SecurityPrefs

    private var credentialCallback: ((Boolean) -> Unit)? = null

    // Register safely during activity creation phase
    private val credentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val success = result.resultCode == RESULT_OK
        credentialCallback?.invoke(success)
        credentialCallback = null
    }

    fun launchDeviceCredential(intent: Intent, callback: (Boolean) -> Unit) {
        credentialCallback = callback
        credentialLauncher.launch(intent)
    }

    val appAuthGuard by lazy {
        AppAuthGuard(
            activity = this,
            securityPrefs = securityPrefs,
            startCredentialIntent = { intent, callback ->
                credentialCallback = callback
                credentialLauncher.launch(intent)
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && securityPrefs.lockOnRestart) {
            AppAuthState.resetForFreshLaunch()
        }
    }

    override fun onResume() {
        super.onResume()
        appAuthGuard.checkOnResume(onAuthenticated = { onResumeAfterAuth() })
    }

    open fun onResumeAfterAuth() {}
}