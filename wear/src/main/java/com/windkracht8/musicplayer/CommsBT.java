package com.windkracht8.musicplayer;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class CommsBT{
    final UUID MP_UUID = UUID.fromString("6f34da3f-188a-4c8c-989c-2baacf8ea6e1");
    final BluetoothAdapter bluetoothAdapter;
    BluetoothServerSocket bluetoothServerSocket;
    BluetoothSocket bluetoothSocket;
    final Main main;
    final Handler handler;

    boolean listen = false;
    final JSONArray responseQueue = new JSONArray();

    public CommsBT(Main main){
        this.main = main;
        handler = new Handler(Looper.getMainLooper());
        BluetoothManager bluetoothManager = (BluetoothManager) main.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if(bluetoothAdapter == null) return;
        main.registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    private final BroadcastReceiver btStateReceiver = new BroadcastReceiver(){
        public void onReceive(Context context, Intent intent){
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){
                int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if(btState == BluetoothAdapter.STATE_TURNING_OFF){
                    stopListening();
                }else if(btState == BluetoothAdapter.STATE_ON){
                    startListening();
                }
            }
        }
    };

    public void startListening(){
        if(bluetoothAdapter == null || !bluetoothAdapter.isEnabled()){
            return;
        }
        Log.d(Main.LOG_TAG, "CommsBT.startListening");
        CommsBTConnect commsBTConnect = new CommsBTConnect();
        commsBTConnect.start();
    }

    public void stopListening(){
        listen = false;
        Log.d(Main.LOG_TAG, "CommsBT.stopListening");
        try{
            main.unregisterReceiver(btStateReceiver);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.stopListening unregisterReceiver: " + e.getMessage());
        }
        try{
            if(bluetoothSocket != null) bluetoothSocket.close();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.stopListening bluetoothSocket: " + e.getMessage());
        }
        try{
            if(bluetoothServerSocket != null) bluetoothServerSocket.close();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.stopListening bluetoothServerSocket: " + e.getMessage());
        }
    }

    private class CommsBTConnect extends Thread{
        @SuppressLint("MissingPermission")//already checked in initBT
        public CommsBTConnect(){
            if(bluetoothServerSocket != null) return;
            try{
                bluetoothServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("MusicPlayer", MP_UUID);
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnect Exception: " + e.getMessage());
            }
        }

        public void run(){
            try{
                bluetoothSocket = bluetoothServerSocket.accept();
                CommsBTConnected commsBTConnected = new CommsBTConnected();
                commsBTConnected.start();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnect.run Exception: " + e.getMessage());
            }
        }
    }

    private class CommsBTConnected extends Thread{
        private InputStream inputStream;
        private OutputStream outputStream;

        public CommsBTConnected(){
            try{
                inputStream = bluetoothSocket.getInputStream();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected getInputStream Exception: " + e.getMessage());
            }
            try{
                outputStream = bluetoothSocket.getOutputStream();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected getOutputStream Exception: " + e.getMessage());
            }
        }

        public void run(){
            Log.d(Main.LOG_TAG, "CommsBTConnected.run");
            listen = true;
            process();
        }

        private void process(){
            if(!listen){
                close();
                return;
            }
            if(!sendNextResponse()){
                close();
                startListening();
                return;
            }
            read();
            handler.postDelayed(this::process, 100);
        }

        private void close(){
            try{
                Log.d(Main.LOG_TAG, "CommsBTConnected.close");
                bluetoothSocket.close();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected.close exception: " + e.getMessage());
            }
        }

        private void sleep100(){
            try{
                Thread.sleep(100);
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected.sleep100 exception: " + e.getMessage());
            }
        }

        private boolean sendNextResponse(){
            try{
                outputStream.write("".getBytes());
                if(responseQueue.length() < 1) return true;
                JSONObject response = (JSONObject) responseQueue.get(0);
                responseQueue.remove(0);
                Log.d(Main.LOG_TAG, "CommsBTConnected.sendNextResponse: " + response.toString());
                outputStream.write(response.toString().getBytes());
            }catch(Exception e){
                if(e.getMessage() != null && e.getMessage().contains("Broken pipe")){
                    return false;
                }
                Log.e(Main.LOG_TAG, "CommsBTConnected.sendNextResponse Exception: " + e.getMessage());
                main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_respond));
            }
            return true;
        }
        private void read(){
            try{
                if(inputStream.available() == 0) return;
                String request = "";

                while(inputStream.available() > 0){
                    byte[] buffer = new byte[inputStream.available()];
                    int numBytes = inputStream.read(buffer);
                    if(numBytes < 0){
                        Log.e(Main.LOG_TAG, "CommsBTConnected.read read error, request: " + request);
                        main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_request));
                        return;
                    }
                    String temp = new String(buffer);
                    request += temp;
                    if(isValidJSON(request)){
                        JSONObject requestMessage = new JSONObject(request);
                        gotRequest(requestMessage);
                        return;
                    }
                    if(inputStream.available() == 0) sleep100();
                }
                Log.e(Main.LOG_TAG, "CommsBTConnected.read no more bytes to read, no valid json, request: " + request);
                main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_request));
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected.read Exception: " + e.getMessage());
                main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_request));
            }
        }
        private void gotRequest(final JSONObject requestMessage){
            Log.d(Main.LOG_TAG, "CommsBTConnected.gotRequest: " + requestMessage);
            try{
                String requestType = requestMessage.getString("requestType");
                switch(requestType){
                    case "sync":
                        onReceiveSync();
                        break;
                    case "fileDetails":
                        JSONObject requestData = requestMessage.getJSONObject("requestData");
                        String path = requestData.getString("path");
                        if(!Main.library.ensurePath(main.handler_message, path)){
                            Log.e(Main.LOG_TAG, "CommsBTConnected.gotRequest: failed to create directory");
                            sendResponse("fileDetails", "failed to create directory");
                            return;
                        }
                        long length = requestData.getLong("length");
                        String ip = requestData.getString("ip");
                        int port = requestData.getInt("port");

                        main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_COMMS_FILE_START, path));
                        main.executorService.submit(() -> CommsWifi.receiveFile(main, path, length, ip, port));
                        break;
                    case "deleteFile":
                        String delPath = requestMessage.getString("requestData");
                        sendResponse("deleteFile", Main.library.deleteFile(main, delPath));
                        break;
                    default:
                        Log.e(Main.LOG_TAG, "CommsBTConnected.gotRequest Unknown requestType: " + requestType);
                }

            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected.gotRequest Exception: " + e.getMessage());
                main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_request));
            }
        }
    }

    private void onReceiveSync(){
        try{
            JSONArray tracks = Main.library.getTracks(main.handler_message);
            long freeSpace = new File(Library.exStorageDir).getFreeSpace();
            JSONObject responseData = new JSONObject();
            responseData.put("tracks", tracks);
            responseData.put("freeSpace", freeSpace);
            sendResponse("sync", responseData);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.onReceiveSync Exception: " + e.getMessage());
            sendResponse("sync", "unexpected error");
        }
    }

    public void sendResponse(final String requestType, final String responseData){
        try{
            JSONObject response = new JSONObject();
            response.put("requestType", requestType);
            response.put("responseData", responseData);
            Log.d(Main.LOG_TAG, "CommsBT.sendResponse: " + response);
            responseQueue.put(response);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.sendResponse String Exception: " + e.getMessage());
            main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_respond));
        }
    }
    public void sendResponse(final String requestType, final JSONObject responseData){
        try{
            JSONObject response = new JSONObject();
            response.put("requestType", requestType);
            response.put("responseData", responseData);
            responseQueue.put(response);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.sendResponse JSONObject Exception: " + e.getMessage());
            main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_respond));
        }
    }

    private static boolean isValidJSON(String json){
        try{
            new JSONObject(json);
        }catch(JSONException e){
            return false;
        }
        return true;
    }
}