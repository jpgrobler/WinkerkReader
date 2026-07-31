package za.co.jpsoft.winkerkreader.utils

/**
 * The category of a permission, which determines how it is requested.
 * Extracted from the inner enum inside PermissionsActivity.
 */
enum class PermissionType {
    /** Standard Android runtime permission — requested via ActivityResultLauncher. */
    RUNTIME,

    /** System overlay (draw over other apps) — requires Settings intent. */
    OVERLAY,

    /** Schedule exact alarms (Android 12+) — requires Settings intent. */
    EXACT_ALARM,

    /** Do Not Disturb / notification policy access — requires Settings intent. */
    NOTIFICATION_POLICY,

    /** Notification listener service — requires Settings intent. */
    NOTIFICATION_LISTENER
}