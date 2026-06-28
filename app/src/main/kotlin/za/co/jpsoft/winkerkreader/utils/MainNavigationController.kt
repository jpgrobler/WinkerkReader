// File: utils/MainNavigationController.kt
package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import za.co.jpsoft.winkerkreader.ui.activities.ArgiefListActivity
import za.co.jpsoft.winkerkreader.ui.activities.BedieningActivity
import za.co.jpsoft.winkerkreader.ui.activities.CallLogActivity
import za.co.jpsoft.winkerkreader.ui.activities.LaaiDatabasisActivity
import za.co.jpsoft.winkerkreader.ui.activities.LidmaatDetailActivity
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.activities.PermissionsActivity
import za.co.jpsoft.winkerkreader.ui.activities.RegistreerActivity
import za.co.jpsoft.winkerkreader.ui.activities.TemplateEditorActivity
import za.co.jpsoft.winkerkreader.ui.activities.TemplateManagerActivity
import za.co.jpsoft.winkerkreader.ui.activities.UitlegActivity
import za.co.jpsoft.winkerkreader.ui.activities.VerjaarSmsActivity

/**
 * Centralised navigation for the entire app.
 * All activity transitions should go through this controller.
 * When migrating to Navigation Component, only the internal implementation
 * of these methods needs to change – call sites stay identical.
 */
class MainNavigationController(private val context: Context) {

    // ---- Existing methods (already used) ----
    fun navigateToBediening() {
        BedieningActivity.launch(context)
    }

    fun navigateToBediening(reminderId: String) {
        BedieningActivity.launch(context, reminderId)
    }

    fun navigateToUitleg() {
        context.startActivity(Intent(context, UitlegActivity::class.java))
    }

    fun navigateToLaaiDatabasis() {
        context.startActivity(Intent(context, LaaiDatabasisActivity::class.java))
    }

    fun navigateToRegistreer() {
        context.startActivity(Intent(context, RegistreerActivity::class.java))
    }

    fun navigateToSmsVerjaar() {
        context.startActivity(Intent(context, VerjaarSmsActivity::class.java))
    }

    fun navigateToArgief() {
        context.startActivity(Intent(context, ArgiefListActivity::class.java))
    }

    fun navigateToCallLog() {
        context.startActivity(Intent(context, CallLogActivity::class.java))
    }

    fun navigateToPermissions() {
        context.startActivity(Intent(context, PermissionsActivity::class.java))
    }

    // ---- New methods for other transitions ----

    /**
     * Navigate to MainActivity, optionally with extras.
     * Used by splash and other screens that return to the main list.
     */
    fun navigateToMain(extras: Bundle? = null) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extras?.let { putExtras(it) }
        }
        context.startActivity(intent)
    }

    /**
     * Open LidmaatDetail for a specific member.
     * Supports both direct GUID and content URI based opening.
     */
    fun navigateToLidmaatDetail(
        memberGuid: String,
        recordStatus: String = "0",
        memberId: Long? = null
    ) {
        val intent = Intent(context, LidmaatDetailActivity::class.java).apply {
            putExtra(LidmaatDetailActivity.EXTRA_MEMBER_GUID, memberGuid)
            putExtra("RECORD_STATUS", recordStatus)
            memberId?.let {
                data = android.content.ContentUris.withAppendedId(
                    za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.CONTENT_URI,
                    it
                )
            }
        }
        context.startActivity(intent)
    }

    /**
     * Open TemplateManager (list of templates).
     */
    fun navigateToTemplateManager() {
        TemplateManagerActivity.launch(context)
    }

    /**
     * Open TemplateEditor for a specific template.
     */
    fun navigateToTemplateEditor(templateId: String) {
        TemplateEditorActivity.launch(context, templateId)
    }
}