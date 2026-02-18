// MenuItemHandler.java
package za.co.jpsoft.winkerkreader;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import za.co.jpsoft.winkerkreader.data.WinkerkContract;

import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry;
import static za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.LIDMATE_TAG;

/**
 * Handles main menu item selections
 */
public class MenuItemHandler {
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String[] STORAGE_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private PermissionManager permissionManager;
    private final AppCompatActivity activity;
    private final SharedPreferences preferences;

    public MenuItemHandler(AppCompatActivity activity, SharedPreferences preferences) {
        this.activity = activity;
        this.preferences = preferences;
    }

    public boolean handleMenuItem(MenuItem item) {
        TextView sortOrderView = activity.findViewById(R.id.sortorder);
        permissionManager = new PermissionManager(activity);
        int id = item.getItemId();

        if (id == R.id.select_options) {
            return handleSelectOptions();
        } else if (id == R.id.tagged) {
            return handleTagged(sortOrderView);
        } else if (id == R.id.sort_van) {
            return handleSortVan(sortOrderView);
        } else if (id == R.id.sort_wyk) {
            return handleSortWyk(sortOrderView);
        } else if (id == R.id.sort_ouderdom) {
            return handleSortOuderdom(sortOrderView);
        } else if (id == R.id.verjaar) {
            return handleVerjaar(sortOrderView);
        } else if (id == R.id.sort_adres) {
            return handleSortAdres(sortOrderView);
        } else if (id == R.id.sort_gesin) {
            return handleSortGesin(sortOrderView);
        } else if (id == R.id.registreer) {
            return handleRegistreer();
        } else if (id == R.id.laai) {
            return handleLaai();
        } else if (id == R.id.sms_verjaar) {
            return handleSmsVerjaar();
        } else if (id == R.id.filter_options) {
            return handleFilterOptions();
        } else if (id == R.id.deselect) {
            return handleDeselect();
        } else if (id == R.id.fotos) {
            return handleFotos();
        } else if (id == R.id.opdateerApp) {
            return handleUpdateApp();
        } else if (id == R.id.uitleg) {
            return handleUitleg();
        } else if (id == R.id.argief) {
            return handleArgief();
        } else if (id == R.id.action_view_call_log) {
            return handleViewCallLog();
        } else  if (id == R.id.menu_permissions) {
            return handlePermissions();
        } else if (id == R.id.menu_permission_settings) {
                showPermissionSettingsDialog();
                return true;
        } else {
            return false;
        }
    }

    public boolean handlePermissions() {
            Intent intent = new Intent(activity, PermissionsActivity.class);
            activity.startActivity(intent);
            return true;
    }

    private boolean handleViewCallLog() {
        // Open call log activity
        Intent intent = new Intent(activity, CallLogActivity.class);
        activity.startActivity(intent);
        return true;
    }

    private boolean handleSelectOptions() {
        Intent intent = new Intent(activity, MyActivity_Settings.class);
        // Add search list extra if needed
        activity.startActivity(intent);
        return true;
    }

    private boolean handleTagged(TextView sortOrderView) {
        sortOrderView.setBackground(null);
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER = "VAN";
        winkerkEntry.SOEKLIST = false;
        ((MainActivity2) activity).observeDataset();
        return true;
    }

    private boolean handleSortVan(TextView sortOrderView) {
        sortOrderView.setBackground(null);
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER = "VAN";
        winkerkEntry.SOEKLIST = false;
        ((MainActivity2) activity).observeDataset();
        return true;
    }

    private boolean handleSortWyk(TextView sortOrderView) {
        sortOrderView.setBackground(null);
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER = "WYK";
        winkerkEntry.SOEKLIST = false;
        ((MainActivity2) activity).observeDataset();
        return true;
    }

    private boolean handleSortOuderdom(TextView sortOrderView) {
        sortOrderView.setBackground(null);
        winkerkEntry.DEFLAYOUT = WinkerkContract.winkerkEntry.SORTORDER = "OUDERDOM";
        WinkerkContract.winkerkEntry.SOEKLIST = false;
        ((MainActivity2) activity).observeDataset();
        return true;
    }

    private boolean handleVerjaar(TextView sortOrderView) {
        sortOrderView.setBackground(null);
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER = "VERJAAR";
        winkerkEntry.SOEKLIST = false;
        ((MainActivity2) activity).observeDataset();
        return true;
    }

    private boolean handleSortAdres(TextView sortOrderView) {
        sortOrderView.setBackground(null);
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER = "ADRES";
        winkerkEntry.SOEKLIST = false;
        ((MainActivity2) activity).observeDataset();
        return true;
    }

    private boolean handleSortGesin(TextView sortOrderView) {
        sortOrderView.setBackground(null);
        winkerkEntry.DEFLAYOUT = winkerkEntry.SORTORDER = "GESINNE";
        winkerkEntry.SOEKLIST = false;
        ((MainActivity2) activity).observeDataset();
        return true;
    }

    private boolean handleRegistreer() {
        Intent intent = new Intent(activity, registreer.class);
        activity.startActivity(intent);
        return true;
    }
    // In your Activity
    private boolean checkAndRequestStoragePermissions() {
        if (PermissionHelper.arePermissionsGranted(activity, PermissionHelper.STORAGE_PERMISSIONS)) {
            // Storage permissions are granted, proceed with your logic
            return true;
        } else {
            // Check if we should show rationale
            if (PermissionHelper.shouldShowRationaleForPermissions(activity, PermissionHelper.STORAGE_PERMISSIONS)) {
                // Show explanation to user why you need these permissions
                showStoragePermissionRationale();
            } else {
                // Request the permissions directly
                PermissionHelper.requestPermissionGroup(activity,
                        PermissionHelper.STORAGE_PERMISSIONS,
                        PermissionHelper.REQUEST_CODE_STORAGE);
            }
        }
        return PermissionHelper.arePermissionsGranted(activity, PermissionHelper.STORAGE_PERMISSIONS);
    }
    private void showStoragePermissionRationale() {
        // Show a dialog or explanation to the user
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Storage Permission Required")
                .setMessage("This app needs storage access to read and save files. Please grant the permission.")
                .setPositiveButton("Grant", (dialog, which) -> {
                    PermissionHelper.requestPermissionGroup(activity,
                            PermissionHelper.STORAGE_PERMISSIONS,
                            PermissionHelper.REQUEST_CODE_STORAGE);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private boolean handleLaai() {
//        if (!checkAndRequestStoragePermissions()) {
//            return true;
//        }

        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("FROM_MENU", true);
        editor.apply();

        Intent intent = new Intent(activity, Laaidatabasis.class);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }

    private boolean handleSmsVerjaar() {
        Intent intent = new Intent(activity, VerjaarSMS2.class);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("FROM_MENU", true);
        editor.apply();
        activity.startActivity(intent);
        return true;
    }

    private boolean handleFilterOptions() {
        try {
            MainActivity2 mainActivity = (MainActivity2) activity;
            FilterHandler filterHandler = new FilterHandler(activity);
            filterHandler.showFilterDialog();
            return true;
        } catch (Exception e) {
            Toast.makeText(activity, "Error opening filter options", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean handleDeselect() {
        ContentValues values = new ContentValues();
        values.put(LIDMATE_TAG, 0);
        activity.getContentResolver().update(WinkerkContract.winkerkEntry.CONTENT_URI, values, null, null);
        return true;
    }

    private boolean handleFotos() {
        if (checkStoragePermission()) {
            return true;
        }

        Intent intent = new Intent(activity, SyncFotos.class);
        activity.startActivity(intent);
        return true;
    }

    private boolean handleUpdateApp() {
        Intent intent = new Intent(activity, UpdateActivity.class);
        activity.startActivity(intent);
        return true;
//        try {
//            UpdateCheck updateCheck = new UpdateCheck(activity);
//            // Implement update check logic
//            Toast.makeText(activity, "Checking for updates...", Toast.LENGTH_SHORT).show();
//        } catch (Exception e) {
//            Toast.makeText(activity, "Error checking for updates", Toast.LENGTH_SHORT).show();
//        }
//        return true;
    }

    private boolean handleUitleg() {
        Intent intent = new Intent(activity, UitlegActivity.class);
        activity.startActivity(intent);
        return true;
    }

    private boolean handleArgief() {
        Intent intent = new Intent(activity, argief_List.class);
        activity.startActivity(intent);
        return true;
    }

    private boolean checkStoragePermission() {
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, "Storage permission required", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(activity, STORAGE_PERMISSIONS, REQUEST_EXTERNAL_STORAGE);
            return false;
        }
        return true;
    }
    private void showPermissionSettingsDialog() {
        boolean currentSetting = permissionManager.isCheckOnStartEnabled();

        new AlertDialog.Builder(activity)
                .setTitle("Permission Check Settings")
                .setMessage("Check permissions on app start: " + (currentSetting ? "Enabled" : "Disabled"))
                .setPositiveButton(currentSetting ? "Disable" : "Enable", (dialog, which) -> {
                    permissionManager.setCheckOnStart(!currentSetting);
                    Toast.makeText(activity,
                            "Permission check " + (!currentSetting ? "enabled" : "disabled"),
                            Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Manage Permissions", (dialog, which) -> {
                    Intent intent = new Intent(activity, PermissionsActivity.class);
                    activity.startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}