package com.windkracht8.musicplayer;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

@SuppressLint("MissingPermission") //Permissions are handled in Main, no further need to complain
public class CommsBT{
    final String MP_UUID = "6f34da3f-188a-4c8c-989c-2baacf8ea6e1";
    final BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    final JSONArray responseQueue;
    final Handler handler;
    final Main main;

    boolean connect = false;
    public final ArrayList<String> connect_failed_addresses = new ArrayList<>();
    public final ArrayList<String> queried_addresses = new ArrayList<>();

    public CommsBT(Main main){
        this.main = main;
        responseQueue = new JSONArray();
        BluetoothManager bluetoothManager = (BluetoothManager) main.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        handler = new Handler(Looper.getMainLooper());
        main.registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    private final BroadcastReceiver btStateReceiver = new BroadcastReceiver(){
        public void onReceive(Context context, Intent intent){
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){
                int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if(btState == BluetoothAdapter.STATE_TURNING_OFF){
                    stop();
                }else if(btState == BluetoothAdapter.STATE_ON){
                    connect();
                }
            }
        }
    };

    public void connect(){
        connect = true;
        search();
    }

    void search(){
        if(!connect) return;
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        if(bondedDevices == null){
            Log.e(Main.LOG_TAG, "bondedDevices == null");
            main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_bluetooth));
            return;
        }

        for(BluetoothDevice bondedDevice : bondedDevices){
            if(connect_failed_addresses.contains(bondedDevice.getAddress())) continue;
            for(ParcelUuid uuid : bondedDevice.getUuids()){
                if(uuid.toString().equals(MP_UUID)){
                    CommsConnect commsConnect = new CommsConnect(bondedDevice);
                    commsConnect.start();
                    return;
                }
            }
        }

        for(BluetoothDevice bondedDevice : bondedDevices){
            if(queried_addresses.contains(bondedDevice.getAddress())) continue;
            bondedDevice.fetchUuidsWithSdp();
            queried_addresses.add(bondedDevice.getAddress());
        }

        connect_failed_addresses.clear();
        handler.postDelayed(this::search, 3000);
    }

    void stop(){
        connect = false;
        handler.removeCallbacksAndMessages(null);
        try{
            main.unregisterReceiver(btStateReceiver);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Comms.stop unregisterReceiver: " + e.getMessage());
        }
    }

    private class CommsConnect extends Thread{
        private final BluetoothDevice device;

        public CommsConnect(BluetoothDevice device){
            this.device = device;
            try{
                bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(MP_UUID));
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsConnect Exception: " + e.getMessage());
                main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_bluetooth));
            }
        }

        public void run(){
            bluetoothAdapter.cancelDiscovery();
            try{
                bluetoothSocket.connect();
            }catch(IOException e){
                connect_failed_addresses.add(device.getAddress());
                search();
                return;
            }
            CommsConnected commsConnected = new CommsConnected();
            commsConnected.start();
        }
    }

    private class CommsConnected extends Thread{
        private InputStream inputStream;
        private OutputStream outputStream;

        public CommsConnected(){
            try{
                inputStream = bluetoothSocket.getInputStream();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsConnected getInputStream Exception: " + e.getMessage());
                main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_bluetooth));
            }
            try{
                outputStream = bluetoothSocket.getOutputStream();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsConnected getOutputStream Exception: " + e.getMessage());
                main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_bluetooth));
            }
        }

        public void run(){
            process();
        }

        private void process(){
            if(!connect){
                close();
                return;
            }
            if(!sendNextResponse()){
                close();
                search();
                return;
            }
            read();
            handler.postDelayed(this::process, 100);
        }

        private void close(){
            try{
                bluetoothSocket.close();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsConnected.close exception: " + e.getMessage());
            }
        }

        private void sleep100(){
            try{
                Thread.sleep(100);
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsConnected.sleep100 exception: " + e.getMessage());
            }
        }

        private boolean sendNextResponse(){
            try{
                outputStream.write("".getBytes());
                if(responseQueue.length() < 1) return true;
                JSONObject response = (JSONObject) responseQueue.get(0);
                responseQueue.remove(0);
                Log.d(Main.LOG_TAG, "CommsConnected.sendNextResponse: " + response.toString());
                outputStream.write(response.toString().getBytes());
            }catch(Exception e){
                if(e.getMessage() != null && e.getMessage().contains("Broken pipe")){
                    return false;
                }
                Log.e(Main.LOG_TAG, "CommsConnected.sendNextResponse Exception: " + e.getMessage());
                main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_respond));
            }
            return true;
        }

        private void read(){
            try{
                if(inputStream.available() == 0) return;
                String request = "";

                while(inputStream.available() > 0){
                    byte[] buffer = new byte[1024];
                    int numBytes = inputStream.read(buffer);
                    if(numBytes < 0){
                        Log.e(Main.LOG_TAG, "CommsConnected.read read error, request: " + request);
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
                Log.e(Main.LOG_TAG, "CommsConnected.read no more bytes to read, no valid json, request: " + request);
                main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_request));
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsConnected.read Exception: " + e.getMessage());
                main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_TOAST, R.string.fail_request));
            }
        }

        private void gotRequest(final JSONObject requestMessage){
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
                            Log.e(Main.LOG_TAG, "failed to create directory");
                            sendResponse("fileDetails", "failed to create directory");
                            return;
                        }
                        long length = requestData.getLong("length");
                        String ip = requestData.getString("ip");
                        int port = requestData.getInt("port");

                        main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_COMMS_FILE_START, path));
                        CommsWifi.receiveFile(main.handler_message, path, length, ip, port);
                        break;
                    case "deleteFile":
                        String delPath = requestMessage.getString("requestData");
                        sendResponse("deleteFile", Main.library.deleteFile(main, delPath));
                        break;
                    default:
                        Log.e(Main.LOG_TAG, "CommsConnected.gotRequest Unknown requestType: " + requestType);
                }

            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsConnected.gotRequest Exception: " + e.getMessage());
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
            Log.e(Main.LOG_TAG, "Comms.onReceiveSync Exception: " + e.getMessage());
            sendResponse("sync", "unexpected error");
        }
    }

    public void sendResponse(final String requestType, final String responseData){
        try{
            JSONObject response = new JSONObject();
            response.put("requestType", requestType);
            response.put("responseData", responseData);
            Log.i(Main.LOG_TAG, "sendResponse: " + response);
            responseQueue.put(response);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Comms.sendResponse String Exception: " + e.getMessage());
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
            Log.e(Main.LOG_TAG, "Comms.sendResponse JSONObject Exception: " + e.getMessage());
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