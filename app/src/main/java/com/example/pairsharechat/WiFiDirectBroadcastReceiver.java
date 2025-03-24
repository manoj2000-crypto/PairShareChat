package com.example.pairsharechat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

    private final WifiP2pManager mManager;
    private final Channel mChannel;
    private final MainActivity mActivity;
    private static final String TAG = "WFD_BroadcastReceiver";

    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel, MainActivity activity) {
        super();
        this.mManager = manager;
        this.mChannel = channel;
        this.mActivity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Check to see if Wi-Fi Direct is enabled or not.
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Log.d(TAG, "Wi-Fi Direct is enabled");
                Toast.makeText(context, "Wi-Fi Direct is enabled", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "Wi-Fi Direct is not enabled");
                Toast.makeText(context, "Wi-Fi Direct is not enabled", Toast.LENGTH_SHORT).show();
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            // The peer list has changed! Request the updated list.
            if (mManager != null) {
                // Use context instead of "this" for permission check
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                    // Missing required permissions; log, toast, and return.
                    Log.d(TAG, "Missing required Wi-Fi Direct permissions. Please request them in the Activity.");
                    Toast.makeText(context, "Missing required Wi-Fi Direct permissions.", Toast.LENGTH_SHORT).show();
                    return;
                }
                mManager.requestPeers(mChannel, mActivity);
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            if (mManager == null) {
                Log.d(TAG, "WifiP2pManager is null");
                Toast.makeText(context, "WifiP2pManager is null", Toast.LENGTH_SHORT).show();
                return;
            }
            // Instead of using the deprecated isConnected(), we now use ConnectivityManager and NetworkCapabilities.
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                Log.d(TAG, "ConnectivityManager is null");
                Toast.makeText(context, "ConnectivityManager is null", Toast.LENGTH_SHORT).show();
                return;
            }
            Network currentNetwork = connectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(currentNetwork);
            if (capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                // We consider the device connected if it has internet capability.
                Log.d(TAG, "Connected to Wi-Fi Direct peer");
                Toast.makeText(context, "Connected to Wi-Fi Direct peer", Toast.LENGTH_SHORT).show();
                mManager.requestConnectionInfo(mChannel, mActivity);
            } else {
                Log.d(TAG, "Disconnected from Wi-Fi Direct peer");
                Toast.makeText(context, "Disconnected from Wi-Fi Direct peer", Toast.LENGTH_SHORT).show();
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            // This device's details have changed.
            Log.d(TAG, "This device's Wi-Fi Direct details have changed.");
            Toast.makeText(context, "This device's Wi-Fi Direct details have changed.", Toast.LENGTH_SHORT).show();
        }
    }
}