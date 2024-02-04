package com.windkracht8.wearmusicplayer;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;

public class CommsWifi{
    static final int PORT_NUMBER = 9002;
    static boolean sendingFile = false;
    private Socket socket;

    public CommsWifi(){}
    void stopSendFile(){
        sendingFile = false;
        try{
            socket.close();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsWifi.stopSendFile Exception: " + e.getMessage());
        }
    }
    String getIpAddress(Main main){
        WifiManager wifiManager = (WifiManager) main.getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        return String.format(Locale.US, "%d.%d.%d.%d", (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
    }
    void sendFile(Main main, Item item){
        Library.LibItem libItem = item.libItem;
        sendingFile = true;
        try(ServerSocket serverSocket = new ServerSocket(PORT_NUMBER)){
            while(sendingFile){
                socket = serverSocket.accept();
                try(FileInputStream fileInputStream = new FileInputStream(libItem.uri.getPath())){
                    long bytesDone = 0;
                    OutputStream outputStream = socket.getOutputStream();
                    while(fileInputStream.available() > 0){
                        byte[] buffer = new byte[2048];
                        int numBytes = fileInputStream.read(buffer);
                        if(numBytes < 0){
                            Log.e(Main.LOG_TAG, "CommsWifi.sendFile read error");
                            return;
                        }
                        outputStream.write(buffer, 0, numBytes);
                        bytesDone += numBytes;
                        libItem.progress = bytesDone;
                        item.updateProgress(main);
                    }
                    sendingFile = false;
                }catch(Exception e){
                    Log.e(Main.LOG_TAG, "CommsWifi.sendFile FileInputStream exception: " + e.getMessage());
                }
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsWifi.sendFile ServerSocket exception: " + e.getMessage());
        }
    }
}
