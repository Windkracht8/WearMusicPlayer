package com.windkracht8.wearmusicplayer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.Objects;

class CommsWifi{
    static final int PORT_NUMBER = 9002;
    static boolean isSendingFile = false;
    private static Socket socket;

    static void stopSendFile(){
        isSendingFile = false;
        try{
            if(socket != null) socket.close();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsWifi.stopSendFile Exception: " + e.getMessage());
        }
        socket = null;
    }
    static String getIpAddress(Main main){
        int ipAddress = 0;
        try{
            if(Build.VERSION.SDK_INT >= 29){
                ConnectivityManager connectivityManager = (ConnectivityManager)main.getSystemService(Main.CONNECTIVITY_SERVICE);
                ipAddress = ((WifiInfo)Objects.requireNonNull((Objects.requireNonNull(connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork()))).getTransportInfo())).getIpAddress();
            }else{
                WifiManager wifiManager = (WifiManager) main.getSystemService(Context.WIFI_SERVICE);
                ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsWifi.getIpAddress: " + e.getMessage());
        }
        return String.format(Locale.US, "%d.%d.%d.%d", (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
    }
    static void sendFile(Main main, Item item){
        Library.LibItem libItem = item.libItem;
        isSendingFile = true;
        try(ServerSocket serverSocket = new ServerSocket(PORT_NUMBER)){
            while(isSendingFile){
                socket = serverSocket.accept();
                try(FileInputStream fileInputStream = new FileInputStream(libItem.uri.getPath())){
                    long bytesDone = 0;
                    OutputStream outputStream = socket.getOutputStream();
                    while(fileInputStream.available() > 0){
                        byte[] buffer = new byte[2048];
                        int numBytes = fileInputStream.read(buffer);
                        if(numBytes < 0){
                            Log.e(Main.LOG_TAG, "CommsWifi.sendFile read error");
                            item.clearStatus();
                            stopSendFile();
                            return;
                        }
                        outputStream.write(buffer, 0, numBytes);
                        bytesDone += numBytes;
                        libItem.progress = bytesDone;
                        item.updateProgress(main);
                    }
                }catch(Exception e){
                    Log.e(Main.LOG_TAG, "CommsWifi.sendFile FileInputStream exception: " + e.getMessage());
                }
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsWifi.sendFile ServerSocket exception: " + e.getMessage());
        }
        stopSendFile();
    }

/*
    private CommsP2PWifi commsP2PWifi;
    void startP2PWifi(Main main){
        commsP2PWifi = new CommsP2PWifi(main);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        main.registerReceiver(commsP2PWifi, intentFilter);
        commsP2PWifi.start();
    }
    void stopP2PWifi(Main main){
        main.unregisterReceiver(commsP2PWifi);
        commsP2PWifi = null;
    }

    private class CommsP2PWifi extends BroadcastReceiver {
        private WifiP2pManager wifiP2pManager;
        private WifiP2pManager.Channel channel;

        private CommsP2PWifi(Main main){
            wifiP2pManager = (WifiP2pManager) main.getSystemService(Context.WIFI_P2P_SERVICE);
            channel = wifiP2pManager.initialize(main, main.getMainLooper(), null);
        }
        private void start(){
            wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener(){
                @Override
                public void onSuccess(){
                    Log.d(Main.LOG_TAG, "CommsP2PWifi.start.discoverPeers.onSuccess");
                }
                @Override
                public void onFailure(int reasonCode){
                    Log.d(Main.LOG_TAG, "CommsP2PWifi.start.discoverPeers.onFailure " + reasonCode);
                }
            });

        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)){
                Log.d(Main.LOG_TAG, "CommsWifi.onReceive WIFI_P2P_STATE_CHANGED_ACTION");
                // Check to see if Wi-Fi is enabled and notify appropriate activity
            }else if(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)){
                Log.d(Main.LOG_TAG, "CommsWifi.onReceive WIFI_P2P_PEERS_CHANGED_ACTION");
                // Call WifiP2pManager.requestPeers() to get a list of current peers
            }else if(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)){
                Log.d(Main.LOG_TAG, "CommsWifi.onReceive WIFI_P2P_CONNECTION_CHANGED_ACTION");
                // Respond to new connection or disconnections
            }else if(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)){
                Log.d(Main.LOG_TAG, "CommsWifi.onReceive WIFI_P2P_THIS_DEVICE_CHANGED_ACTION");
                // Respond to this device's wifi state changing
            }
        }
    }
*/
}
