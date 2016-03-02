# wifidirectdemo

This demo allows to send photos from one device (SENDER) to multiple devices (RECEIVERS) via Wifi Direct (i.e. no Wifi/LTE/4G connectivity needed).

1. To initiate sending photos turn ON the toggle for one remote device. At that point your device becomes the SENDER. You can continue turning ON toggels for other remote devices to send to multiple devices. 
2. On the remote devices accept the connection thus turning them into RECEIVERS. 
2. Click SEND PHOTO on the SENDER to send a photos to all RECEIVERS. 
3. To stop receiving photos on the RECEIVER device turn toggle OFF.
4. To stop sending photos to all RECEIVERS turn toggle OFF on the SENDER.

Note that the photo filename is sent along as well so that it is preserved on the receiving device (see RECEIVER screenshot below). As a matter of fact 1kB worth of metadata (currently just the file name) is pre-pended to the photo byte array stream when sent and parsed on the receiving end.

SENDER:

![Alt text](/sender.png?raw=true "initiates connection to send")

RECEIVER:

![Alt text](/receiver.png?raw=true "accepts connection to receive")
