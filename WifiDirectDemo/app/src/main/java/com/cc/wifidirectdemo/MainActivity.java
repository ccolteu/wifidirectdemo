package com.cc.wifidirectdemo;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final int SOCKET_PORT = 8988;

    private MainActivity mActivity;

    private WifiP2pManager mWifiP2pManager;
    private WifiP2pManager.ChannelListener mChannelListener;
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = true;

    //A channel that connects the application to the Wifi p2p framework
    private WifiP2pManager.Channel mWifiP2pChannel;
    private BroadcastReceiver mWifiP2pReceiver = null;


    private List<WifiP2pDevice> peers = new ArrayList<>();
    private ProgressDialog progressDialog = null;
    private WifiP2pDevice thisDevice, remoteConnectedDevice;

    private WifiP2pManager.PeerListListener mPeerListListener;
    private ListView mPeersListView;
    private WiFiPeerListAdapter mWiFiPeerListAdapter;

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private WifiP2pInfo info;

    private WifiP2pManager.ConnectionInfoListener mConnectionInfoListener;

    private boolean initiatedConnection = false;

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    public void resetPeers() {
        peers.clear();
        mWiFiPeerListAdapter.notifyDataSetChanged();
    }

    public void resetDetailsData() {
        ((TextView) findViewById(R.id.status_text)).setText("");
        findViewById(R.id.open_received_photo).setVisibility(View.GONE);
        findViewById(R.id.btn_send_photo).setVisibility(View.GONE);
    }

    public WifiP2pManager.PeerListListener getPeerListListener() {
        return mPeerListListener;
    }

    public WifiP2pManager.ConnectionInfoListener getConnectionInfoListener() {
        return mConnectionInfoListener;
    }

    public void setInitiatedConnectionFlag(boolean value) {
        initiatedConnection = value;
    }

    public void updateThisDevice(WifiP2pDevice device) {
        this.thisDevice = device;
        TextView view = (TextView) findViewById(R.id.my_name);
        view.setText(device.deviceName);
        view = (TextView) findViewById(R.id.my_status);
        view.setText(getDeviceStatus(device.status));
        if (device.status == WifiP2pDevice.CONNECTED) {
            view.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            view.setTextColor(getResources().getColor(android.R.color.black));
        }
    }

    private void updateRemoteDevice(WifiP2pDevice device) {
        this.remoteConnectedDevice = device;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = this;

        setContentView(R.layout.activity_main);

        mWifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);

        mChannelListener = new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {

                if (mWifiP2pManager != null && retryChannel) {
                    Toast.makeText(mActivity, "Channel lost. Trying again", Toast.LENGTH_LONG).show();

                    // we will try once more
                    resetPeers();
                    resetDetailsData();
                    retryChannel = false;
                    mWifiP2pManager.initialize(mActivity, getMainLooper(), mChannelListener);

                } else {
                    Toast.makeText(mActivity, "Severe! Channel is probably permanently lost. Try Disable/Re-Enable P2P.", Toast.LENGTH_LONG).show();
                }
            }
        };
        // Registers the application with the Wi-Fi framework
        mWifiP2pChannel = mWifiP2pManager.initialize(this, getMainLooper(), mChannelListener);

        findViewById(R.id.btn_discover).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                discover();
            }
        });

        findViewById(R.id.btn_send_photo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Allow user to pick an image from Gallery or other
                // registered apps, result returned in onActivityResult
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
            }
        });

        mPeersListView = (ListView) findViewById(R.id.peers_list);
        mWiFiPeerListAdapter = new WiFiPeerListAdapter(mActivity, R.layout.row_devices, peers);
        mPeersListView.setAdapter(mWiFiPeerListAdapter);
        mPeerListListener = new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peerList) {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                peers.clear();
                peers.addAll(peerList.getDeviceList());
                mWiFiPeerListAdapter.notifyDataSetChanged();
                if (peers.size() == 0) {
                    Log.d("toto", "No peer devices found!");
                    return;
                } else {
                    for (WifiP2pDevice d:peerList.getDeviceList()) {
                        Log.d("toto", "found peer device/state: " + d.deviceName + "/" + getDeviceStatus(d.status));
                    }
                }
            }
        };

        mConnectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(WifiP2pInfo info) {

                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                mActivity.info = info;

                if (info.groupFormed) {
                    if (initiatedConnection) {

                        /////////////////////////////////////////////////////////////////
                        // The device which initiated the connection will send the photos
                        /////////////////////////////////////////////////////////////////

                        findViewById(R.id.btn_send_photo).setVisibility(View.VISIBLE);
                        ((TextView) findViewById(R.id.status_text)).setText("Ready to Send Photo");

                    } else {

                        findViewById(R.id.btn_send_photo).setVisibility(View.GONE);

                        new FileServerAsyncTask(mActivity, findViewById(R.id.status_bar)).execute();
                    }
                }

            }
        };
    }

    private void discover() {

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = ProgressDialog.show(mActivity, "Press back to cancel", "finding peers", true,
                true, new DialogInterface.OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {

                    }
                });

        mWifiP2pManager.discoverPeers(mWifiP2pChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(mActivity, "Discovery Initiated", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(mActivity, "Discovery Failed : " + reasonCode, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void connect() {
        initiatedConnection = true;
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = remoteConnectedDevice.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = ProgressDialog.show(mActivity, "Press back to cancel",
                "Connecting to :" + remoteConnectedDevice.deviceAddress, true, true);

        mWifiP2pManager.connect(mWifiP2pChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us, nothing to do here
            }

            @Override
            public void onFailure(int reason) {
                initiatedConnection = false;
                Toast.makeText(mActivity, "Connect failed. Please retry.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void disconnect() {

        mWifiP2pManager.removeGroup(mWifiP2pChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d("toto", "Disconnect failed. Reason: " + reasonCode);
            }

            @Override
            public void onSuccess() {
                initiatedConnection = false;
                resetDetailsData();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mWifiP2pReceiver = new WiFiDirectBroadcastReceiver(mWifiP2pManager, mWifiP2pChannel, this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        // as soon as we register we will start receiving events
        registerReceiver(mWifiP2pReceiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mWifiP2pReceiver != null) {
            unregisterReceiver(mWifiP2pReceiver);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        // User has picked an image, send it
        Uri uri = data.getData();
        //sendData(uri);
        sendData(getPathFromUri(uri));
    }

    // Sends image using the FileTransferService
    private void sendData(Uri uri) {
        ((TextView) findViewById(R.id.status_text)).setText("Sending...");
        Log.d("toto", "Sending uri: " + uri);
        Intent serviceIntent = new Intent(mActivity, FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_URI, uri.toString());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS, info.groupOwnerAddress.getHostAddress());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, SOCKET_PORT);
        mActivity.startService(serviceIntent);
    }

    // Sends image using the FileTransferService
    private void sendData(String  path) {
        ((TextView) findViewById(R.id.status_text)).setText("Sending...");
        Log.d("toto", "Sending path: " + path);
        Intent serviceIntent = new Intent(mActivity, FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, path);
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS, info.groupOwnerAddress.getHostAddress());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, SOCKET_PORT);
        mActivity.startService(serviceIntent);
    }


    /*
    Open a socket connection and wait for the data to be sent
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {
        private Context context;
        private View statusBar;

        public FileServerAsyncTask(Context context, View statusBar) {
            this.context = context;
            this.statusBar = statusBar;
        }

        @Override
        protected void onPreExecute() {
            ((TextView) statusBar.findViewById(R.id.status_text)).setText("Ready to Receive Photo");
            statusBar.findViewById(R.id.open_received_photo).setVisibility(View.GONE);
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                ServerSocket serverSocket = new ServerSocket(MainActivity.SOCKET_PORT);
                Socket client = serverSocket.accept();
                InputStream inputstream = client.getInputStream();
                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");
                File dirs = new File(f.getParent());
                if (!dirs.exists()) {
                    dirs.mkdirs();
                }
                f.createNewFile();
                copyFile(inputstream, new FileOutputStream(f));

                Log.e("toto", "Photo received: " + f.getAbsolutePath());

                serverSocket.close();
                return f.getAbsolutePath();
            } catch (IOException e) {
                Log.e("toto", e.getMessage());
                return null;
            }
        }

        // this will trigger when the data is sent over the
        // socket connection from the FileTransferService
        @Override
        protected void onPostExecute(final String absolutePath) {
            if (!TextUtils.isEmpty(absolutePath)) {
                ImageView imageView = (ImageView) statusBar.findViewById(R.id.open_received_photo);
                imageView.setImageBitmap(BitmapFactory.decodeFile(absolutePath));
                imageView.setVisibility(View.VISIBLE);
                ((TextView) statusBar.findViewById(R.id.status_text)).setText("Received file:\n" + absolutePath);
                statusBar.findViewById(R.id.open_received_photo).setVisibility(View.VISIBLE);
                statusBar.findViewById(R.id.open_received_photo).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        viewImageFile(context, absolutePath);
                    }
                });
            }
        }
    }

    private static void viewImageFile(Context context, String absolutePath) {
        Intent intent = new Intent();
        intent.setAction(android.content.Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("file://" + absolutePath), "image/*");
        context.startActivity(intent);
    }


    /*
    Peers list view adapter
     */
    private class WiFiPeerListAdapter extends ArrayAdapter<WifiP2pDevice> {

        private List<WifiP2pDevice> devices;
        private LayoutInflater inflater;

        public WiFiPeerListAdapter(Context context, int textViewResourceId, List<WifiP2pDevice> objects) {
            super(context, textViewResourceId, objects);
            devices = objects;
            inflater = (LayoutInflater) mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = inflater.inflate(R.layout.row_devices, null);
            }
            final WifiP2pDevice device = devices.get(position);
            if (device != null) {
                TextView top = (TextView) v.findViewById(R.id.device_name);
                TextView bottom = (TextView) v.findViewById(R.id.device_details);
                if (top != null) {
                    top.setText(device.deviceName);
                }
                if (bottom != null) {
                    bottom.setText(getDeviceStatus(device.status));
                    if (device.status == WifiP2pDevice.CONNECTED) {
                        bottom.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        updateRemoteDevice(device);
                    } else {
                        bottom.setTextColor(getResources().getColor(android.R.color.black));
                    }
                }
                if (connected(devices)) {
                    if (device.status == WifiP2pDevice.CONNECTED) {
                        v.findViewById(R.id.btn_connect).setVisibility(View.GONE);
                        v.findViewById(R.id.btn_disconnect).setVisibility(View.VISIBLE);
                        v.findViewById(R.id.btn_disconnect).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                disconnect();
                            }
                        });
                    } else {
                        v.findViewById(R.id.btn_connect).setVisibility(View.GONE);
                        v.findViewById(R.id.btn_disconnect).setVisibility(View.GONE);
                    }
                } else {
                    v.findViewById(R.id.btn_disconnect).setVisibility(View.GONE);
                    v.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
                    v.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            remoteConnectedDevice = device;
                            connect();
                        }
                    });
                }
            }
            return v;
        }
    }

    private boolean connected(List<WifiP2pDevice> devices) {
        for (WifiP2pDevice device:devices) {
            if (device.status == WifiP2pDevice.CONNECTED) {
                return true;
            }
        }
        return false;
    }


    /*
    Utils
     */
    public static String getDeviceStatus(int deviceStatus) {
        switch (deviceStatus) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Unknown";

        }
    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d("toto", e.toString());
            return false;
        }
        return true;
    }

    private String getPathFromUri(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        CursorLoader loader = new CursorLoader(mActivity, contentUri, proj, null, null, null);
        Cursor cursor = loader.loadInBackground();
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String result = cursor.getString(column_index);
        cursor.close();
        return result;
    }

}
