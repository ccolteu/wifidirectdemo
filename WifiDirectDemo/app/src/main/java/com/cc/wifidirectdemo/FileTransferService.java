package com.cc.wifidirectdemo;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.util.ArrayList;

// send data to WiFi Direct Group Owner
// via a socket connection
public class FileTransferService extends IntentService {

    private static final int SOCKET_TIMEOUT = 30000;
    public static final String ACTION_SEND_FILE = "com.cc.wifidirectdemo.SEND_FILE";
    public static final String EXTRAS_FILE_URI = "file_uri";
    public static final String EXTRAS_FILE_PATH = "file_path";
    public static final String EXTRAS_FILE_PATHS = "file_paths";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";

    public FileTransferService(String name) {
        super(name);
    }

    public FileTransferService() {
        super("FileTransferService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Context context = getApplicationContext();
        if (intent.getAction().equals(ACTION_SEND_FILE)) {
            Bundle extras = intent.getExtras();
            String fileUri = null;
            String filePath = null;
            ArrayList<String> filePaths = null;
            if (extras.containsKey(EXTRAS_FILE_URI)) {
                fileUri = extras.getString(EXTRAS_FILE_URI);
            } else if (extras.containsKey(EXTRAS_FILE_PATH)) {
                filePath = extras.getString(EXTRAS_FILE_PATH);
            } else if (extras.containsKey(EXTRAS_FILE_PATHS)) {
                filePaths = extras.getStringArrayList(EXTRAS_FILE_PATHS);
            }
            String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
            int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);
            if (filePaths != null && filePaths.size() > 0) {
                // multiple files
                for (String path : filePaths) {
                    sendFile(context, host, port, null, path, filePaths);
                }
            } else {
                // single file
                sendFile(context, host, port, fileUri, filePath, filePaths);
            }
        }
    }

    private void sendFile(Context context, String host, int port, String fileUri, String filePath, ArrayList<String> filePaths) {

        Log.e("toto", "Sender: send " + (!TextUtils.isEmpty(fileUri) ? fileUri : filePath));

        Socket socket = new Socket();
        try {
            socket.bind(null);

            Log.e("toto", "Sender: connect on " + host + ":" + port);

            // TODO failed to connect to /192.168.49.1 (port 8988) after 5000ms: isConnected failed: ECONNREFUSED (Connection refused)
            socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

            OutputStream stream = socket.getOutputStream();
            InputStream is = null;
            try {
                if (!TextUtils.isEmpty(fileUri)) {
                    ContentResolver cr = context.getContentResolver();
                    is = cr.openInputStream(Uri.parse(fileUri));
                } else if (!TextUtils.isEmpty(filePath)) {
                    is = new FileInputStream(filePath);
                }
            } catch (FileNotFoundException e) {
                Log.e("toto", "Sender: file not found ERROR: " + e.getMessage());
                e.printStackTrace();
            }
            MainActivity.copyFile(is, stream);
        } catch (IOException e) {
            Log.e("toto", "Sender: connection ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (socket != null) {
                if (socket.isConnected()) {
                    try {
                        Log.e("toto", "Sender: try closing socket");
                        socket.close();
                        Log.e("toto", "Sender: socket closed successfully");
                    } catch (IOException e) {
                        Log.e("toto", "Sender: socket close ERROR: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

}
