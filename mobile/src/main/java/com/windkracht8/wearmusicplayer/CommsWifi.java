package com.windkracht8.wearmusicplayer;

import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.util.Log;

import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class CommsWifi{
    static final int PORT_NUMBER = 9002;
    private final Main main;
    private final ScheduledExecutorService executor;
    private final ArrayList<Item> itemQueue = new ArrayList<>();
    boolean running = false;
    boolean canSendNext = true;
    private boolean disconnect = true;
    private boolean isSendingFile = false;

    CommsWifi(Main main){
        this.main = main;
        executor = Executors.newSingleThreadScheduledExecutor();
    }
    void stop(){
        disconnect = true;
        running = false;
        isSendingFile = false;
        itemQueue.clear();
    }
    private String getIpAddress(){
        ConnectivityManager connectivityManager = (ConnectivityManager)main.getSystemService(Main.CONNECTIVITY_SERVICE);
        List<LinkAddress> linkAddresses = Objects.requireNonNull(connectivityManager.getLinkProperties(connectivityManager.getActiveNetwork())).getLinkAddresses();
        for(LinkAddress linkAddress : linkAddresses){
            if(linkAddress.getAddress() instanceof Inet4Address){
                return linkAddress.getAddress().getHostAddress();
            }
        }
        return "0.0.0.0";
    }
    void queueFile(Item item){
        Log.d(Main.LOG_TAG, "CommsWifi.queueFile " + item.libItem.name);
        if(itemQueue.contains(item)) return;//don't add it again
        disconnect = false;
        item.newStatus(main, Library.LibItem.Status.UNKNOWN);
        item.updateProgress(main, 0);
        itemQueue.add(item);
        if(!running){
            running = true;
            sendFile();
        }
    }
    private void sendFile(){
        if(itemQueue.isEmpty()){
            running = false;
            return;
        }
        if(isSendingFile || !canSendNext){
            executor.schedule(this::sendFile, 100, TimeUnit.MILLISECONDS);
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
                while(!disconnect && fileInputStream.available() > 0){
                    byte[] buffer = new byte[2048];
                    int numBytes = fileInputStream.read(buffer);
                    if(numBytes < 0){
                        Log.e(Main.LOG_TAG, "CommsWifi.sendFile read error");
                        item.clearStatus();
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
        executor.schedule(this::sendFile, 100, TimeUnit.MILLISECONDS);
    }
}
