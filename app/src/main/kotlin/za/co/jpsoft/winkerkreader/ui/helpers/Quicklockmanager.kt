package za.co.jpsoft.winkerkreader.ui.helpers

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.utils.security.AppAuthGuard
import za.co.jpsoft.winkerkreader.utils.prefs.SecurityPrefs

/**
 * Quick Lock Manager – handles menu integration and locking logic.
 *
 * Usage:
 *   - In onCreateOptionsMenu: quickLockManager.setupQuickLockMenuItem(menu)
 *   - In onOptionsItemSelected: quickLockManager.onMenuItemSelected(item)
 */
class QuickLockManager(
    private val context: Context,
    private val appAuthGuard: AppAuthGuard,
    private val securityPrefs: SecurityPrefs
) {
    // No const – use a regular val, or just inline R.id.menu_lock_app_now
    private val menuItemId = R.id.menu_lock_app_now

    /**
     * Locates the existing "Lock App Now" menu item (from XML) and sets its visibility
     * based on biometric enablement.
     */
    fun setupQuickLockMenuItem(menu: Menu?): MenuItem? {
        if (menu == null) return null
        val item = menu.findItem(menuItemId)
        item?.isVisible = securityPrefs.biometricEnabled
        // (Optional) pin to toolbar: item?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return item
    }

    /**
     * Handles the menu item selection. Returns true if handled.
     */
    fun onMenuItemSelected(item: MenuItem): Boolean {
        if (item.itemId != menuItemId) return false
        performQuickLock()
        return true
    }

    /**
     * Confirmation dialog (optional).
     */
    fun showLockConfirmation() {
        MaterialAlertDialogBuilder(context)
            .setTitle("🔐 Lock App?")
            .setMessage(
                "This will lock all confidential pastoral notes. " +
                        "You'll need to authenticate again on next access.\n\n" +
                        "Lock now?"
            )
            .setPositiveButton("Lock") { _, _ -> performQuickLock() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun performQuickLock() {
        appAuthGuard.lockAppNow()
        Toast.makeText(context, "🔐 App locked. PIN required on next access.", Toast.LENGTH_SHORT)
            .show()
    }

    /**
     * Update visibility when biometric settings change at runtime.
     */
    fun updateMenuItemVisibility(menu: Menu?) {
        menu?.findItem(menuItemId)?.isVisible = securityPrefs.biometricEnabled
    }
}