package com.windkracht8.wearmusicplayer;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Socket;

class CommsWifi{
    private static ConnectivityManager connectivityManager;
    static boolean isReceiving = false;
    static void receiveFile(Main main, String path, long length, String ip, int port){
        Log.d(Main.LOG_TAG, "CommsWifi path: " + path);
        Log.d(Main.LOG_TAG, "CommsWifi length: " + length);
        Log.d(Main.LOG_TAG, "CommsWifi ip: " + ip);
        Log.d(Main.LOG_TAG, "CommsWifi port: " + port);

        if(connectivityManager == null){
            connectivityManager = (ConnectivityManager)main.getSystemService(Main.CONNECTIVITY_SERVICE);
        }
        try{
            connectivityManager.requestNetwork(
                    new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .build()
                    , new ConnectivityManager.NetworkCallback() {
                        public void onAvailable(@NonNull Network network){
                            super.onAvailable(network);
                            Log.d(Main.LOG_TAG, "CommsWifi.NetworkCallback.onAvailable");
                            connectivityManager.bindProcessToNetwork(network);
                            main.commsConnectionInfo(R.string.connection_info_Wifi);
                            readFileFromStream(main, path, length, ip, port);
                        }
                        public void onUnavailable(){
                            super.onUnavailable();
                            Log.d(Main.LOG_TAG, "CommsWifi.NetworkCallback.onUnavailable");
                            main.toast(R.string.fail_wifi_fast);
                            readFileFromStream(main, path, length, ip, port);
                        }
                    }
                    , 2000
            );
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsWifi " + e.getMessage());
            main.toast(R.string.fail_wifi_fast);
        }
    }
    private static void readFileFromStream(Main main, String path, long length, String ip, int port){
        Log.d(Main.LOG_TAG, "CommsWifi.readFileFromStream");
        isReceiving = true;
        long bytesDone = 0;
        try(Socket socket = new Socket(ip, port)){
            InputStream inputStream = socket.getInputStream();
            try(FileOutputStream fileOutputStream = new FileOutputStream(Library.exStorageDir + "/" + path)){
                int counter = 0;
                while(counter < 100){
                    if(inputStream.available() == 0){
                        counter++;
                        sleep100();
                        continue;
                    }
                    counter = 0;
                    byte[] buffer = new byte[2048];
                    int numBytes = inputStream.read(buffer);
                    if(numBytes < 0){
                        Log.e(Main.LOG_TAG, "CommsWifi.receiveFile read error");
                        main.toast(R.string.fail_read_wifi);
                        main.commsFileFailed(path, R.string.fail_read_wifi);
                        connectivityManager.bindProcessToNetwork(null);
                        isReceiving = false;
                        return;
                    }
                    fileOutputStream.write(buffer, 0, numBytes);
                    bytesDone += numBytes;

                    long progress = (bytesDone * 100) / length;
                    main.commsProgress((int)progress);
                    if(bytesDone >= length){
                        main.commsFileDone(path);
                        connectivityManager.bindProcessToNetwork(null);
                        isReceiving = false;
                        return;
                    }
                }
                main.toast(R.string.fail_read_wifi);
                main.commsFileFailed(path, R.string.fail_read_wifi);
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsWifi.receiveFile FileOutputStream exception: " + e.getMessage());
                main.toast(R.string.fail_write_file);
                main.commsFileFailed(path, R.string.fail_write_file);
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsWifi.receiveFile Socket exception: " + e);
            Log.e(Main.LOG_TAG, "CommsWifi.receiveFile Socket exception: " + e.getMessage());
            main.toast(R.string.fail_wifi);
            main.commsFileFailed(path, R.string.fail_wifi);
        }
        //if we get here it failed
        connectivityManager.bindProcessToNetwork(null);
        isReceiving = false;
    }
    private static void sleep100(){
        try{
            Thread.sleep(100);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsWifi.sleep100 exception: " + e.getMessage());
        }
    }
}
