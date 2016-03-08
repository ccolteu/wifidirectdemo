# wifidirectdemo

This demo allows to send photos from one device (SENDER) to multiple devices (RECEIVERS) via Wifi Direct (i.e. no Wifi/LTE/4G connectivity needed).


Architecture considerations:

StartActivity -> WifiDirectActivity <-> WifiDirectService

1. StartActivity - dummy activity used to BACK out from WifiDirectActivity to illustrate that the connections are maintained beyond the activity hosting the UI
2. WifiDirectActivity - hosts the connections UI and user controls (connect, send)
3. WifiDirectService - maintains connections and their state

The connections status and the connections themselves are maintained in a WifiDirectService so that they are not tight to the WifiDirectActivity initiating the service. 
This WifiDirectService is bound so that we can access its public methods from the WifiDirectActivity binding to it, such as UI clicks that refresh the peers list. Also this WifiDirectService is started (startService is called) so that it will not be destroyed when the WifiDirectActivity is destroyed (unbindService is called).
You can initiate a connection, press BACK to destroy WifiDirectActivity, launch it again (from StartActivity) and it will bind to the WifiDirectService and update the UI with the connections status.

The SENDER is the Group Owner (requested via config.groupOwnerIntent = 15). It will create a forever blocking thread awaiting to receive RECEIVERS IPs. The RECEIVER will first send its IP to the SENDER and then will create a forever blocking thread awaiting photos from the SENDER.


Instructions:

1. To initiate sending photos turn ON the toggle for one remote device. At that point your device becomes the SENDER. You can continue turning ON toggels for other remote devices to send to multiple devices. 
2. On the remote devices accept the connection thus turning them into RECEIVERS. 
2. Click SEND PHOTO on the SENDER to send a photos to all RECEIVERS. 
3. To stop receiving photos on the RECEIVER device turn toggle OFF.
4. To stop sending photos to all RECEIVERS turn toggle OFF on the SENDER.

Note that the photo filename is sent along as well so that it is preserved on the receiving device (see RECEIVER screenshot below). As a matter of fact 1kB worth of metadata (currently just the file name) is pre-pended to the photo byte array stream when sent and parsed on the receiving end.


Sending to 2 devices:

![Alt text](/sender_receivers.png?raw=true "")


