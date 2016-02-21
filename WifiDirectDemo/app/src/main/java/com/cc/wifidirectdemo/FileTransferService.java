package com.cc.wifidirectdemo;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

// send data to WiFi Direct Group Owner
// via a socket connection
public class FileTransferService extends IntentService {

    private static final int SOCKET_TIMEOUT = 5000;
    public static final String ACTION_SEND_FILE = "com.cc.wifidirectdemo.SEND_FILE";
    public static final String EXTRAS_FILE_URI = "file_uri";
    public static final String EXTRAS_FILE_PATH = "file_path";
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
            if (extras.containsKey(EXTRAS_FILE_URI)) {
                fileUri = extras.getString(EXTRAS_FILE_URI);
            } else if (extras.containsKey(EXTRAS_FILE_PATH)) {
                filePath = extras.getString(EXTRAS_FILE_PATH);
            }
            String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
            Socket socket = new Socket();
            int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);

            try {
                socket.bind(null);
                socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                OutputStream stream = socket.getOutputStream();
                ContentResolver cr = context.getContentResolver();
                InputStream is = null;
                try {
                    if (!TextUtils.isEmpty(fileUri)) {
                        is = cr.openInputStream(Uri.parse(fileUri));
                    } else if (!TextUtils.isEmpty(filePath)) {
                        is = new FileInputStream(filePath);
                    }
                } catch (FileNotFoundException e) {
                    Log.e("toto", e.toString());
                }
                MainActivity.copyFile(is, stream);
            } catch (IOException e) {
                Log.e("toto", e.getMessage());
            } finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            // Give up
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
}
