package com.tagkast.mywifidirectdemo;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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

public class MainActivity extends AppCompatActivity implements WifiP2pManager.ChannelListener {

    private MainActivity mActivity;

    private WifiP2pManager manager;
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;

    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver = null;


    private List<WifiP2pDevice> peers = new ArrayList<>();
    ProgressDialog progressDialog = null;
    View detailView = null;
    private WifiP2pDevice device;

    private WifiP2pManager.PeerListListener mPeerListListener;
    private ListView mPeersListView;
    private WiFiPeerListAdapter mWiFiPeerListAdapter;

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private WifiP2pInfo info;

    private WifiP2pManager.ConnectionInfoListener mConnectionInfoListener;


    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    //ChannelListener
    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            resetPeers();
            resetDetailsData();
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

    public void resetPeers() {
        peers.clear();
        mWiFiPeerListAdapter.notifyDataSetChanged();
    }

    public void resetDetailsData() {
        ((TextView) detailView.findViewById(R.id.device_address)).setText("");
        ((TextView) detailView.findViewById(R.id.device_info)).setText("");
        ((TextView) detailView.findViewById(R.id.group_owner)).setText("");
        ((TextView) detailView.findViewById(R.id.status_text)).setText("");
        detailView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        detailView.findViewById(R.id.open_received_photo).setVisibility(View.GONE);
        detailView.findViewById(R.id.btn_send_photo).setVisibility(View.GONE);
        detailView.setVisibility(View.GONE);
    }

    public WifiP2pManager.PeerListListener getPeerListListener() {
        return mPeerListListener;
    }

    public WifiP2pManager.ConnectionInfoListener getConnectionInfoListener() {
        return mConnectionInfoListener;
    }

    public void updateThisDevice(WifiP2pDevice device) {
        this.device = device;
        TextView view = (TextView) findViewById(R.id.my_name);
        view.setText(device.deviceName);
        view = (TextView) findViewById(R.id.my_status);
        view.setText(getDeviceStatus(device.status));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = this;

        setContentView(R.layout.activity_main);

        detailView = findViewById(R.id.detail);
        detailView.setVisibility(View.GONE);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        findViewById(R.id.btn_discover).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                discover();
            }
        });

        findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connect();
            }
        });

        findViewById(R.id.btn_disconnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect();
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
                    Log.d("toto", "No devices found");
                    return;
                }
            }
        };
        mPeersListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                device = mWiFiPeerListAdapter.getItem(position);
                detailView.setVisibility(View.VISIBLE);
                detailView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
                detailView.findViewById(R.id.btn_disconnect).setVisibility(View.GONE);
                detailView.findViewById(R.id.btn_send_photo).setVisibility(View.GONE);
                ((TextView) detailView.findViewById(R.id.device_address)).setText("Address: " + device.deviceAddress);
                ((TextView) detailView.findViewById(R.id.device_info)).setText("Name: " + device.deviceName); //device.toString()
            }
        });


        mConnectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(WifiP2pInfo info) {

                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                mActivity.info = info;

                detailView.setVisibility(View.VISIBLE);

                // update UI
                ((TextView) detailView.findViewById(R.id.group_owner)).setText("Am I the Group Owner? " + ((info.isGroupOwner == true) ? "Yes" : "No"));
                ((TextView) detailView.findViewById(R.id.device_info)).setText("Group Owner IP: " + info.groupOwnerAddress.getHostAddress());

                // After the group negotiation, we assign the group owner as the file
                // server. The file server is single threaded, single connection server
                // socket.
                if (info.groupFormed && info.isGroupOwner) {

                    detailView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
                    detailView.findViewById(R.id.btn_disconnect).setVisibility(View.VISIBLE);
                    detailView.findViewById(R.id.btn_send_photo).setVisibility(View.GONE);

                    new FileServerAsyncTask(mActivity, detailView.findViewById(R.id.status_bar)).execute();

                } else if (info.groupFormed) {

                    detailView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
                    detailView.findViewById(R.id.btn_disconnect).setVisibility(View.VISIBLE);
                    detailView.findViewById(R.id.btn_send_photo).setVisibility(View.VISIBLE);
                    ((TextView) detailView.findViewById(R.id.status_text)).setText("Ready to Send Photo");
                }
            }
        };
    }

    private void discover() {
        detailView.setVisibility(View.GONE);

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = ProgressDialog.show(mActivity, "Press back to cancel", "finding peers", true,
                true, new DialogInterface.OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {

                    }
                });

        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

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
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = ProgressDialog.show(mActivity, "Press back to cancel",
                "Connecting to :" + device.deviceAddress, true, true);

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(mActivity, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void disconnect() {
        resetDetailsData();

        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d("toto", "Disconnect failed. Reason: " + reasonCode);
            }

            @Override
            public void onSuccess() {
                detailView.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        // User has picked an image, send it
        sendData(data.getData());
    }

    // Sends image to the Group Owner using the FileTransferService
    private void sendData(Uri uri) {
        TextView statusText = (TextView) detailView.findViewById(R.id.status_text);
        statusText.setText("Sending: " + uri);
        Log.d("toto", "=== Sending: " + uri);
        Intent serviceIntent = new Intent(mActivity, FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS, info.groupOwnerAddress.getHostAddress());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
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
                ServerSocket serverSocket = new ServerSocket(8988);
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
                statusBar.findViewById(R.id.open_received_photo).setVisibility(View.VISIBLE);
                ((TextView) statusBar.findViewById(R.id.status_text)).setText("Received file: " + absolutePath);
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

        private List<WifiP2pDevice> items;
        private LayoutInflater inflater;

        public WiFiPeerListAdapter(Context context, int textViewResourceId, List<WifiP2pDevice> objects) {
            super(context, textViewResourceId, objects);
            items = objects;
            inflater = (LayoutInflater) mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = inflater.inflate(R.layout.row_devices, null);
            }
            WifiP2pDevice device = items.get(position);
            if (device != null) {
                TextView top = (TextView) v.findViewById(R.id.device_name);
                TextView bottom = (TextView) v.findViewById(R.id.device_details);
                if (top != null) {
                    top.setText(device.deviceName);
                }
                if (bottom != null) {
                    bottom.setText(getDeviceStatus(device.status));
                }
            }
            return v;
        }
    }


    /*
    Utils
     */
    private static String getDeviceStatus(int deviceStatus) {
        Log.d("toto", "Peer status :" + deviceStatus);
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

}
