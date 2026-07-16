// File: utils/MainNavigationController.kt
package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.net.toUri
import za.co.jpsoft.winkerkreader.ui.activities.ArgiefListActivity
import za.co.jpsoft.winkerkreader.ui.activities.BedieningActivity
import za.co.jpsoft.winkerkreader.ui.activities.CallLogActivity
import za.co.jpsoft.winkerkreader.ui.activities.LaaiDatabasisActivity
import za.co.jpsoft.winkerkreader.ui.activities.LidmaatDetailActivity
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity
import za.co.jpsoft.winkerkreader.ui.activities.PermissionsActivity
import za.co.jpsoft.winkerkreader.ui.activities.RegistreerActivity
import za.co.jpsoft.winkerkreader.ui.activities.SplashActivity
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

    // ============================================================
    // Main Navigation
    // ============================================================

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

    fun navigateToSplash() {
        context.startActivity(Intent(context, SplashActivity::class.java))
    }

    // ============================================================
    // Bediening (Pastoral)
    // ============================================================

    fun navigateToBediening() {
        BedieningActivity.launch(context)
    }

    fun navigateToBediening(reminderId: String) {
        BedieningActivity.launch(context, reminderId)
    }

    // ============================================================
    // Member Management
    // ============================================================

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

    fun navigateToVerjaarSms() {
        val settings = SettingsManager.getInstance(context)
        settings.fromMenu = true
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

    /**
     * Navigate to LaaiDatabasisActivity with optional extras
     */
    fun navigateToLaaiDatabasis(extras: Bundle? = null) {
        val intent = Intent(context, LaaiDatabasisActivity::class.java)
        extras?.let { intent.putExtras(it) }
        context.startActivity(intent)
    }

    /**
     * Convenience method for common LaaiDatabasisActivity use cases
     */
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
        TemplateManagerActivity.launch(context)
    }

    fun navigateToTemplateEditor(templateId: String) {
        TemplateEditorActivity.launch(context, templateId)
    }

    // ============================================================
    // System Settings Navigation
    // ============================================================

    /**
     * Opens the Notification Listener settings screen.
     * Required for VoIP call detection (WhatsApp, Skype, etc.)
     */
    fun navigateToNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            navigateToAppSettings()
        }
    }

    /**
     * Opens the Notification Policy Access settings screen.
     * Required for Do Not Disturb mode access.
     */
    fun navigateToNotificationPolicySettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            navigateToAppSettings()
        }
    }

    /**
     * Opens the app-specific settings screen.
     * Fallback when specific settings screens can't be opened.
     */
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

    /**
     * Opens the Overlay Permission settings screen.
     * Required for floating caller ID window.
     */
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

    /**
     * Opens the Battery Optimization settings screen.
     * Required to keep background services running reliably.
     */
    fun navigateToBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            navigateToAppSettings()
        }
    }

    /**
     * Opens the Exact Alarm permission settings screen.
     * Required for Android 12+ exact alarm scheduling.
     */
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

    /**
     * Opens the Manage Overlay Permission settings screen.
     * Legacy method for overlay permission.
     */
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

    /**
     * Opens a URL in the default browser.
     */
    fun navigateToUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    /**
     * Opens the phone dialer with the given number.
     */
    fun navigateToPhoneDial(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, "tel:$phoneNumber".toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    /**
     * Opens the SMS app with the given number.
     */
    fun navigateToSms(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, "sms:$phoneNumber".toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    /**
     * Opens the email app with the given address.
     */
    fun navigateToEmail(emailAddress: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, "mailto:$emailAddress".toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    /**
     * Opens the calendar app to create a new event.
     */
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

    /**
     * Opens Google Maps with the given address.
     */
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

    /**
     * Starts the CallMonitoringService if not already running.
     */
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

    /**
     * Starts the WhatsAppNotificationService if not already running.
     */
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

    /**
     * Starts the ServiceKeepAlive service.
     */
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