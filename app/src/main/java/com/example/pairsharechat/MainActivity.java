package com.example.pairsharechat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 100;

    private TextView tvStatus;
    private DevicesAdapter devicesAdapter;
    private final List<Device> deviceList = new ArrayList<>();

    // Wi-Fi Direct objects
    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private BroadcastReceiver mReceiver;
    private IntentFilter mIntentFilter;
    private boolean isSettingsOpened = false;

    // Store the discovered peers (WifiP2pDevice) to map with our Device objects
    private final List<WifiP2pDevice> peers = new ArrayList<>();

    // Instead of a static final list, we will build the required permissions dynamically
    private String[] getRequiredPermissions() {
        int deviceVersion = Build.VERSION.SDK_INT;

        // For devices running below Android 6 (API 23), permissions are granted at install time.
        if (deviceVersion < Build.VERSION_CODES.M) {
            return new String[0];
        }

        // For devices running Android 12 (API 31) and above, include NEARBY_WIFI_DEVICES.
        if (deviceVersion >= Build.VERSION_CODES.S) { // Build.VERSION_CODES.S corresponds to Android 12 (API 31)
            return new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
//                    Manifest.permission.NEARBY_WIFI_DEVICES
            };
        } else {
            // For devices below Android 12, the older permissions are sufficient.
            return new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
            };
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check Wi-Fi state and prompt to enable if off
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            new AlertDialog.Builder(this).setTitle("Wi‑Fi Disabled").setMessage("Wi‑Fi is turned off. The app requires Wi‑Fi to work. Would you like to enable it?").setPositiveButton("Turn On", (dialog, which) -> {
                // Note: On modern Android versions this might not work automatically.
                wifiManager.setWifiEnabled(true);
                Toast.makeText(MainActivity.this, "Enabling Wi‑Fi...", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Cancel", (dialog, which) -> Toast.makeText(MainActivity.this, "Wi‑Fi is required for the app to function properly.", Toast.LENGTH_LONG).show()).show();
        }

        // Request necessary permissions if not already granted
        if (!hasRequiredPermissions()) {
            Log.d(TAG, "Requesting permissions initially...");
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
        }

        // Initialize views
        MaterialButton btnScan = findViewById(R.id.btnScan);
        RecyclerView rvDevices = findViewById(R.id.rvDevices);
        tvStatus = findViewById(R.id.tvStatus);

        // Set up RecyclerView with DevicesAdapter and a click listener for pairing
        devicesAdapter = new DevicesAdapter(deviceList, device -> new AlertDialog.Builder(MainActivity.this).setTitle("Pair Request").setMessage("Do you want to connect with " + device.getName() + "?").setPositiveButton("Yes", (dialog, which) -> connectToPeer(device)).setNegativeButton("No", null).show());
        rvDevices.setLayoutManager(new LinearLayoutManager(this));
        rvDevices.setAdapter(devicesAdapter);

        // Initialize Wi-Fi Direct manager and channel
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (mManager == null) {
            Toast.makeText(this, "Wi-Fi Direct not supported on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        mChannel = mManager.initialize(this, getMainLooper(), null);

        // Initialize broadcast receiver and intent filter
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // Scan button action: start peer discovery
        btnScan.setOnClickListener(view -> scanPeers());
    }

    @SuppressLint("MissingPermission")
    private void scanPeers() {
        updateStatus("Scanning for Wi-Fi Direct peers...");
        if (!hasRequiredPermissions()) {
            Toast.makeText(MainActivity.this, "Missing required permissions", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(MainActivity.this, getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
            return;
        }
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, "Discovery Initiated", Toast.LENGTH_SHORT).show();
                updateStatus("Discovery initiated...");
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(MainActivity.this, "Discovery Failed: " + reasonCode, Toast.LENGTH_SHORT).show();
                updateStatus("Discovery failed: " + reasonCode);
            }
        });
    }

    // Check if all required permissions are granted
    private boolean hasRequiredPermissions() {
        int deviceVersion = Build.VERSION.SDK_INT;

        if (deviceVersion >= Build.VERSION_CODES.S) { // Android 12+
            return ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    // Called by the broadcast receiver when peers are available
    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
        peers.clear();
        peers.addAll(peerList.getDeviceList());
        int previousSize = deviceList.size();
        deviceList.clear();
        for (WifiP2pDevice device : peers) {
            deviceList.add(new Device(device.deviceName, "N/A"));
        }
        if (previousSize == 0) {
            devicesAdapter.notifyItemRangeInserted(0, deviceList.size());
        } else {
            devicesAdapter.notifyDataSetChanged();
        }
        updateStatus("Found " + deviceList.size() + " device(s).");
    }

    // Initiate connection to a selected peer
    @SuppressLint("MissingPermission")
    private void connectToPeer(Device device) {
        for (WifiP2pDevice p : peers) {
            if (p.deviceName.equals(device.getName())) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = p.deviceAddress;
                updateStatus("Connecting to " + p.deviceName);
                if (!hasRequiredPermissions()) {
                    Toast.makeText(MainActivity.this, "Missing required permissions", Toast.LENGTH_SHORT).show();
                    ActivityCompat.requestPermissions(MainActivity.this, getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
                    return;
                }
                try {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // Permissions are missing, so exit
                        return;
                    }
                    mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            updateStatus("Connection initiated, waiting for connection info...");
                            Toast.makeText(MainActivity.this, "Connection initiated", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(int reason) {
                            updateStatus("Connection failed: " + reason);
                            Toast.makeText(MainActivity.this, "Connection failed: " + reason, Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error connecting to peer", e);
                    Toast.makeText(MainActivity.this, "Error connecting to peer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    // Called when connection info is available (i.e. after a successful connection)
    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (info.groupOwnerAddress != null) {
            String groupOwnerAddress = info.groupOwnerAddress.getHostAddress();
            updateStatus("Connected. Group Owner: " + groupOwnerAddress);
            Toast.makeText(MainActivity.this, "Connected. Group Owner: " + groupOwnerAddress, Toast.LENGTH_SHORT).show();

            for (Device device : deviceList) {
                if (device.getIp().equals("N/A")) {
                    device.setIp(groupOwnerAddress);
                    break;
                }
            }
            devicesAdapter.notifyDataSetChanged();

            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            intent.putExtra("groupOwnerAddress", groupOwnerAddress);
            intent.putExtra("isGroupOwner", info.isGroupOwner);
            startActivity(intent);
        } else {
            updateStatus("Connection info available but Group Owner Address is null");
            Toast.makeText(MainActivity.this, "Group Owner Address is null. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    // Update the UI status text and log the message
    @SuppressLint("SetTextI18n")
    private void updateStatus(String message) {
        runOnUiThread(() -> tvStatus.setText("Status: " + message));
        Log.d(TAG, message);
    }

    // Handle runtime permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                Log.d(TAG, "Permission " + permissions[i] + " : " + (grantResults[i] == PackageManager.PERMISSION_GRANTED));
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                }
            }
            Log.d(TAG, "hasRequiredPermissions after request: " + hasRequiredPermissions());

//            if (!allGranted) {
//                checkPermissionManually();
//            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

//    private void checkPermissionManually() {
//        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.NEARBY_WIFI_DEVICES)) {
//            if (!isSettingsOpened) {
//                new AlertDialog.Builder(this)
//                        .setTitle("Nearby Devices Permission Required")
//                        .setMessage("Please enable 'Nearby Devices' permission in App Settings to proceed.")
//                        .setPositiveButton("Open Settings", (dialog, which) -> openAppSettings())
//                        .setNegativeButton("Cancel", null)
//                        .show();
//            }
//        }
//    }

//    private void openAppSettings() {
//        isSettingsOpened = true;
//        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//        intent.setData(android.net.Uri.parse("package:" + getPackageName()));
//        startActivity(intent);
//    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            registerReceiver(mReceiver, mIntentFilter);
        } catch (Exception e) {
            Log.e(TAG, "Error registering receiver", e);
            Toast.makeText(this, "Error registering receiver: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        if (hasRequiredPermissions()) {
            isSettingsOpened = false; // Reset flag when permissions granted
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(mReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
            Toast.makeText(this, "Error unregistering receiver: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}