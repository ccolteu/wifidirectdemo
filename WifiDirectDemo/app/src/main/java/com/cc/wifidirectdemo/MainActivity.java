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
    private boolean isConnected = false;

    private AsyncTask mFileServerAsyncTask;
    private Thread socketServerThread;
    private ServerSocket serverSocket;
    private String serverIp;
    private View mStatusBar;

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
        findViewById(R.id.btn_send_photos).setVisibility(View.GONE);
        if (mFileServerAsyncTask!= null && mFileServerAsyncTask.getStatus() == AsyncTask.Status.RUNNING) {
            Log.e("toto", "Receiver: cancel AsyncTask waiting for Sender to open a connection");
            mFileServerAsyncTask.cancel(true);
        }
        try {
            if (serverSocket != null) {
                // this will unblock socketServerThread: accept() will
                // throw a SocketException and terminate the thread
                serverSocket.close();
            }
        } catch (IOException e) {

        }
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

    public void setIsConnected(boolean value) {
        isConnected = value;
    }

    public boolean isConnected() {
        return isConnected;
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

        mStatusBar = findViewById(R.id.status_bar);

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

        findViewById(R.id.btn_send_photos).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendPhotos(getPhotosPaths());
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

                    // The Group Owner should be acting as the server Receiving the photos,
                    // while the other device shall act as the client Sending the photos.
                    // This condition should really be "if (!info.isGroupOwner)"
                    // but we made sure that the device that initiates the connection
                    // (initiatedConnection is true) will not be the group owner by
                    // setting config.groupOwnerIntent = 0 when initiating the connection
                    if (initiatedConnection) {

                        // client / Sender

                        Log.e("toto", "I am the Sender, I" + (info.isGroupOwner? " AM ": " am NOT ") + "the group owner");
                        Log.e("toto", "Group owner IP: " + info.groupOwnerAddress.getHostAddress());

                        findViewById(R.id.btn_send_photo).setVisibility(View.VISIBLE);
                        findViewById(R.id.btn_send_photos).setVisibility(View.VISIBLE);
                        ((TextView) findViewById(R.id.status_text)).setText("Ready to Send Photo");

                    } else {

                        // server / Receiver

                        Log.e("toto", "I am the Receiver, I" + (info.isGroupOwner? " AM ": " am NOT ") + "the group owner");
                        Log.e("toto", "Group owner IP: " + info.groupOwnerAddress);

                        findViewById(R.id.btn_send_photo).setVisibility(View.GONE);
                        findViewById(R.id.btn_send_photos).setVisibility(View.GONE);

                        Log.e("toto", "Receiver: create a server thread waiting for Sender to open a connection");
                        serverIp = info.groupOwnerAddress.getHostAddress();
                        socketServerThread = new Thread(new SocketServerThread());
                        socketServerThread.start();
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


        // This is an integer value between 0 and 15 where 0 indicates the least
        // inclination to be a group owner and 15 indicates the highest inclination
        // to be a group owner. A value of -1 indicates the system can choose an appropriate value.
        // We want the device that initiates the connection to be the Sender, so we need
        // it NOT to be the group owner (the group owner always acts as the server / Receiver)
        config.groupOwnerIntent = 0;

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

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
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
        sendPhoto(getPathFromUri(uri));
    }

    private void sendPhoto(String  path) {
        ((TextView) findViewById(R.id.status_text)).setText("Sending...");
        Log.d("toto", "Sending path: " + path);
        Intent serviceIntent = new Intent(mActivity, FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, path);
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS, info.groupOwnerAddress.getHostAddress());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, SOCKET_PORT);
        mActivity.startService(serviceIntent);
    }

    private void sendPhotos(ArrayList<String> pathsArray) {

//        for (String path:pathsArray) {
//            sendPhoto(path);
//        }

        // alternate way that only creates on thread (service intent called only once)
        ((TextView) findViewById(R.id.status_text)).setText("Sending...");
        Intent serviceIntent = new Intent(mActivity, FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putStringArrayListExtra(FileTransferService.EXTRAS_FILE_PATHS, pathsArray);
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS, info.groupOwnerAddress.getHostAddress());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, SOCKET_PORT);
        mActivity.startService(serviceIntent);
    }

    private ArrayList<String> getPhotosPaths() {
        ArrayList<String> pathsArray = new ArrayList<>();
        pathsArray.add(Environment.getExternalStorageDirectory() + "/send/01.jpg");
        pathsArray.add(Environment.getExternalStorageDirectory() + "/send/02.jpg");
        return pathsArray;
    }


    // This is a blocking thread (serverSocket.accept()) that runs forever (while (true)).
    // To stop it you just close the serverSocket which will have accept() throw a SocketException
    // thus unblocking the thread and terminating it.
    private class SocketServerThread extends Thread {

        int count = 0;

        @Override
        public void run() {
            try {

                MainActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        ((TextView) mStatusBar.findViewById(R.id.status_text)).setText("Ready to Receive Photo");
                        mStatusBar.findViewById(R.id.open_received_photo).setVisibility(View.GONE);
                    }
                });

                serverSocket = new ServerSocket(MainActivity.SOCKET_PORT);

                while (true) {

                    Log.e("toto", "Receiver: blocked, waiting for Sender to open connection on port " + serverSocket.getLocalPort());
                    // Waits for an incoming request and blocks until the connection is opened.
                    // This method returns a socket object representing the just opened connection.
                    Socket clientSocket = serverSocket.accept();
                    Log.e("toto", "Receiver: unblocked, accepted Sender connection on " + clientSocket.getLocalAddress() + ":" + clientSocket.getLocalPort());
                    count++;

                    InputStream inputstream = clientSocket.getInputStream();
                    final File file = new File(Environment.getExternalStorageDirectory() + "/"
                            + mActivity.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                            + ".jpg");
                    File dirs = new File(file.getParent());
                    if (!dirs.exists()) {
                        dirs.mkdirs();
                    }
                    file.createNewFile();

                    // the name of the file received is sent along with
                    // the photo itself and extracted from the byte stream
                    String fileName = copyFile(inputstream, new FileOutputStream(file));

                    String receivedFilePath = file.getAbsolutePath();
                    if (!TextUtils.isEmpty(fileName)) {
                        Log.e("toto", "Receiver: received filename: " + fileName);
                        // rename with the name sent by the client
                        final File renamedFile = new File(Environment.getExternalStorageDirectory() + "/"
                                + mActivity.getPackageName() + "/" + fileName);
                        if (file.exists()) {
                            file.renameTo(renamedFile);
                            Log.e("toto", "Photo received: " + renamedFile.getAbsolutePath());
                            receivedFilePath = renamedFile.getAbsolutePath();
                        }
                    } else {
                        Log.e("toto", "Photo received: " + file.getAbsolutePath());
                    }

                    clientSocket.close();

                    final String absolutePath = receivedFilePath;
                    MainActivity.this.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            if (!TextUtils.isEmpty(absolutePath)) {
                                ImageView imageView = (ImageView) mStatusBar.findViewById(R.id.open_received_photo);
                                imageView.setImageBitmap(BitmapFactory.decodeFile(absolutePath));
                                imageView.setVisibility(View.VISIBLE);
                                ((TextView) mStatusBar.findViewById(R.id.status_text)).setText("Received file:\n" + absolutePath);
                                mStatusBar.findViewById(R.id.open_received_photo).setVisibility(View.VISIBLE);
                                mStatusBar.findViewById(R.id.open_received_photo).setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        viewImageFile(mActivity, absolutePath);
                                    }
                                });
                            }
                        }
                    });
                }
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
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

    private String copyFile(InputStream inputStream, OutputStream outputStream) {
        byte buf[] = new byte[1024];
        byte data[] = new byte[1024];
        String fileName = null;
        int len;
        try {

            // get data
            len = inputStream.read(data, 0, 1024);
            if (len != -1) {
                String dataString = new String(data);
                fileName = dataString.substring(0, dataString.indexOf("|"));
            }

            // get photo
            // inputStream.read(buf) reads up to buf.length (1024
            // bytes) at a time, returns the number of bytes read
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d("toto", e.toString());
            return null;
        }
        return fileName;
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
