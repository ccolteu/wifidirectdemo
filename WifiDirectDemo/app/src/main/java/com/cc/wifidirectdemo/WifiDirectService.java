package com.cc.wifidirectdemo;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
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

public class WifiDirectService extends Service {

    public interface WifiDirectServiceCallbacks {
        void dismissProgress();
        void showDiscoverUI();
        void updateThisDeviceUI(WifiP2pDevice device);
        void refreshPeersUI(List<WifiP2pDevice> peers);
        void resetPeersUI();
        void showConnectingDialog(String deviceAddress);
        void newConnectionInfoUpdateSenderUI();
        void newConnectionInfoUpdateReceiverUI();
        void updateRemoteDevicesTitle(String title);
        void showReadyToReceiveUI();
        void showPhotoReceivedUI(String path);
        void resetPhotoReceivedUI();
    }

    private WifiDirectService mService;

    private WifiDirectServiceCallbacks listener;

    private List<WifiP2pDevice> peers = new ArrayList<>();

    public final static boolean USE_REFLECTION_TO_FILTER_OUT_NON_SUPPORTED_DEVICES = true;
    public static final int SOCKET_PORT = 8988;
    private WifiP2pManager mWifiP2pManager;
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = true;

    private WifiP2pManager.Channel mWifiP2pChannel;
    private BroadcastReceiver mWifiP2pReceiver = null;
    public ArrayList<String> receiversIPs = new ArrayList<>();
    private WifiP2pManager.PeerListListener mPeerListListener;

    private WifiP2pManager.ConnectionInfoListener mConnectionInfoListener;

    private boolean initiatedConnection = false;
    private boolean isConnected = false;

    // used by Receiver(s) to receive photos
    private Thread socketServerThread;
    private ServerSocket serverSocket;

    // used by Sender to receive Receiver(s) IP(s)
    private Thread ipSocketServerThread;
    private ServerSocket ipServerSocket;

    private WifiP2pDevice thisDevice;


    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    // Binder given to clients
    private final IBinder mBinder = new WifiServiceBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class WifiServiceBinder extends Binder {
        WifiDirectService getService() {

            // Return this instance of WifiDirectService
            // so clients can call public methods
            return WifiDirectService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // If your service is started and accepts binding, then when the system calls your onUnbind()
    // method, you can optionally return true if you would like to receive a call to onRebind()
    // the next time a client binds to the service. onRebind() returns void, but the client still
    // receives the IBinder in its onServiceConnected() callback
    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    // When a service is unbound from all clients, the Android system destroys it.
    // However, if you choose to implement the onStartCommand() callback method, then you must
    // explicitly stop the service, because the service is now considered to be started. In this
    // case, the service runs until the service stops itself with stopSelf() or another component
    // calls stopService(), regardless of whether it is bound to any clients.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mService = this;

        /*
        used by WifiDirectBroadcastReceiver
         */
        mPeerListListener = new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peerList) {

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

                if (listener != null) {
                    listener.refreshPeersUI(peers);
                }

            }
        };


        /*
        used by WifiDirectBroadcastReceiver
         */
        mConnectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(WifiP2pInfo info) {

                if (listener != null) {
                    listener.dismissProgress();
                }

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

                        if (listener != null) {
                            listener.newConnectionInfoUpdateSenderUI();
                        }

                        // never-ending blocking thread waiting to receive Receivers IPs
                        ipSocketServerThread = new Thread(new IPSocketServerThread());
                        ipSocketServerThread.start();

                    } else {

                        if (info.isGroupOwner) {
                            // this should not happen often - we did request to be
                            // group owner, but just in case we'll have to try again

                            Log.e("toto", "Receiver: requested NOT to be Group Owner, was not granted the request, so disconnect and have the user try again");

                            disconnect();
                            return;
                        }

                        // Receiver

                        Log.e("toto", "I am the Receiver, I" + (info.isGroupOwner? " AM ": " am NOT ") + "the Group Owner");

                        // send my IP to the Sender (so that it knows to send me the photos)
                        final WifiP2pInfo infoCopy = info;
                        new android.os.Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            Socket socket = new Socket();
                                            socket.bind(null);
                                            socket.connect((new InetSocketAddress(infoCopy.groupOwnerAddress.getHostAddress(), SOCKET_PORT)), 30000);
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

                        if (listener != null) {
                            listener.newConnectionInfoUpdateReceiverUI();
                        }

                        Log.e("toto", "Receiver: create a server thread waiting for Sender to open a connection");

                        // never-ending blocking thread waiting to receive Sender photos
                        socketServerThread = new Thread(new SocketServerThread());
                        socketServerThread.start();
                    }
                }
            }
        };

        /*
         register with the WifiP2p framework and get a channel
          */
        mWifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mWifiP2pChannel = mWifiP2pManager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {

                if (mWifiP2pManager != null && retryChannel) {
                    Toast.makeText(mService, "Channel lost. Trying again", Toast.LENGTH_LONG).show();

                    // we will try once more
                    if (listener != null) {
                        listener.resetPeersUI();
                        resetDetailsData();
                    }
                    retryChannel = false;
                    mWifiP2pManager.initialize(mService, getMainLooper(), this);

                } else {
                    Toast.makeText(mService, "Severe! Channel is probably permanently lost. Try Disable/Re-Enable P2P.", Toast.LENGTH_LONG).show();
                }
            }
        });

        /*
        filter out non supported devices by sending a specific the device name to the WifiP2p framework
         */
        if (USE_REFLECTION_TO_FILTER_OUT_NON_SUPPORTED_DEVICES) {
            String deviceId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
            Utils.setDeviceNameViaReflection("com.cc.wifidirectdemo" + deviceId, mWifiP2pManager, mWifiP2pChannel);
        }

        /*
        register for broadcasts
         */
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
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e("toto", "WifiDirectService:onDestroy");

        disconnect();
        stopServerThreads();
        if (mWifiP2pReceiver != null) {
            unregisterReceiver(mWifiP2pReceiver);
        }
    }

    /** method for clients */

    public void setCallback(WifiDirectServiceCallbacks listener) {
        this.listener = listener;
    }

    public boolean initiatedConnection() {
        return initiatedConnection;
    }

    public void stopServerThreads() {
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

    public void sendPhotos(ArrayList<MetaData> data) {
        Intent serviceIntent = new Intent(mService, FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putParcelableArrayListExtra(FileTransferService.EXTRAS_FILES, data);
        serviceIntent.putStringArrayListExtra(FileTransferService.EXTRAS_CLIENTS_IPS, receiversIPs);
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, SOCKET_PORT);
        mService.startService(serviceIntent);
    }

    public void resetDetailsData() {
        if (listener != null) {
            listener.resetPhotoReceivedUI();
        }

        mService.stopServerThreads();
    }

    public void discover() {

        if (listener != null) {
            listener.showDiscoverUI();
        }

        mWifiP2pManager.discoverPeers(mWifiP2pChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(mService, "Discovery Initiated", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(mService, "Discovery Failed : " + reasonCode, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void connect(String deviceAddress) {
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

        if (listener != null) {
            listener.showConnectingDialog(deviceAddress);
        }

        mWifiP2pManager.connect(mWifiP2pChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us, nothing to do here
            }

            @Override
            public void onFailure(int reason) {
                initiatedConnection = false;
                Toast.makeText(mService, "Connect failed. Please retry.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void disconnect() {

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

        try {
            if (serverSocket != null) {
                // this will unblock ipSocketServerThread: accept() will
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

        receiversIPs.clear();

        if (listener != null) {
            listener.updateRemoteDevicesTitle("SEND TO THESE REMOTE DEVICES");
        }

        discover();
    }

    // This is a blocking thread (accept() is blocking) that runs forever (while (true)).
    // To stop it you just close the ipServerSocket which will have accept() throw a SocketException
    // thus unblocking the thread and terminating it.
    // This thread is initiated by the Sender and is used to receive Receiver(s) IP(s)
    private class IPSocketServerThread extends Thread {

        @Override
        public void run() {
            try {
                ipServerSocket = new ServerSocket(WifiDirectService.SOCKET_PORT);
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

                // post to UI thread from within a Thread within a Service
                new Handler(Looper.getMainLooper()).post(new Runnable() {

                    @Override
                    public void run() {

                        if (listener != null) {
                            listener.showReadyToReceiveUI();
                        }
                    }
                });

                serverSocket = new ServerSocket(WifiDirectService.SOCKET_PORT);

                while (true) {

                    Log.e("toto", "Receiver: blocked, waiting for Sender to open connection on port " + serverSocket.getLocalPort());
                    // Waits for an incoming request and blocks until the connection is opened.
                    // This method returns a socket object representing the just opened connection.
                    Socket clientSocket = serverSocket.accept();
                    Log.e("toto", "Receiver: unblocked, accepted Sender connection on " + clientSocket.getLocalAddress() + ":" + clientSocket.getLocalPort());
                    count++;

                    InputStream inputstream = clientSocket.getInputStream();
                    final File file = new File(Environment.getExternalStorageDirectory() + "/"
                            + mService.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
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
                                + mService.getPackageName() + "/" + fileName);
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
                    new Handler(Looper.getMainLooper()).post(new Runnable() {

                        @Override
                        public void run() {

                            if (listener != null) {
                                listener.showPhotoReceivedUI(absolutePath);
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

    public void updateThisDevice(WifiP2pDevice device) {
        thisDevice = device;
        if (listener != null) {
            listener.updateThisDeviceUI(device);
        }
    }

    public void resetPeers() {
        if (listener != null) {
            listener.resetPeersUI();
        }
    }

    public List<WifiP2pDevice> getPeers() {
        return peers;
    }

    public WifiP2pDevice getThisDevice() {
        return thisDevice;
    }

}
