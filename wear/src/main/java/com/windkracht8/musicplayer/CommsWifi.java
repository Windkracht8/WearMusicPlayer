package com.windkracht8.musicplayer;

import android.os.Handler;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Socket;

public class CommsWifi{
    public static void receiveFile(Handler handler_message, String path, long length, String ip, int port){
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
                        handler_message.sendMessage(handler_message.obtainMessage(Main.MESSAGE_COMMS_FILE_ERROR));
                        return;
                    }
                    fileOutputStream.write(buffer, 0, numBytes);
                    bytesDone += numBytes;

                    long progress = (bytesDone * 100) / length;
                    handler_message.sendMessage(handler_message.obtainMessage(Main.MESSAGE_COMMS_FILE_PROGRESS, (int) progress));
                    if(bytesDone >= length){
                        handler_message.sendMessage(handler_message.obtainMessage(Main.MESSAGE_COMMS_FILE_DONE, path));
                        return;
                    }
                }
                handler_message.sendMessage(handler_message.obtainMessage(Main.MESSAGE_COMMS_FILE_ERROR));
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsWifi.receiveFile FileOutputStream exception: " + e.getMessage());
                handler_message.sendMessage(handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_create_file));
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsWifi.receiveFile Socket exception: " + e.getMessage());
            handler_message.sendMessage(handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_wifi));
        }
    }
    private static void sleep100(){
        try{
            Thread.sleep(100);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsConnected.sleep100 exception: " + e.getMessage());
        }
    }
}
