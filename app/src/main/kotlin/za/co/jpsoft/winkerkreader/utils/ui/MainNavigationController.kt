// File: utils/MainNavigationController.kt
package za.co.jpsoft.winkerkreader.utils.ui

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract
import za.co.jpsoft.winkerkreader.services.CallMonitoringService
import za.co.jpsoft.winkerkreader.services.ServiceKeepAlive
import za.co.jpsoft.winkerkreader.services.WhatsAppNotificationService
import za.co.jpsoft.winkerkreader.ui.activities.ArgiefListActivity
import za.co.jpsoft.winkerkreader.ui.activities.BedieningActivity
import za.co.jpsoft.winkerkreader.ui.activities.CallLogActivity
import za.co.jpsoft.winkerkreader.ui.activities.LaaiDatabasisActivity
import za.co.jpsoft.winkerkreader.ui.activities.LidmaatDetailActivity
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.activities.PastoralBackupActivity
import za.co.jpsoft.winkerkreader.ui.activities.PermissionsActivity
import za.co.jpsoft.winkerkreader.ui.activities.RegistreerActivity
import za.co.jpsoft.winkerkreader.ui.activities.SplashActivity
import za.co.jpsoft.winkerkreader.ui.activities.TemplateEditorActivity
import za.co.jpsoft.winkerkreader.ui.activities.TemplateManagerActivity
import za.co.jpsoft.winkerkreader.ui.activities.UitlegActivity
import za.co.jpsoft.winkerkreader.ui.activities.VerjaarSmsActivity

@Singleton
class MainNavigationController @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Required when [context] is not an [Activity] (e.g. Hilt application context). */
    private fun launchActivity(intent: Intent) {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun activityIntent(target: Class<*>): Intent = Intent(context, target)

    // ============================================================
    // Main Navigation
    // ============================================================

    fun navigateToMain(extras: Bundle? = null) {
        val intent = activityIntent(MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            extras?.let { putExtras(it) }
        }
        launchActivity(intent)
    }

    fun navigateToSplash() {
        launchActivity(activityIntent(SplashActivity::class.java))
    }

    // ============================================================
    // Bediening (Pastoral)
    // ============================================================

    fun navigateToBediening(reminderId: String? = null) {
        if (reminderId == null) {
            BedieningActivity.launch(context)
        } else {
            BedieningActivity.launch(context, reminderId)
        }
    }

    // ============================================================
    // Pastoral Backup
    // ============================================================

    fun navigateToPastoralBackup() {
        launchActivity(activityIntent(PastoralBackupActivity::class.java))
    }

    // ============================================================
    // Member Management
    // ============================================================

    fun navigateToLidmaatDetail(
        memberGuid: String,
        recordStatus: String = "0",
        memberId: Long? = null
    ) {
        val intent = activityIntent(LidmaatDetailActivity::class.java).apply {
            putExtra(LidmaatDetailActivity.EXTRA_MEMBER_GUID, memberGuid)
            putExtra("RECORD_STATUS", recordStatus)
            memberId?.let {
                data = ContentUris.withAppendedId(
                    WinkerkContract.winkerkEntry.CONTENT_URI,
                    it
                )
            }
        }
        launchActivity(intent)
    }

    fun navigateToVerjaarSms() {
        // The fromMenu flag is now handled inside the ViewModel/Activity, not here.
        // We'll keep the intent creation simple.
        launchActivity(activityIntent(VerjaarSmsActivity::class.java))
    }

    fun navigateToArgief() {
        launchActivity(activityIntent(ArgiefListActivity::class.java))
    }

    // ============================================================
    // Settings & Configuration
    // ============================================================

    fun navigateToUitleg() {
        launchActivity(activityIntent(UitlegActivity::class.java))
    }

    fun navigateToRegistreer() {
        launchActivity(activityIntent(RegistreerActivity::class.java))
    }

    fun navigateToPermissions() {
        launchActivity(activityIntent(PermissionsActivity::class.java))
    }

    // ============================================================
    // Data Management
    // ============================================================

    fun navigateToLaaiDatabasis(extras: Bundle? = null) {
        val intent = activityIntent(LaaiDatabasisActivity::class.java)
        extras?.let { intent.putExtras(it) }
        launchActivity(intent)
    }

    fun navigateToLaaiDatabasis(promptRestore: Boolean = false) {
        val intent = activityIntent(LaaiDatabasisActivity::class.java)
        if (promptRestore) {
            intent.putExtra(LaaiDatabasisActivity.EXTRA_PROMPT_RESTORE, true)
        }
        launchActivity(intent)
    }

    // ============================================================
    // Call Log
    // ============================================================

    fun navigateToCallLog() {
        launchActivity(activityIntent(CallLogActivity::class.java))
    }

    // ============================================================
    // Template Management
    // ============================================================

    fun navigateToTemplateManager() {
        launchActivity(activityIntent(TemplateManagerActivity::class.java))
    }

    fun navigateToTemplateEditor(templateId: String) {
        launchActivity(
            activityIntent(TemplateEditorActivity::class.java)
                .putExtra("extra_template_id", templateId)
        )
    }

    // ============================================================
    // System Settings Navigation
    // ============================================================

    fun navigateToNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            navigateToAppSettings()
        }
    }

    fun navigateToNotificationPolicySettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            navigateToAppSettings()
        }
    }

    fun navigateToAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log error if needed
        }
    }

    fun navigateToOverlaySettings() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            navigateToAppSettings()
        }
    }

    fun navigateToBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            navigateToAppSettings()
        }
    }

    fun navigateToExactAlarmSettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            navigateToAppSettings()
        }
    }

    fun navigateToManageOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                navigateToAppSettings()
            }
        }
    }

    // ============================================================
    // External Navigation
    // ============================================================

    fun navigateToUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    fun navigateToPhoneDial(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, "tel:$phoneNumber".toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    fun navigateToSms(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, "sms:$phoneNumber".toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    fun navigateToEmail(emailAddress: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, "mailto:$emailAddress".toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    fun navigateToCalendar() {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    fun navigateToMaps(address: String) {
        try {
            val encodedAddress = Uri.encode(address)
            val uri = Uri.parse("geo:0,0?q=$encodedAddress")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to browser maps
            try {
                val encodedAddress = Uri.encode(address)
                val url = "https://maps.google.com/maps?q=$encodedAddress"
                navigateToUrl(url)
            } catch (e2: Exception) {
                // Log or handle error
            }
        }
    }

    // ============================================================
    // Service Management
    // ============================================================

    fun startCallMonitoringService() {
        try {
            val intent = Intent(
                context,
                CallMonitoringService::class.java
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    fun startWhatsAppNotificationService() {
        try {
            val intent = Intent(
                context,
                WhatsAppNotificationService::class.java
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    fun startKeepAliveService() {
        try {
            val intent =
                Intent(context, ServiceKeepAlive::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Log or handle error
        }
    }
}