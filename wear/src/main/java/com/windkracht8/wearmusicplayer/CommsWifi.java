package com.windkracht8.wearmusicplayer;

import android.util.Log;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Socket;

class CommsWifi{
    static void receiveFile(Main main, String path, long length, String ip, int port){
        long bytesDone = 0;
        Log.d(Main.LOG_TAG, "CommsWifi.receiveFile path: " + path);
        Log.d(Main.LOG_TAG, "CommsWifi.receiveFile length: " + length);
        Log.d(Main.LOG_TAG, "CommsWifi.receiveFile ip: " + ip);
        Log.d(Main.LOG_TAG, "CommsWifi.receiveFile port: " + port);
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
                        main.commsFileFailed(path);
                        return;
                    }
                    fileOutputStream.write(buffer, 0, numBytes);
                    bytesDone += numBytes;

                    long progress = (bytesDone * 100) / length;
                    main.commsProgress((int)progress);
                    if(bytesDone >= length){
                        main.commsFileDone(path);
                        return;
                    }
                }
                main.commsFileFailed(path);
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsWifi.receiveFile FileOutputStream exception: " + e.getMessage());
                main.toast(R.string.fail_create_file);
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsWifi.receiveFile Socket exception: " + e);
            Log.e(Main.LOG_TAG, "CommsWifi.receiveFile Socket exception: " + e.getMessage());
            main.toast(R.string.fail_wifi);
        }
        //if we get here it failed
        main.commsFileFailed(path);
    }
    private static void sleep100(){
        try{
            Thread.sleep(100);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsWifi.sleep100 exception: " + e.getMessage());
        }
    }
}
