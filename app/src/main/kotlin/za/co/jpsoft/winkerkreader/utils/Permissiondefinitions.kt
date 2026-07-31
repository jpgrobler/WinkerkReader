package za.co.jpsoft.winkerkreader.utils

import android.Manifest
import android.content.Context
import android.os.Build

/**
 * Builds the canonical list of [PermissionItem]s for the app.
 *
 * Extracted from PermissionsActivity.initializePermissionsList() (~140 lines).
 * Pure data — no Activity reference needed. Items are filtered per SDK version
 * at build time.
 *
 * Usage in PermissionsActivity:
 *
 *   permissionsList = PermissionDefinitions.build(this)
 */
object PermissionDefinitions {

    /**
     * Returns all permissions the app requires, filtered for the running SDK version.
     * Each item's [PermissionItem.isGranted] is evaluated immediately during construction.
     */
    fun build(context: Context): List<PermissionItem> = buildList {

        // ── Alarms (Android 12+) ──────────────────────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                PermissionItem(
                    name = "Alarms",
                    description = "Maak dit moontlik dat app jou kan herinner op sekere tye",
                    permission = null,
                    type = PermissionType.EXACT_ALARM,
                    context = context
                )
            )
        }

        // ── Notifications (Android 13+) ───────────────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                PermissionItem(
                    name = "Notifications",
                    description = "Wys Kennisgewings",
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    type = PermissionType.RUNTIME,
                    context = context
                )
            )
        }

        // ── Do Not Disturb ────────────────────────────────────────────────────
        add(
            PermissionItem(
                name = "Do Not Disturb Access",
                description = "Laat app toe om beleid te lees",
                permission = null,
                type = PermissionType.NOTIFICATION_POLICY,
                context = context
            )
        )

        // ── Phone ─────────────────────────────────────────────────────────────
        add(
            PermissionItem(
                name = "Phone State",
                description = "Laat app toe om inkomende nommer op te soek teen gemeente data",
                permission = Manifest.permission.READ_PHONE_STATE,
                type = PermissionType.RUNTIME,
                context = context
            )
        )
        add(
            PermissionItem(
                name = "Call Log",
                description = "Laat app toe om nommer op te soek teen gemeente data",
                permission = Manifest.permission.READ_CALL_LOG,
                type = PermissionType.RUNTIME,
                context = context
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                PermissionItem(
                    name = "Phone Numbers",
                    description = "Laat app toe om inkomende nommer op te soek teen gemeente data",
                    permission = Manifest.permission.READ_PHONE_NUMBERS,
                    type = PermissionType.RUNTIME,
                    context = context
                )
            )
        }

        // ── SMS ───────────────────────────────────────────────────────────────
        add(
            PermissionItem(
                name = "Send SMS",
                description = "Laat app toe om SMS te stuur",
                permission = Manifest.permission.SEND_SMS,
                type = PermissionType.RUNTIME,
                context = context
            )
        )
        add(
            PermissionItem(
                name = "Read SMS",
                description = "Laat app toe om SMS'e te lees",
                permission = Manifest.permission.READ_SMS,
                type = PermissionType.RUNTIME,
                context = context
            )
        )

        // ── Contacts ──────────────────────────────────────────────────────────
        add(
            PermissionItem(
                name = "Read Contacts",
                description = "Laat app toe om jou foon se kontakte te lees",
                permission = Manifest.permission.READ_CONTACTS,
                type = PermissionType.RUNTIME,
                context = context
            )
        )
        add(
            PermissionItem(
                name = "Write Contacts",
                description = "Laat app toe om kontak by te voeg op jou foon",
                permission = Manifest.permission.WRITE_CONTACTS,
                type = PermissionType.RUNTIME,
                context = context
            )
        )

        // ── Calendar ──────────────────────────────────────────────────────────
        add(
            PermissionItem(
                name = "Read Calendar",
                description = "Laat app toe om kalender te lees",
                permission = Manifest.permission.READ_CALENDAR,
                type = PermissionType.RUNTIME,
                context = context
            )
        )
        add(
            PermissionItem(
                name = "Write Calendar",
                description = "Laat app toe om veranderinge aan jou kalender te maak",
                permission = Manifest.permission.WRITE_CALENDAR,
                type = PermissionType.RUNTIME,
                context = context
            )
        )

        // ── System overlay ────────────────────────────────────────────────────
        add(
            PermissionItem(
                name = "Display over other apps",
                description = "Toestemming om bo oor ander apps te wys",
                permission = null,
                type = PermissionType.OVERLAY,
                context = context
            )
        )

        // ── Notification Listener ─────────────────────────────────────────────
        add(
            PermissionItem(
                name = "Notification Access",
                description = "Luister na kennisgewings (vir VOIP oproepe bv. Whatsapp)",
                permission = null,
                type = PermissionType.NOTIFICATION_LISTENER,
                context = context
            )
        )
    }
}