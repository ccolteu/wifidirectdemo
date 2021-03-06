package com.cc.wifidirectdemo;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v7.app.ActionBar;
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

import java.util.ArrayList;
import java.util.List;

public class WifiDirectActivity extends AppCompatActivity {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;

    private WifiDirectActivity mActivity;
    private WifiDirectService mService;
    private boolean mBound = false;
    WifiDirectService.WifiDirectServiceCallbacks serviceCallbacks;

    private ProgressDialog progressDialog = null;

    private ListView mPeersListView;
    private WiFiPeerListAdapter mWiFiPeerListAdapter;
    private List<WifiP2pDevice> peers = new ArrayList<>();

    private View mRefreshButton;
    private TextView mThisDeviceTitle;
    private View mThisDeviceUnderline;
    private TextView mRemoteDevicesTitle;
    private View mRemoteDevicesUnderline;
    private View mStatusBar;

    private static boolean active = false;

    private String deviceColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = this;
        setContentView(R.layout.activity_main);
        String deviceId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId.length() > 6) {
            deviceColor = deviceId.substring(0, 6);
            deviceColor = Utils.transformColor(deviceColor);
        } else {
            deviceColor = deviceId;
        }

        ActionBar actionBar = getSupportActionBar();
        actionBar.setBackgroundDrawable(new ColorDrawable(Integer.parseInt(deviceColor, 16) + 0xFF000000));
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowTitleEnabled(true);

        /*
        UI setup
         */
        mThisDeviceTitle = (TextView) findViewById(R.id.this_device_title);
        mThisDeviceUnderline = findViewById(R.id.this_device_underline);
        mRemoteDevicesTitle = (TextView) findViewById(R.id.remote_devices_title);
        mRemoteDevicesUnderline = findViewById(R.id.remote_devices_underline);
        mStatusBar = findViewById(R.id.status_bar);
        setDeviceColor();

        mPeersListView = (ListView) findViewById(R.id.peers_list);
        mWiFiPeerListAdapter = new WiFiPeerListAdapter(mActivity, R.layout.row_devices, peers);
        mPeersListView.setAdapter(mWiFiPeerListAdapter);
        mRefreshButton = findViewById(R.id.btn_refresh);
        mRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.discover();
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
        setGradientBackgroundAndColor(findViewById(R.id.btn_send_photo), Integer.parseInt(deviceColor, 16) + 0xFF000000);
        findViewById(R.id.btn_send_photos).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((TextView) findViewById(R.id.status_text)).setText("Sending...");
                mService.sendPhotos(getPhotosData());
            }
        });

        /*
        the WifiDirectService triggers UI updates via callbacks
         */
        serviceCallbacks = new WifiDirectService.WifiDirectServiceCallbacks() {
            @Override
            public void resetPeersUI() {
                if (mActivity == null || !active) return;

                peers.clear();
                mWiFiPeerListAdapter.notifyDataSetChanged();
                mActivity.updateRemoteDevicesTitle();
            }

            @Override
            public void refreshPeersUI(List<WifiP2pDevice> peersList) {
                if (mActivity == null || !active) return;

                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                mRefreshButton.clearAnimation();

                peers.clear();
                // only show the connected devices for Receivers
                if (mService.isConnected() && !mService.initiatedConnection()) {
                    for (WifiP2pDevice d : mService.getPeers()) {
                        if (d.status == WifiP2pDevice.CONNECTED) {
                            peers.add(d);
                        }
                    }
                } else {
                    peers.addAll(peersList);
                }

                mWiFiPeerListAdapter.notifyDataSetChanged();
                mActivity.updateRemoteDevicesTitle();
            }

            @Override
            public void dismissProgress() {
                if (mActivity == null || !active) return;

                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }

            @Override
            public void newConnectionInfoUpdateSenderUI() {
                if (mActivity == null || !active) return;

                findViewById(R.id.btn_send_photo).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.status_text)).setText("Ready to Send Photo");
            }

            @Override
            public void newConnectionInfoUpdateReceiverUI() {
                if (mActivity == null || !active) return;

                findViewById(R.id.btn_send_photo).setVisibility(View.GONE);
                findViewById(R.id.btn_send_photos).setVisibility(View.GONE);
            }

            @Override
            public void resetPhotoReceivedUI() {
                if (mActivity == null || !active) return;

                ((TextView) findViewById(R.id.status_text)).setText("");
                findViewById(R.id.open_received_photo).setVisibility(View.GONE);
                findViewById(R.id.btn_send_photo).setVisibility(View.GONE);
                findViewById(R.id.btn_send_photos).setVisibility(View.GONE);
            }

            @Override
            public void showConnectingDialog(String deviceAddress) {
                if (mActivity == null || !active) return;

                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(mActivity, "Press back to cancel", "Connecting to :" + deviceAddress, true, true);
            }

            @Override
            public void updateRemoteDevicesTitle(String title) {
                if (mActivity == null || !active) return;

                mRemoteDevicesTitle.setText(title);
            }

            @Override
            public void showDiscoverUI() {
                if (mActivity == null || !active) return;

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
            }

            @Override
            public void updateThisDeviceUI(WifiP2pDevice device) {
                if (mActivity == null || !active) return;

                mActivity.updateThisDevice(device);
            }

            @Override
            public void showReadyToReceiveUI() {
                if (mActivity == null || !active) return;

                ((TextView) mStatusBar.findViewById(R.id.status_text)).setText("Ready to Receive Photo");
                mStatusBar.findViewById(R.id.open_received_photo).setVisibility(View.GONE);
            }

            @Override
            public void showPhotoReceivedUI(final String path) {
                if (mActivity == null || !active) return;

                if (!TextUtils.isEmpty(path)) {
                    ImageView imageView = (ImageView) mStatusBar.findViewById(R.id.open_received_photo);
                    imageView.setImageBitmap(BitmapFactory.decodeFile(path));
                    imageView.setVisibility(View.VISIBLE);
                    ((TextView) mStatusBar.findViewById(R.id.status_text)).setText("Received file:\n" + path);
                    mStatusBar.findViewById(R.id.open_received_photo).setVisibility(View.VISIBLE);
                    mStatusBar.findViewById(R.id.open_received_photo).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            viewImageFile(mActivity, path);
                        }
                    });
                }
            }
        };
    }

    @Override
    public void onStart() {
        super.onStart();
        active = true;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Bind to WifiDirectService

        // service is started using the App context
        Context appContext = this.getApplication().getApplicationContext();
        startService(new Intent(appContext, WifiDirectService.class));

        // and bound using the activity context
        bindService(new Intent(this, WifiDirectService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Unbind from WifiDirectService
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        active = false;
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

        ((TextView) findViewById(R.id.status_text)).setText("Sending...");
        mService.sendPhotos(photosData);
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {

            // We've bound to WifiServiceBinder, cast the IBinder and get
            // a WifiDirectService instance to access its public methods
            WifiDirectService.WifiServiceBinder binder = (WifiDirectService.WifiServiceBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.setCallback(serviceCallbacks);

            // get the state of the device and peers from service and update UI accordingly
            if (mService.initiatedConnection()) {

                // connected Sender

                findViewById(R.id.btn_send_photo).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.status_text)).setText("Ready to Send Photo");
                mRemoteDevicesTitle.setText("SEND TO THESE REMOTE DEVICES");

                peers.clear();
                peers.addAll(mService.getPeers());
                mWiFiPeerListAdapter.notifyDataSetChanged();

            } else if (mService.isConnected()) {

                // connected Receiver

                findViewById(R.id.btn_send_photo).setVisibility(View.GONE);
                findViewById(R.id.btn_send_photos).setVisibility(View.GONE);
                ((TextView) mStatusBar.findViewById(R.id.status_text)).setText("Ready to Receive Photo");
                mStatusBar.findViewById(R.id.open_received_photo).setVisibility(View.GONE);
                mRemoteDevicesTitle.setText("RECEIVE FROM THIS REMOTE DEVICE");

                peers.clear();
                //peers.addAll(mService.getPeers());
                // only show the connected devices
                for (WifiP2pDevice d:mService.getPeers()) {
                    if (d.status == WifiP2pDevice.CONNECTED) {
                        peers.add(d);
                    }
                }

                mWiFiPeerListAdapter.notifyDataSetChanged();
            }

            WifiP2pDevice device = mService.getThisDevice();
            if (device != null) {
                updateThisDevice(device);
            }

            // once bound and setup with the callbacks, discover
            mService.discover();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


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

                Drawable background = v.findViewById(R.id.btn_disconnect).getBackground();
                if (background instanceof ShapeDrawable) {
                    GradientDrawable gradientDrawable = (GradientDrawable)background;
                    gradientDrawable.setColor(Integer.parseInt(deviceColor, 16) + 0xFF000000);
                }

                String deviceColor;
                if (device.deviceName.length() > 6) {
                    deviceColor = device.deviceName.substring(0, 6);
                    deviceColor = Utils.transformColor(deviceColor);
                } else {
                    deviceColor = device.deviceName;
                }
                setGradientBackgroundAndColor(v.findViewById(R.id.btn_disconnect), Integer.parseInt(deviceColor, 16) + 0xFF000000);
                setGradientBackgroundAndColor(v.findViewById(R.id.btn_connect), 0xFFaaaaaa);
                v.findViewById(R.id.remote_device_color_indicator).setBackgroundColor(Integer.parseInt(deviceColor, 16) + 0xFF000000);

                TextView top = (TextView) v.findViewById(R.id.device_name);
                TextView bottom = (TextView) v.findViewById(R.id.device_details);
                if (top != null) {
                    top.setText(device.deviceName);
                }
                if (bottom != null) {
                    bottom.setText(Utils.getDeviceStatus(device.status));
                    if (device.status == WifiP2pDevice.CONNECTED) {
                        bottom.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    } else {
                        bottom.setTextColor(getResources().getColor(android.R.color.black));
                    }
                }

                if (mService.initiatedConnection()) {
                    if (device.status == WifiP2pDevice.CONNECTED) {
                        v.findViewById(R.id.btn_connect).setVisibility(View.GONE);
                        v.findViewById(R.id.btn_disconnect).setVisibility(View.VISIBLE);
                        v.findViewById(R.id.btn_disconnect).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                mService.disconnect();
                            }
                        });
                    } else {
                        v.findViewById(R.id.btn_disconnect).setVisibility(View.GONE);
                        v.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
                        v.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                mService.connect(device.deviceAddress);
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
                                    mService.disconnect();
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
                                mService.connect(device.deviceAddress);
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

    private void updateRemoteDevicesTitle() {
        if (mService.initiatedConnection()) {
            mRemoteDevicesTitle.setText("SEND TO THESE REMOTE DEVICES");
        } else {
            if (connected(peers)) {
                mRemoteDevicesTitle.setText("RECEIVE FROM THIS REMOTE DEVICE");
            }
        }
    }

    private void updateThisDevice(WifiP2pDevice device) {
        TextView view = (TextView) findViewById(R.id.my_name);
        if (WifiDirectService.USE_REFLECTION_TO_FILTER_OUT_NON_SUPPORTED_DEVICES) {
            view.setText(device.deviceName.replace("com.cc.wifidirectdemo", ""));
        } else {
            view.setText(device.deviceName);
        }
        view = (TextView) findViewById(R.id.my_status);
        view.setText(Utils.getDeviceStatus(device.status));
        if (device.status == WifiP2pDevice.CONNECTED) {
            view.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            view.setTextColor(getResources().getColor(android.R.color.black));
        }
    }

    private static void viewImageFile(Context context, String absolutePath) {
        Intent intent = new Intent();
        intent.setAction(android.content.Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("file://" + absolutePath), "image/*");
        context.startActivity(intent);
    }

    private void setDeviceColor() {
        Log.e("toto", "device color: " + deviceColor);
        mThisDeviceTitle.setTextColor(Integer.parseInt(deviceColor, 16)+0xFF000000);
        mThisDeviceUnderline.setBackgroundColor(Integer.parseInt(deviceColor, 16)+0xFF000000);
        mRemoteDevicesTitle.setTextColor(Integer.parseInt(deviceColor, 16) + 0xFF000000);
        mRemoteDevicesUnderline.setBackgroundColor(Integer.parseInt(deviceColor, 16) + 0xFF000000);
        mStatusBar.setBackgroundColor(Integer.parseInt(deviceColor, 16) + 0xFF000000);
    }

    private void setGradientBackgroundAndColor(View v, int color) {
        int bottomColor = color;
        int topColor = color;
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] {bottomColor, topColor});
        gradient.setShape(GradientDrawable.RECTANGLE);
        gradient.setCornerRadius(40.f);
        v.setBackgroundDrawable(gradient);
    }

}
