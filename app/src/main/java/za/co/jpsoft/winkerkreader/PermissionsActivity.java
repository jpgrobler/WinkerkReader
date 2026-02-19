package za.co.jpsoft.winkerkreader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PermissionsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PermissionsAdapter adapter;
    private List<PermissionItem> permissionsList;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1002;
    private static final int NOTIFICATION_POLICY_REQUEST_CODE = 1003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("App Permissions");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.recyclerViewPermissions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        initializePermissionsList();
        adapter = new PermissionsAdapter(permissionsList);
        recyclerView.setAdapter(adapter);

        Button btnRequestAll = findViewById(R.id.btnRequestAllPermissions);
        btnRequestAll.setOnClickListener(v -> requestAllPermissions());
    }

    private void initializePermissionsList() {
        permissionsList = new ArrayList<>();

        // Exact Alarm permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsList.add(new PermissionItem(
                    "Alarms",
                    "Maak dit mooontlik dat app jou kan herinner op sekere tye",
                    PermissionType.EXACT_ALARM
            ));
        }

        // Notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(new PermissionItem(
                    "Notifications",
                    "Wys Kennisgewings",
                    Manifest.permission.POST_NOTIFICATIONS,
                    PermissionType.RUNTIME
            ));
        }

        // Notification Policy Access
        permissionsList.add(new PermissionItem(
                "Do Not Disturb Access",
                "Laat app toe om beleid te lees",
                PermissionType.NOTIFICATION_POLICY
        ));

        // Phone permissions
        permissionsList.add(new PermissionItem(
                "Phone State",
                "Laat app toe om inkomende nommer op te soek teen gemeente data",
                Manifest.permission.READ_PHONE_STATE,
                PermissionType.RUNTIME
        ));

        permissionsList.add(new PermissionItem(
                "Call Log",
                "Laat app toe om nommer op te soek teen gemeente data",
                Manifest.permission.READ_CALL_LOG,
                PermissionType.RUNTIME
        ));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(new PermissionItem(
                    "Phone Numbers",
                    "Laat app toe om inkomende nommer op te soek teen gemeente data",
                    Manifest.permission.READ_PHONE_NUMBERS,
                    PermissionType.RUNTIME
            ));
        }

        // SMS permissions
        permissionsList.add(new PermissionItem(
                "Send SMS",
                "Laat app toe om SMS te stuur",
                Manifest.permission.SEND_SMS,
                PermissionType.RUNTIME
        ));

        permissionsList.add(new PermissionItem(
                "Read SMS",
                "Laat app toe om SMS'e te lees",
                Manifest.permission.READ_SMS,
                PermissionType.RUNTIME
        ));

        // Contacts permissions
        permissionsList.add(new PermissionItem(
                "Read Contacts",
                "Laat app toe om jou foon se kontakte te lees",
                Manifest.permission.READ_CONTACTS,
                PermissionType.RUNTIME
        ));

        permissionsList.add(new PermissionItem(
                "Write Contacts",
                "Laat app toe om kontak by te voeg op jou foon",
                Manifest.permission.WRITE_CONTACTS,
                PermissionType.RUNTIME
        ));

        // Calendar permissions
        permissionsList.add(new PermissionItem(
                "Read Calendar",
                "Laat app toe om kalender te lees",
                Manifest.permission.READ_CALENDAR,
                PermissionType.RUNTIME
        ));

        permissionsList.add(new PermissionItem(
                "Write Calendar",
                "Laat app toe om veranderinge aan jou kalender te maak",
                Manifest.permission.WRITE_CALENDAR,
                PermissionType.RUNTIME
        ));

        // Media permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(new PermissionItem(
                    "Read Images",
                    "Toegang tot prentjies van foon",
                    Manifest.permission.READ_MEDIA_IMAGES,
                    PermissionType.RUNTIME
            ));

            permissionsList.add(new PermissionItem(
                    "Read Audio",
                    "Toegang tot klank files op foon",
                    Manifest.permission.READ_MEDIA_AUDIO,
                    PermissionType.RUNTIME
            ));

            permissionsList.add(new PermissionItem(
                    "Read Video",
                    "Toegang tot videos op foon",
                    Manifest.permission.READ_MEDIA_VIDEO,
                    PermissionType.RUNTIME
            ));
        } else {
            // Storage permissions for older Android versions
            permissionsList.add(new PermissionItem(
                    "Read Storage",
                    "Toegang om files te lees",
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    PermissionType.RUNTIME
            ));

            permissionsList.add(new PermissionItem(
                    "Write Storage",
                    "Toegang om files te stooor",
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    PermissionType.RUNTIME
            ));
        }

        // System overlay permission
        permissionsList.add(new PermissionItem(
                "Display over other apps",
                "Toestemming om bo oor ander apps te wys",
                PermissionType.OVERLAY
        ));

        // Notification Listener
        permissionsList.add(new PermissionItem(
                "Notification Access",
                "Luister na kennisgewings (vir VOIP oproepe bv. Whatsapp)",
                PermissionType.NOTIFICATION_LISTENER
        ));
    }

    private void requestAllPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        for (PermissionItem item : permissionsList) {
            if (item.getType() == PermissionType.RUNTIME && !item.isGranted()) {
                permissionsToRequest.add(item.getPermission());
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        } else {
            Toast.makeText(this, "Alle toestemmings is reeds gegee", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestSpecialPermission(PermissionItem item) {
        switch (item.getType()) {
            case OVERLAY:
                requestOverlayPermission();
                break;
            case EXACT_ALARM:
                requestExactAlarmPermission();
                break;
            case NOTIFICATION_POLICY:
                requestNotificationPolicyAccess();
                break;
            case NOTIFICATION_LISTENER:
                requestNotificationListenerAccess();
                break;
            case RUNTIME:
                if (item.getPermission() != null) {
                    ActivityCompat.requestPermissions(
                            this,
                            new String[]{item.getPermission()},
                            PERMISSION_REQUEST_CODE
                    );
                }
                break;
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
            } else {
                Toast.makeText(this, "Toestemming reeds gegee", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void requestNotificationPolicyAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.NotificationManager notificationManager =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted()) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                startActivityForResult(intent, NOTIFICATION_POLICY_REQUEST_CODE);
            } else {
                Toast.makeText(this, "Toestemming reeds gegee", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestNotificationListenerAccess() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startActivity(intent);
        new AlertDialog.Builder(this)
                .setTitle("Enable Notification Access")
                .setMessage("Gee asb vir WinkerkReader Notification Access om VOIP oproepe te monitor.")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            refreshPermissions();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE ||
                requestCode == NOTIFICATION_POLICY_REQUEST_CODE) {
            refreshPermissions();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissions();
    }

    private void refreshPermissions() {
        for (PermissionItem item : permissionsList) {
            item.updateStatus(this);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onSupportNavigateUp() {
        //onBackPressed();
        return true;
    }

    // Inner classes
    enum PermissionType {
        RUNTIME,
        OVERLAY,
        EXACT_ALARM,
        NOTIFICATION_POLICY,
        NOTIFICATION_LISTENER
    }

    class PermissionItem {
        private String name;
        private String description;
        private String permission;
        private PermissionType type;
        private boolean granted;

        public PermissionItem(String name, String description, String permission, PermissionType type) {
            this.name = name;
            this.description = description;
            this.permission = permission;
            this.type = type;
            updateStatus(PermissionsActivity.this);
        }

        public PermissionItem(String name, String description, PermissionType type) {
            this(name, description, null, type);
        }

        public void updateStatus(PermissionsActivity activity) {
            switch (type) {
                case RUNTIME:
                    granted = permission != null &&
                            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
                    break;
                case OVERLAY:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        granted = Settings.canDrawOverlays(activity);
                    } else {
                        granted = true;
                    }
                    break;
                case EXACT_ALARM:
                    granted = true; // Assume granted for simplicity
                    break;
                case NOTIFICATION_POLICY:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        android.app.NotificationManager manager =
                                (android.app.NotificationManager) activity.getSystemService(NOTIFICATION_SERVICE);
                        granted = manager != null && manager.isNotificationPolicyAccessGranted();
                    } else {
                        granted = true;
                    }
                    break;
                case NOTIFICATION_LISTENER:
                    granted = NotificationManagerCompat.getEnabledListenerPackages(activity)
                            .contains(activity.getPackageName());
                    break;
            }
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getPermission() { return permission; }
        public PermissionType getType() { return type; }
        public boolean isGranted() { return granted; }
    }

    class PermissionsAdapter extends RecyclerView.Adapter<PermissionsAdapter.ViewHolder> {
        private List<PermissionItem> items;

        public PermissionsAdapter(List<PermissionItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_permission, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PermissionItem item = items.get(position);
            holder.bind(item);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDescription;
            ImageView ivStatus;
            Button btnRequest;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvPermissionName);
                tvDescription = itemView.findViewById(R.id.tvPermissionDescription);
                ivStatus = itemView.findViewById(R.id.ivPermissionStatus);
                btnRequest = itemView.findViewById(R.id.btnRequestPermission);
            }

            public void bind(PermissionItem item) {
                tvName.setText(item.getName());
                tvDescription.setText(item.getDescription());

                if (item.isGranted()) {
                    ivStatus.setImageResource(android.R.drawable.checkbox_on_background);
                    ivStatus.setColorFilter(getResources().getColor(android.R.color.holo_green_dark));
                    btnRequest.setEnabled(false);
                    btnRequest.setText("Granted");
                } else {
                    ivStatus.setImageResource(android.R.drawable.ic_delete);
                    ivStatus.setColorFilter(getResources().getColor(android.R.color.holo_red_dark));
                    btnRequest.setEnabled(true);
                    btnRequest.setText("Request");
                }

                // Click listener for the button
                btnRequest.setOnClickListener(v -> {
                    if (!item.isGranted()) {
                        requestSpecialPermission(item);
                    }
                });

                // Click listener for the entire item
                itemView.setOnClickListener(v -> {
                    if (!item.isGranted()) {
                        requestSpecialPermission(item);
                    } else {
                        Toast.makeText(PermissionsActivity.this,
                                item.getName() + " reeds toestemming ontvang.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
}