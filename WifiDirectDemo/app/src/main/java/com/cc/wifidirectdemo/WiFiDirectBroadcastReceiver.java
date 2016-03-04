package com.cc.wifidirectdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.util.Log;

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

    private WifiP2pManager mWifiP2pManager;
    private Channel mWifiP2pChannel;
    private WifiDirectService mService;

    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel, WifiDirectService service) {
        super();
        this.mWifiP2pManager = manager;
        this.mWifiP2pChannel = channel;
        this.mService = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();

        switch (action) {

            case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:

                // UI update to indicate wifi p2p status.
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Log.d("toto", "---> received STATE_CHANGED, state = WIFI_P2P_STATE_ENABLED");
                    mService.setIsWifiP2pEnabled(true);
                } else if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                    Log.d("toto", "---> received STATE_CHANGED, state = WIFI_P2P_STATE_DISABLED, reset peers");
                    mService.setIsWifiP2pEnabled(false);
                    mService.resetPeers();
                    mService.resetDetailsData();
                }

                break;

            case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:

                Log.d("toto", "---> received PEERS_CHANGED, request peers");

                // request available peers from the wifi p2p manager
                if (mWifiP2pManager != null) {
                    mWifiP2pManager.requestPeers(mWifiP2pChannel, mService.getPeerListListener());
                }

                break;

            case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:

                if (mWifiP2pManager == null) {
                    return;
                }

                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

                if (networkInfo.isConnected()) {

                    if (mService.isConnected()) {
                        return;
                    }

                    mService.setIsConnected(true);

                    Log.d("toto", "---> received CONNECTION_CHANGED, connected, call requestConnectionInfo");

                    // connected with the other device, request
                    // connection info to find group owner IP
                    mWifiP2pManager.requestConnectionInfo(mWifiP2pChannel, mService.getConnectionInfoListener());

                } else {

                    mService.setIsConnected(false);

                    Log.d("toto", "---> received CONNECTION_CHANGED, disconnected, reset peers");

                    // disconnected, update UI
                    mService.resetPeers();
                    mService.resetDetailsData();

                    mService.setInitiatedConnectionFlag(false);

                    mService.discover();
                }

                break;

            case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:

                WifiP2pDevice device = (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);

                Log.d("toto", "---> received THIS_DEVICE_CHANGED, update UI with device name/status: " + device.deviceName + "/" + Utils.getDeviceStatus(device.status));

                mService.updateThisDevice(device);

                break;

            default:
                break;
        }
    }
}

