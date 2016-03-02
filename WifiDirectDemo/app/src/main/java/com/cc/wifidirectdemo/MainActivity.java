package com.cc.wifidirectdemo;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public final static boolean USE_REFLECTION_TO_FILTER_OUT_NON_SUPPORTED_DEVICES = true;

    public static final int SOCKET_PORT = 8988;

    private MainActivity mActivity;

    private WifiP2pManager mWifiP2pManager;
    private WifiP2pManager.ChannelListener mChannelListener;
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = true;

    private WifiP2pManager.Channel mWifiP2pChannel;
    private BroadcastReceiver mWifiP2pReceiver = null;

    private List<WifiP2pDevice> peers = new ArrayList<>();
    private ProgressDialog progressDialog = null;
    public ArrayList<String> receiversIPs = new ArrayList<>();

    private WifiP2pManager.PeerListListener mPeerListListener;
    private ListView mPeersListView;
    private WiFiPeerListAdapter mWiFiPeerListAdapter;

    private View mRefreshButton;

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private WifiP2pInfo info;

    private WifiP2pManager.ConnectionInfoListener mConnectionInfoListener;

    private boolean initiatedConnection = false;
    private boolean isConnected = false;

    // used by Receiver(s) to receive photos
    private Thread socketServerThread;
    private ServerSocket serverSocket;

    // used by Sender to receive Receiver(s) IP(s)
    private Thread ipSocketServerThread;
    private ServerSocket ipServerSocket;

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
        try {
            if (serverSocket != null) {
                // this will unblock socketServerThread: accept() will
                // throw a SocketException and terminate the thread
                serverSocket.close();
            }
        } catch (IOException e) {

        }

        try {
            if (ipServerSocket != null) {
                // this will unblock ipSocketServerThread: accept() will
                // throw a SocketException and terminate the thread
                ipServerSocket.close();
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
        TextView view = (TextView) findViewById(R.id.my_name);
        if (USE_REFLECTION_TO_FILTER_OUT_NON_SUPPORTED_DEVICES) {
            view.setText(device.deviceName.replace("com.cc.wifidirectdemo", ""));
        } else {
            view.setText(device.deviceName);
        }
        view = (TextView) findViewById(R.id.my_status);
        view.setText(getDeviceStatus(device.status));
        if (device.status == WifiP2pDevice.CONNECTED) {
            view.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            view.setTextColor(getResources().getColor(android.R.color.black));
        }
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

        mRefreshButton = findViewById(R.id.btn_refresh);
        mRefreshButton.setOnClickListener(new View.OnClickListener() {
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
                sendPhotos(getPhotosData());
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
                mRefreshButton.clearAnimation();
                peers.clear();

                if (USE_REFLECTION_TO_FILTER_OUT_NON_SUPPORTED_DEVICES) {
                    for (WifiP2pDevice d:peerList.getDeviceList()) {
                        if (!(TextUtils.isEmpty(d.deviceName)) && d.deviceName.toLowerCase().startsWith("com.cc.wifidirectdemo")) {
                            d.deviceName = d.deviceName.replace("com.cc.wifidirectdemo", "");
                            peers.add(d);
                        }
                    }
                } else {
                    peers.addAll(peerList.getDeviceList());
                }

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

                    // The Group Owner should be acting as the sender of the photos,
                    // while the other device(s) shall act as the receivers of the photos.
                    // This condition should really be "if (info.isGroupOwner)"
                    // but we made sure that the device that initiates the connection
                    // (initiatedConnection is true) will be the group owner by
                    // setting config.groupOwnerIntent = 15 when initiating the connection
                    if (initiatedConnection) {

                        if (!info.isGroupOwner) {
                            // this should not happen often - we did request to be
                            // group owner, but just in case we'll have to try again

                            Log.e("toto", "Sender: requested to be Group Owner, was not granted the request, so disconnect and have the user try again");

                            disconnect();
                            return;
                        }

                        // Sender (Group Owner)

                        Log.e("toto", "I am the Sender, I" + (info.isGroupOwner? " AM ": " am NOT ") + "the Group Owner");

                        findViewById(R.id.btn_send_photo).setVisibility(View.VISIBLE);
                        //findViewById(R.id.btn_send_photos).setVisibility(View.VISIBLE);
                        ((TextView) findViewById(R.id.status_text)).setText("Ready to Send Photo");

                        // never-ending blocking thread waiting to receive Receivers IPs
                        ipSocketServerThread = new Thread(new IPSocketServerThread());
                        ipSocketServerThread.start();

                    } else {

                        // Receiver

                        Log.e("toto", "I am the Receiver, I" + (info.isGroupOwner? " AM ": " am NOT ") + "the Group Owner");

                        // send my IP to the Sender (so that it knows to send me the photos)
                        new android.os.Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            Socket socket = new Socket();
                                            socket.bind(null);
                                            socket.connect((new InetSocketAddress(mActivity.info.groupOwnerAddress.getHostAddress(), SOCKET_PORT)), 30000);
                                            OutputStream os = socket.getOutputStream();
                                            ObjectOutputStream oos = new ObjectOutputStream(os);
                                            oos.writeObject(new String("BROFIST"));
                                            oos.close();
                                            os.close();
                                            socket.close();
                                            Log.e("toto", "sent IP to GO");
                                        } catch (Exception e) {
                                            Log.e("toto", "ERROR sending IP to GO: " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                    }
                                }).start();
                            }
                        }, 500);

                        findViewById(R.id.btn_send_photo).setVisibility(View.GONE);
                        findViewById(R.id.btn_send_photos).setVisibility(View.GONE);

                        Log.e("toto", "Receiver: create a server thread waiting for Sender to open a connection");

                        // never-ending blocking thread waiting to receive Sender photos
                        socketServerThread = new Thread(new SocketServerThread());
                        socketServerThread.start();
                    }
                }
            }
        };

        if (USE_REFLECTION_TO_FILTER_OUT_NON_SUPPORTED_DEVICES) {
            String deviceId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
            Utils.setDeviceNameViaReflection("com.cc.wifidirectdemo" + deviceId, mWifiP2pManager, mWifiP2pChannel);
        }

        discover();
    }

    public void discover() {

        mRefreshButton.startAnimation(AnimationUtils.loadAnimation(mActivity, R.anim.rotation));

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

    private void connect(String deviceAddress) {
        initiatedConnection = true;
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;
        config.wps.setup = WpsInfo.PBC;


        // This is an integer value between 0 and 15 where 0 indicates the least
        // inclination to be a group owner and 15 indicates the highest inclination
        // to be a group owner. A value of -1 indicates the system can choose an appropriate value.
        // We want the device that initiates the connection to be the Sender, and we need it to
        // send to multiple devices, so it needs to be the Group Owner so it can collect IPs from
        // Receiver(s) to know to whom to send the photos.
        config.groupOwnerIntent = 15;

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = ProgressDialog.show(mActivity, "Press back to cancel",
                "Connecting to :" + deviceAddress, true, true);

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

        receiversIPs.clear();

        discover();
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

        String path = Utils.getPath(this.getBaseContext(), uri);

        ArrayList<MetaData> photosData = new ArrayList<>();
        MetaData photoData = new MetaData();
        photoData.absolute_path = path;
        photoData.filename = path.substring(path.lastIndexOf("/") + 1);
        photosData.add(photoData);

        sendPhotos(photosData);
    }

    private void sendPhotos(ArrayList<MetaData> data) {
        ((TextView) findViewById(R.id.status_text)).setText("Sending...");
        Intent serviceIntent = new Intent(mActivity, FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putParcelableArrayListExtra(FileTransferService.EXTRAS_FILES, data);
        serviceIntent.putStringArrayListExtra(FileTransferService.EXTRAS_CLIENTS_IPS, receiversIPs);
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, SOCKET_PORT);
        mActivity.startService(serviceIntent);
    }

    private ArrayList<MetaData> getPhotosData() {

        ArrayList<MetaData> photosData = new ArrayList<>();

        String path = Environment.getExternalStorageDirectory() + "/send/01.jpg";
        MetaData photoData = new MetaData();
        photoData.absolute_path = path;
        photoData.filename = path.substring(path.lastIndexOf("/") + 1);
        photosData.add(photoData);

        path = Environment.getExternalStorageDirectory() + "/send/02.jpg";
        photoData = new MetaData();
        photoData.absolute_path = path;
        photoData.filename = path.substring(path.lastIndexOf("/") + 1);
        photosData.add(photoData);

        return photosData;
    }

    // This is a blocking thread (accept() is blocking) that runs forever (while (true)).
    // To stop it you just close the ipServerSocket which will have accept() throw a SocketException
    // thus unblocking the thread and terminating it.
    // This thread is initiated by the Sender and is used to receive Receiver(s) IP(s)
    private class IPSocketServerThread extends Thread {

        @Override
        public void run() {
            try {
                ipServerSocket = new ServerSocket(MainActivity.SOCKET_PORT);
                while (true) {
                    Log.e("toto", "Sender: blocked, waiting for Receiver IP ...");
                    Socket socket = ipServerSocket.accept();
                    ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
                    Object object = objectInputStream.readObject();
                    if (object.getClass().equals(String.class) && ((String) object).equals("BROFIST")) {
                        Log.e("toto", "Sender: ... unblocked, got Receiver IP: " + socket.getInetAddress());
                        receiversIPs.add(socket.getInetAddress().getHostAddress());
                    }
                    socket.close();
                }
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    // This is a blocking thread (accept() is blocking) that runs forever (while (true)).
    // To stop it you just close the serverSocket which will have accept() throw a SocketException
    // thus unblocking the thread and terminating it.
    // This thread is initiated by the Receiver and is used to receive photos
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
                    MetaData metaData = copyFile(inputstream, new FileOutputStream(file));
                    String fileName = metaData.filename;

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
                    } else {
                        bottom.setTextColor(getResources().getColor(android.R.color.black));
                    }
                }

                if (initiatedConnection) {
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
                        v.findViewById(R.id.btn_disconnect).setVisibility(View.GONE);
                        v.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
                        v.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                connect(device.deviceAddress);
                            }
                        });
                    }
                } else {
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
                                connect(device.deviceAddress);
                            }
                        });
                    }
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

    private MetaData copyFile(InputStream inputStream, OutputStream outputStream) {
        byte buf[] = new byte[1024];
        byte data[] = new byte[1024];
        MetaData metaData = null;
        int len;
        try {

            // get data
            len = inputStream.read(data, 0, 1024);
            if (len != -1) {
                String dataString = new String(data);
                metaData = new MetaData();
                String s[] = dataString.split("\\|");
                metaData.filename = s[0];
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
        return metaData;
    }

}
