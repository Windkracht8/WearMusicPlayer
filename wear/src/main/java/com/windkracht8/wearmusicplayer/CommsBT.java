package com.windkracht8.wearmusicplayer;

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
import java.util.Date;
import java.util.UUID;

class CommsBT{
    private final UUID WMP_UUID = UUID.fromString("6f34da3f-188a-4c8c-989c-2baacf8ea6e1");
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket bluetoothServerSocket;
    private BluetoothSocket bluetoothSocket;
    private CommsBTConnect commsBTConnect;
    private CommsBTConnected commsBTConnected;
    private final Main main;
    private final Handler handler;

    private boolean closeConnection;
    private final JSONArray responseQueue = new JSONArray();

    CommsBT(Main main){
        this.main = main;
        handler = new Handler(Looper.getMainLooper());
    }

    private void gotRequest(String request){
        Log.d(Main.LOG_TAG, "CommsBT.gotRequest: " + request);
        try{
            JSONObject requestMessage = new JSONObject(request);
            String requestType = requestMessage.getString("requestType");
            switch(requestType){
                case "sync":
                    onReceiveSync();
                    break;
                case "fileDetails":
                    JSONObject requestData = requestMessage.getJSONObject("requestData");
                    String path = requestData.getString("path");
                    if(!Main.library.ensurePath(main, path)){
                        Log.e(Main.LOG_TAG, "CommsBT.gotRequest: failed to create directory");
                        sendResponse("fileDetails", "failed to create directory");
                        return;
                    }
                    long length = requestData.getLong("length");
                    String ip = requestData.getString("ip");
                    int port = requestData.getInt("port");

                    main.commsFileStart(path);
                    main.executorService.submit(() -> CommsWifi.receiveFile(main, path, length, ip, port));
                    break;
                case "deleteFile":
                    String delPath = requestMessage.getString("requestData");
                    sendResponse("deleteFile", Main.library.deleteFile(main, delPath));
                    break;
                default:
                    Log.e(Main.LOG_TAG, "CommsBT.gotRequest Unknown requestType: " + requestType);
            }

        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.gotRequest Exception: " + e.getMessage());
            main.toast(R.string.fail_request);
        }
    }
    private void onReceiveSync(){
        try{
            JSONArray tracks = Main.library.getTracks(main);
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
    void sendResponse(String requestType, String responseData){
        try{
            JSONObject response = new JSONObject();
            response.put("requestType", requestType);
            response.put("responseData", responseData);
            responseQueue.put(response);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.sendResponse String Exception: " + e.getMessage());
            main.toast(R.string.fail_respond);
        }
    }
    void sendFileBinaryResponse(String path){
        try{
            long freeSpace = new File(Library.exStorageDir).getFreeSpace();
            JSONObject responseData = new JSONObject();
            responseData.put("path", path);
            responseData.put("freeSpace", freeSpace);
            sendResponse("fileBinary", responseData);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.sendFileBinaryResponse String Exception: " + e.getMessage());
            main.toast(R.string.fail_respond);
        }
    }
    private void sendResponse(String requestType, JSONObject responseData){
        try{
            JSONObject response = new JSONObject();
            response.put("requestType", requestType);
            response.put("responseData", responseData);
            responseQueue.put(response);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.sendResponse JSONObject Exception: " + e.getMessage());
            main.toast(R.string.fail_respond);
        }
    }

    private final BroadcastReceiver btStateReceiver = new BroadcastReceiver(){
        public void onReceive(Context context, Intent intent){
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){
                int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if(btState == BluetoothAdapter.STATE_TURNING_OFF){
                    Log.d(Main.LOG_TAG, "CommsBT.btStateReceiver: stop");
                    stopComms();
                }else if(btState == BluetoothAdapter.STATE_ON){
                    Log.d(Main.LOG_TAG, "CommsBT.btStateReceiver: start");
                    startComms();
                }
            }
        }
    };
    void startComms(){
        closeConnection = false;
        BluetoothManager bluetoothManager = (BluetoothManager) main.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if(bluetoothAdapter == null || !bluetoothAdapter.isEnabled()){
            Log.d(Main.LOG_TAG, "CommsBT.startComms bluetooth disabled");
            return;
        }
        Log.d(Main.LOG_TAG, "CommsBT.startComms");
        main.registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        commsBTConnect = new CommsBTConnect();
        commsBTConnect.start();
    }
    void stopComms(){
        Log.d(Main.LOG_TAG, "CommsBT.stopComms");
        closeConnection = true;
        try{
            main.unregisterReceiver(btStateReceiver);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.stopComms unregisterReceiver: " + e.getMessage());
        }
        try{
            if(bluetoothServerSocket != null) bluetoothServerSocket.close();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.stopComms bluetoothServerSocket: " + e.getMessage());
        }
        try{
            if(bluetoothSocket != null) bluetoothSocket.close();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.stopComms bluetoothSocket: " + e.getMessage());
        }
        commsBTConnect = null;
        commsBTConnected = null;
    }

    private class CommsBTConnect extends Thread{
        @SuppressLint("MissingPermission")//Permissions are handled in initBT
        private CommsBTConnect(){
            try{
                Log.d(Main.LOG_TAG, "CommsBTConnect");
                bluetoothServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("WearMusicPlayer", WMP_UUID);
            }catch(Exception e){
                if(closeConnection) return;
                Log.e(Main.LOG_TAG, "CommsBTConnect Exception: " + e.getMessage());
            }
        }
        public void run(){
            try{
                bluetoothSocket = bluetoothServerSocket.accept();
                if(closeConnection) return;
                Log.d(Main.LOG_TAG, "CommsBTConnect.run accepted");
                commsBTConnected = new CommsBTConnected();
                commsBTConnected.start();
            }catch(Exception e){
                Log.d(Main.LOG_TAG, "CommsBTConnect.run Exception closeConnection:" + closeConnection);
                if(closeConnection) return;
                Log.e(Main.LOG_TAG, "CommsBTConnect.run Exception: " + e.getMessage());
            }
            Log.d(Main.LOG_TAG, "CommsBTConnect.run done");
        }
    }

    private class CommsBTConnected extends Thread{
        private InputStream inputStream;
        private OutputStream outputStream;

        CommsBTConnected(){
            try{
                Log.d(Main.LOG_TAG, "CommsBTConnected.getInputStream");
                inputStream = bluetoothSocket.getInputStream();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected getInputStream Exception: " + e.getMessage());
            }
            try{
                Log.d(Main.LOG_TAG, "CommsBTConnected.getOutputStream");
                outputStream = bluetoothSocket.getOutputStream();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected getOutputStream Exception: " + e.getMessage());
            }
        }
        public void run(){
            Log.d(Main.LOG_TAG, "CommsBTConnected.run");
            process();
        }
        private void process(){
            if(closeConnection){
                return;
            }
            read();
            try{
                outputStream.write("".getBytes());
            }catch(Exception e){
                Log.d(Main.LOG_TAG, "CommsBTConnected.process outputStream.write failed: " + e);
                Log.d(Main.LOG_TAG, "CommsBTConnected.process outputStream.write failed: " + e.getMessage());
                stopComms();
                startComms();
                return;
            }
            if(responseQueue.length() > 0 && !sendNextResponse()){
                stopComms();
                startComms();
                return;
            }
            handler.postDelayed(this::process, 100);
        }
        private boolean sendNextResponse(){
            try{
                JSONObject response = (JSONObject) responseQueue.get(0);
                responseQueue.remove(0);
                Log.d(Main.LOG_TAG, "CommsBTConnected.sendNextResponse: " + response.toString());
                outputStream.write(response.toString().getBytes());
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected.sendNextResponse Exception: " + e.getMessage());
                main.toast(R.string.fail_respond);
                return false;
            }
            return true;
        }
        private void read(){
            try{
                if(inputStream.available() < 5) return;
                long read_start = (new Date()).getTime();
                String request = "";

                while(inputStream.available() > 0){
                    byte[] buffer = new byte[inputStream.available()];
                    int numBytes = inputStream.read(buffer);
                    if(numBytes < 0){
                        Log.e(Main.LOG_TAG, "CommsBTConnected.read read error, request: " + request);
                        main.toast(R.string.fail_request);
                        return;
                    }
                    String temp = new String(buffer);
                    request += temp;
                    if(isValidJSON(request)){
                        gotRequest(request);
                        return;
                    }
                    if((new Date()).getTime() - read_start > 3000){
                        Log.e(Main.LOG_TAG, "CommsBTConnected.read started to read, no complete message after 3 seconds: " + request);
                        main.toast(R.string.fail_request);
                        return;
                    }
                    sleep100();
                }
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected.read Exception: " + e.getMessage());
                main.toast(R.string.fail_request);
            }
        }
        private void sleep100(){
            try{
                Thread.sleep(100);
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected.sleep100 exception: " + e.getMessage());
            }
        }
        private boolean isValidJSON(String json){
            try{
                new JSONObject(json);
            }catch(JSONException e){
                return false;
            }
            return true;
        }
    }
}