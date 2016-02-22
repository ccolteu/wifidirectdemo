# wifidirectdemo

The device that initiates the connection will be the sender, while the device that accepts the connection will be the receiver.

1. press DISCOVER on both devices
2. on one device - the sender - press CONNECT in a row of remote devices - which will become the receiver 
3. on receiver device accept connection
4. on sender device press SEND PHOTO, pick photo using your favorite photo app
5. the receiver device will receive the photo
6. press DISCONNECT on any device

Note that the photo filename is sent along as well so that it is preserved on the receiving device (see Receiver screenshot below). As a matter of fact 1kB worth of metadata (currently just the file name) is pre-pended to the photo byte array stream when sent and parsed on the receiving end.

Sender (initiates the connection):

![Alt text](/sender.png?raw=true "initiates connection to send")

Receiver (accepts the connection):

![Alt text](/receiver.png?raw=true "accepts connection to receive")
