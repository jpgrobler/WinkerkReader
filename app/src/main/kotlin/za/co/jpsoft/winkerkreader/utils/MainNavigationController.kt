// File: utils/MainNavigationController.kt
package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import za.co.jpsoft.winkerkreader.receivers.PastoralReminderActionReceiver
import za.co.jpsoft.winkerkreader.ui.activities.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainNavigationController @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ============================================================
    // Main Navigation
    // ============================================================

    fun navigateToMain(extras: Bundle? = null) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extras?.let { putExtras(it) }
        }
        context.startActivity(intent)
    }

    fun navigateToSplash() {
        context.startActivity(Intent(context, SplashActivity::class.java))
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
        context.startActivity(Intent(context, PastoralBackupActivity::class.java))
    }

    // ============================================================
    // Member Management
    // ============================================================

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

    fun navigateToVerjaarSms() {
        // The fromMenu flag is now handled inside the ViewModel/Activity, not here.
        // We'll keep the intent creation simple.
        context.startActivity(Intent(context, VerjaarSmsActivity::class.java))
    }

    fun navigateToArgief() {
        context.startActivity(Intent(context, ArgiefListActivity::class.java))
    }

    // ============================================================
    // Settings & Configuration
    // ============================================================

    fun navigateToUitleg() {
        context.startActivity(Intent(context, UitlegActivity::class.java))
    }

    fun navigateToRegistreer() {
        context.startActivity(Intent(context, RegistreerActivity::class.java))
    }

    fun navigateToPermissions() {
        context.startActivity(Intent(context, PermissionsActivity::class.java))
    }

    // ============================================================
    // Data Management
    // ============================================================

    fun navigateToLaaiDatabasis(extras: Bundle? = null) {
        val intent = Intent(context, LaaiDatabasisActivity::class.java)
        extras?.let { intent.putExtras(it) }
        context.startActivity(intent)
    }

    fun navigateToLaaiDatabasis(promptRestore: Boolean = false) {
        val intent = Intent(context, LaaiDatabasisActivity::class.java)
        if (promptRestore) {
            intent.putExtra(LaaiDatabasisActivity.EXTRA_PROMPT_RESTORE, true)
        }
        context.startActivity(intent)
    }

    // ============================================================
    // Call Log
    // ============================================================

    fun navigateToCallLog() {
        context.startActivity(Intent(context, CallLogActivity::class.java))
    }

    // ============================================================
    // Template Management
    // ============================================================

    fun navigateToTemplateManager() {
        context.startActivity(Intent(context, TemplateManagerActivity::class.java))
    }

    fun navigateToTemplateEditor(templateId: String) {
        context.startActivity(
            Intent(context, TemplateEditorActivity::class.java)
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
                data = android.provider.CalendarContract.Events.CONTENT_URI
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
                za.co.jpsoft.winkerkreader.services.CallMonitoringService::class.java
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
                za.co.jpsoft.winkerkreader.services.WhatsAppNotificationService::class.java
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
                Intent(context, za.co.jpsoft.winkerkreader.services.ServiceKeepAlive::class.java)
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