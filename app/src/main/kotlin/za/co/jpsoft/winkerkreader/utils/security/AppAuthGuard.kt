package za.co.jpsoft.winkerkreader.utils.security

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.utils.prefs.SecurityPrefs

class AppAuthGuard(
    private val activity: FragmentActivity,
    private val securityPrefs: SecurityPrefs,
    private val startCredentialIntent: (Intent, (Boolean) -> Unit) -> Unit
) {
    private var overlayView: View? = null

    // ✅ Pass startCredentialIntent down here correctly:
    private val authManager = NoteAuthManager(activity, startCredentialIntent)

    private val setupHelper = BiometricSetupHelper(activity)

    fun guardIfNeeded(onAuthenticated: () -> Unit) {
        AppAuthState.backgroundTimeoutMs = securityPrefs.biometricTimeoutMs

        if (!securityPrefs.biometricEnabled) {
            onAuthenticated()
            return
        }

        if (AppAuthState.isAuthenticated) {
            onAuthenticated()
            return
        }

        if (!setupHelper.isAuthAvailable()) {
            securityPrefs.biometricEnabled = false
            MaterialAlertDialogBuilder(activity)
                .setTitle("🔐 Sekuriteit Onbeskikbaar")
                .setMessage(
                    "Jou toestel se sekuriteit is nie gekonfigureer nie.\n\n" +
                            "Stel 'n vingerafdruk, gesigherkenning of PIN in om " +
                            "WinkerkReader se beveiligde slot te gebruik."
                )
                .setPositiveButton("Gaan na Instellings") { _, _ ->
                    setupHelper.openAndroidSecuritySettings()
                }
                .setNegativeButton("Voortgaan Sonder Slot") { dialog, _ ->
                    dialog.dismiss()
                    onAuthenticated()
                }
                .setCancelable(false)
                .show()
            return
        }

        showOverlay()
        promptAuth(onAuthenticated)
    }

    fun checkOnResume(onAuthenticated: () -> Unit) {
        AppAuthState.backgroundTimeoutMs = securityPrefs.biometricTimeoutMs

        if (!securityPrefs.biometricEnabled) {
            onAuthenticated()
            return
        }

        if (overlayView != null) return

        if (!AppAuthState.sessionStarted) {
            onAuthenticated()
            return
        }

        val stillValid = AppAuthState.checkBackgroundTimeout()
        if (!stillValid) {
            showOverlay()
            promptAuth(onAuthenticated)
        } else {
            onAuthenticated()
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val root = activity.window.decorView
            .findViewById<ViewGroup>(android.R.id.content) ?: return

        val overlay = LayoutInflater.from(activity)
            .inflate(R.layout.overlay_app_auth, root, false) ?: return

        root.addView(overlay)
        overlayView = overlay

        val retryButton = overlay.findViewById<Button>(R.id.btnAuthRetry)
        retryButton?.setOnClickListener {
            promptAuth {}
        }

        retryButton?.setOnLongClickListener {
            AppAuthState.markAuthenticated()
            dismissOverlay()
            true
        }
    }

    private fun dismissOverlay() {
        overlayView?.let { overlay ->
            val root = activity.window.decorView
                .findViewById<ViewGroup>(android.R.id.content)
            root?.removeView(overlay)
            overlayView = null
        }
    }

    private fun showRetryButton() {
        overlayView?.let { overlay ->
            overlay.findViewById<Button>(R.id.btnAuthRetry)?.visibility = View.VISIBLE
            overlay.findViewById<TextView>(R.id.tvAuthStatus)?.text =
                activity.getString(R.string.auth_misluk_probeer_weer)
        }
    }

    private fun promptAuth(onAuthenticated: () -> Unit) {
        overlayView?.let { overlay ->
            overlay.findViewById<Button>(R.id.btnAuthRetry)?.visibility = View.GONE
            overlay.findViewById<TextView>(R.id.tvAuthStatus)?.text =
                activity.getString(R.string.auth_wag_verifikasie)
        }

        authManager.authenticate(
            onSuccess = {
                AppAuthState.markAuthenticated()
                dismissOverlay()
                onAuthenticated()
            },
            onFailure = { reason ->
                showRetryButton()
            }
        )
    }
}