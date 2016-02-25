package com.cc.wifidirectdemo;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;

// send data to WiFi Direct Group Owner
// via a socket connection
public class FileTransferService extends IntentService {

    private static final int SOCKET_TIMEOUT = 30000;
    public static final String ACTION_SEND_FILE = "com.cc.wifidirectdemo.SEND_FILE";
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
            String filePath = null;
            ArrayList<String> filePaths = null;
            if (extras.containsKey(EXTRAS_FILE_PATH)) {
                filePath = extras.getString(EXTRAS_FILE_PATH);
            } else if (extras.containsKey(EXTRAS_FILE_PATHS)) {
                filePaths = extras.getStringArrayList(EXTRAS_FILE_PATHS);
            }
            String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
            int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);
            if (filePaths != null && filePaths.size() > 0) {
                // multiple files
                for (String path : filePaths) {
                    sendFile(context, host, port, path, filePaths);
                }
            } else {
                // single file
                sendFile(context, host, port, filePath, filePaths);
            }
        }
    }

    private void sendFile(Context context, String host, int port, String filePath, ArrayList<String> filePaths) {

        Log.e("toto", "Sender: send " + filePath);

        Socket socket = new Socket();
        try {
            socket.bind(null);

            Log.e("toto", "Sender: connect on " + host + ":" + port);
            socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);
            OutputStream outputStream = socket.getOutputStream();

            InputStream inputStream = new FileInputStream(filePath);

            if (!TextUtils.isEmpty(filePath)) {
                String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
                copyFile(inputStream, outputStream, getDataBytes(fileName));
            } else {
                copyFile(inputStream, outputStream, null);
            }


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

    private boolean copyFile(InputStream inputStream, OutputStream outputStream, byte data[]) {
        byte buf[] = new byte[1024];
        int len;
        try {
            // write data
            if (data != null) {
                outputStream.write(data, 0, data.length);
            }
            // write photo
            // inputStream.read(buf) reads up to buf.length (1024
            // bytes) at a time, returns the number of bytes read
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d("toto", e.toString());
            return false;
        }
        return true;
    }

    // We send exactly 1024 Bytes of data so that we know a priori on
    // the receiving end how much the data represents out of the stream
    private byte[] getDataBytes(String fileName) {
        byte data[] = new byte[1024];
        String dataString = fileName + "|";
        byte dataBytes[] = dataString.getBytes();
        System.arraycopy(dataBytes, 0, data, 0, dataBytes.length);
        Log.e("toto", "Sender: sent data: " + new String(data));
        return data;
    }

}
