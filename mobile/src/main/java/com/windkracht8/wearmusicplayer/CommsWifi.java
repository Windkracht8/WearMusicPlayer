package com.windkracht8.wearmusicplayer;

import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

class CommsWifi{
    static final int PORT_NUMBER = 9002;
    private final Main main;
    private final Handler handler;
    private final ArrayList<Item> itemQueue = new ArrayList<>();
    boolean running = false;
    boolean canSendNext = true;
    private boolean closeConnection = true;
    private boolean isSendingFile = false;

    CommsWifi(Main main){
        this.main = main;
        handler = new Handler(Looper.getMainLooper());
    }
    void stop(){
        closeConnection = true;
        running = false;
        itemQueue.clear();
        handler.removeCallbacksAndMessages(null);
    }
    private String getIpAddress(){
        ConnectivityManager connectivityManager = (ConnectivityManager)main.getSystemService(Main.CONNECTIVITY_SERVICE);
        List<LinkAddress> linkAddresses = Objects.requireNonNull(connectivityManager.getLinkProperties(connectivityManager.getActiveNetwork())).getLinkAddresses();
        for(LinkAddress linkAddress : linkAddresses){
            byte[] address = linkAddress.getAddress().getAddress();
            if(address.length == 4){
                return String.format(Locale.US, "%d.%d.%d.%d",
                        address[0], address[1], address[2], address[3]);
            }
        }
        return "0.0.0.0";
    }
    void queueFile(Item item){
        Log.d(Main.LOG_TAG, "CommsWifi.queueFile " + item.libItem.name);
        running = true;
        closeConnection = false;
        item.updateProgress(main, 0);
        itemQueue.add(item);
        Main.executorService.submit(this::sendFile);
    }
    private void sendFile(){
        if(itemQueue.isEmpty()){
            running = false;
            return;
        }
        if(isSendingFile || !canSendNext){
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(()-> Main.executorService.submit(this::sendFile), 100);
            return;
        }
        Item item = itemQueue.get(0);
        itemQueue.remove(0);
        Library.LibItem libItem = item.libItem;

        String ipAddress = getIpAddress();
        if(ipAddress.equals("0.0.0.0")){
            main.onBTError(R.string.no_wifi);
            return;
        }
        Log.d(Main.LOG_TAG, "CommsWifi.sendFile " + libItem.name);

        isSendingFile = true;
        canSendNext = false;
        main.sendFileDetailsRequest(item.libItem, ipAddress);
        main.itemInProgress = item;

        try(ServerSocket serverSocket = new ServerSocket(PORT_NUMBER)){
            Socket socket = serverSocket.accept();
            try(FileInputStream fileInputStream = new FileInputStream(libItem.uri.getPath())){
                long bytesDone = 0;
                OutputStream outputStream = socket.getOutputStream();
                while(!closeConnection && fileInputStream.available() > 0){
                    byte[] buffer = new byte[2048];
                    int numBytes = fileInputStream.read(buffer);
                    if(numBytes < 0){
                        Log.e(Main.LOG_TAG, "CommsWifi.sendFile read error");
                        item.clearStatus();
                        main.libraryNewStatuses();
                        socket.close();
                        isSendingFile = false;
                        stop();
                        return;
                    }
                    outputStream.write(buffer, 0, numBytes);
                    bytesDone += numBytes;
                    item.updateProgress(main, bytesDone);
                }
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsWifi.sendFile FileInputStream exception: " + e);
                Log.e(Main.LOG_TAG, "CommsWifi.sendFile FileInputStream exception: " + e.getMessage());
            }
            try{
                socket.close();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsWifi.sendFile socket.close exception: " + e.getMessage());
            }
            try{
                serverSocket.close();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsWifi.sendFile serverSocket.close exception: " + e.getMessage());
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsWifi.sendFile ServerSocket exception: " + e);
            Log.e(Main.LOG_TAG, "CommsWifi.sendFile ServerSocket exception: " + e.getMessage());
            stop();
        }
        isSendingFile = false;
        handler.postDelayed(()-> Main.executorService.submit(this::sendFile), 100);
    }
}
