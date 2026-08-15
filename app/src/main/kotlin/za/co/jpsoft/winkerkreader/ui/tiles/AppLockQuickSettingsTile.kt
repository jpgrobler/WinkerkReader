package za.co.jpsoft.winkerkreader.ui.tiles

import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import za.co.jpsoft.winkerkreader.utils.security.AppAuthState
import za.co.jpsoft.winkerkreader.utils.prefs.SecurityPrefs
import za.co.jpsoft.winkerkreader.ui.activities.MainActivity

/**
 * **Quick Settings Tile for App Lock (Android 7.0+)**
 *
 * Allows users to lock the WinkerkReader app directly from the system quick settings panel.
 *
 * **Benefits:**
 *   - One-swipe access to lock (no need to open app menu)
 *   - Visible, persistent action (pastors see it when opening quick settings)
 *   - Works even if app is backgrounded
 *
 * **Setup:**
 *
 *   1. Add to AndroidManifest.xml:
 *
 *       <service
 *           android:name="za.co.jpsoft.winkerkreader.ui.tiles.AppLockQuickSettingsTile"
 *           android:label="🔐 Lock WinkerkReader"
 *           android:icon="@drawable/ic_lock_24dp"
 *           android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
 *           android:exported="true">
 *           <intent-filter>
 *               <action android:name="android.service.quicksettings.action.QS_TILE" />
 *           </intent-filter>
 *       </service>
 *
 *   2. Ensure minSdkVersion >= 24 in build.gradle (this tile requires API 24+)
 *
 *   3. Users can add the tile to their quick settings:
 *      - Swipe down twice to open full quick settings
 *      - Tap "Edit" or long-hold to customize
 *      - Look for "🔐 Lock WinkerkReader" and drag it to quick settings
 *
 * **Usage:**
 *   - User swipes down to quick settings, taps the lock tile
 *   - App is immediately locked (AppAuthState.lockAppNow())
 *   - Next time they open the app, biometric/PIN is required
 *
 * **Note:**
 *   This is OPTIONAL. Works independently from the menu-based quick lock.
 *   Can be deployed as part of v4.3+ if desired.
 */
@RequiresApi(Build.VERSION_CODES.N)
class AppLockQuickSettingsTile : TileService() {

    private companion object {
        private const val TAG = "AppLockQST"
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "Tile added to quick settings")
        updateTileState()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.d(TAG, "Tile removed from quick settings")
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile clicked - locking app")

        // ✅ Lock the app immediately
        AppAuthState.lockAppNow()

        // Update tile label to reflect the action
        qsTile?.label = "🔐 WinkerkReader Locked"
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()

        // Optional: Bring the app to foreground (so user sees it's locked)
        // This is useful for immediate feedback but can be skipped if you prefer silent lock
        showLockNotification()

        // Reset tile after a brief delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateTileState()
        }, 2000)
    }

    /**
     * Update the tile state based on whether biometric is enabled.
     */
    private fun updateTileState() {
        val prefs = getSharedPreferences(
            "WinkerkReader_prefs",
            Context.MODE_PRIVATE
        )
        val securePrefs = getSharedPreferences(
            "WinkerkReader_SecurePrefs",
            Context.MODE_PRIVATE
        )

        val securityPrefs = SecurityPrefs(prefs, securePrefs)
        val biometricEnabled = securityPrefs.biometricEnabled

        qsTile?.apply {
            label = "🔐 Lock WinkerkReader"
            state = if (biometricEnabled) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            contentDescription = if (biometricEnabled) {
                "Tap to lock WinkerkReader"
            } else {
                "WinkerkReader biometric lock is disabled"
            }
            updateTile()
        }
    }

    /**
     * Optional: Show a notification when app is locked via quick settings.
     * Provides feedback that the action was successful.
     */
    private fun showLockNotification() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivityAndCollapse(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start activity", e)
        }
    }
}

/**
 * **Alternative: Notification-based Quick Lock**
 *
 * If you prefer not to use the system tile, you can add a persistent notification
 * with a quick-lock action button. This is simpler to implement and works on older APIs.
 *
 * Usage: Show this notification in a service or when certain conditions are met.
 */
object QuickLockNotificationHelper {

    private const val CHANNEL_ID = "winkerk_quick_lock"
    private const val NOTIFICATION_ID = 7734

    /**
     * Create a persistent notification with quick-lock action.
     * Useful when the user is actively moving between apps.
     */
    fun showQuickLockNotification(context: Context) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as? android.app.NotificationManager ?: return

        // Create channel (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "WinkerkReader Security",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Quick actions for app security"
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent for quick-lock action
        val lockIntent = Intent(context, QuickLockBroadcastReceiver::class.java).apply {
            action = "za.co.jpsoft.winkerkreader.QUICK_LOCK"
        }
        val lockPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            0,
            lockIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build notification
        val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("WinkerkReader")
            .setContentText("Tap to lock app for security")
            .setOngoing(true)
            .addAction(
                0,
                "🔐 Lock Now",
                lockPendingIntent
            )
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Hide the quick-lock notification.
     */
    fun dismissQuickLockNotification(context: Context) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as? android.app.NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
    }
}

/**
 * **Broadcast Receiver for Quick-Lock Actions**
 *
 * Handles the click event from quick-lock notification.
 * Register in AndroidManifest.xml:
 *
 *   <receiver
 *       android:name="za.co.jpsoft.winkerkreader.ui.tiles.QuickLockBroadcastReceiver"
 *       android:exported="false">
 *       <intent-filter>
 *           <action android:name="za.co.jpsoft.winkerkreader.QUICK_LOCK" />
 *       </intent-filter>
 *   </receiver>
 */
class QuickLockBroadcastReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: android.content.Intent?) {
        if (intent?.action == "za.co.jpsoft.winkerkreader.QUICK_LOCK") {
            // ✅ Lock the app
            AppAuthState.lockAppNow()

            // Optional: Show toast feedback
            if (context != null) {
                android.widget.Toast.makeText(
                    context,
                    "🔐 App locked. PIN required on next access.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}