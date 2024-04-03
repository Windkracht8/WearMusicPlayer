package com.windkracht8.wearmusicplayer;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@SuppressLint("MissingPermission")//Permissions are handled in initBT
public class CommsBT{
    private final String WMP_UUID = "6f34da3f-188a-4c8c-989c-2baacf8ea6e1";
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private final Handler handler;
    private final Main main;

    enum Status{INIT, CONNECTING, CONNECT_TIMEOUT, SEARCHING,
        SEARCH_TIMEOUT, CONNECTED, DISCONNECTED, FATAL}
    Status status = Status.INIT;
    private boolean closeConnection = false;
    private Set<String> wmp_device_addresses = new HashSet<>();
    private int devices_fetch_pending = 0;
    private int devices_connect_pending = 0;
    private final JSONArray requestQueue = new JSONArray();
    private String fileNameInRequestQueue;

    public CommsBT(Main main){
        this.main = main;
        handler = new Handler(Looper.getMainLooper());

        BluetoothManager bm = (BluetoothManager) main.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bm.getAdapter();
        if(bluetoothAdapter == null){
            gotError(main.getString(R.string.fail_BT_off));
            return;
        }

        if(bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON){
            checkWMPDeviceAddresses();
        }else{
            gotError(main.getString(R.string.fail_BT_off));
        }

        IntentFilter btIntentFilter = new IntentFilter();
        btIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        btIntentFilter.addAction(BluetoothDevice.ACTION_UUID);
        BroadcastReceiver btBroadcastReceiver = new BroadcastReceiver(){
            public void onReceive(Context context, Intent intent){
                if(closeConnection) return;
                if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){
                    int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                    if(btState == BluetoothAdapter.STATE_TURNING_OFF){
                        gotError(main.getString(R.string.fail_BT_off));
                        stopComms();
                    }else if(btState == BluetoothAdapter.STATE_ON){
                        startComms();
                    }
                }else if(BluetoothDevice.ACTION_UUID.equals(intent.getAction())){
                    devices_fetch_pending--;
                    BluetoothDevice bluetoothDevice;
                    Parcelable[] parcelUuids;
                    if(Build.VERSION.SDK_INT >= 33){
                        bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                        parcelUuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID, Parcelable.class);
                    }else{
                        bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        parcelUuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                    }
                    if(bluetoothDevice != null && parcelUuids != null){
                        for(Parcelable parcelUuid : parcelUuids){
                            if(parcelUuid.toString().equals(WMP_UUID)){
                                foundWMPDevice(bluetoothDevice);
                                return;
                            }
                        }
                    }
                    if(devices_fetch_pending == 0 && devices_connect_pending == 0){
                        updateStatus(Status.SEARCH_TIMEOUT);
                        stopComms();
                    }
                }
            }
        };
        main.registerReceiver(btBroadcastReceiver, btIntentFilter);
    }

    private void checkWMPDeviceAddresses(){
        Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
        wmp_device_addresses = Main.sharedPreferences.getStringSet("wmp_device_addresses", wmp_device_addresses);
        for(String wmp_device_address : wmp_device_addresses){
            boolean stillBonded = false;
            for(BluetoothDevice device : devices){
                if(device.getAddress().equals(wmp_device_address)){
                    stillBonded = true;
                    break;
                }
            }
            if(!stillBonded){
                wmp_device_addresses.remove(wmp_device_address);
                Main.sharedPreferences_editor.putStringSet("wmp_device_addresses", wmp_device_addresses);
                Main.sharedPreferences_editor.apply();
            }
        }
    }

    void startComms(){
        if(bluetoothAdapter.getState() != BluetoothAdapter.STATE_ON) return;
        Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
        Log.d(Main.LOG_TAG, "CommsBT.startComms " + wmp_device_addresses.size() + "/" + devices.size());
        closeConnection = false;
        devices_connect_pending = 0;
        devices_fetch_pending = 0;

        if(status == Status.CONNECT_TIMEOUT || status == Status.SEARCH_TIMEOUT){
            updateStatus(Status.SEARCHING);
            for(BluetoothDevice device : devices){
                if(wmp_device_addresses.contains(device.getAddress())){
                    connectWMPDevice(device);
                }else{
                    searchWMPDevice(device);
                }
            }
        }else if(wmp_device_addresses.isEmpty()){
            updateStatus(Status.SEARCHING);
            for(BluetoothDevice device : devices){
                searchWMPDevice(device);
            }
        }else{
            updateStatus(Status.CONNECTING);
            for(BluetoothDevice device : devices){
                if(wmp_device_addresses.contains(device.getAddress())){
                    connectWMPDevice(device);
                }
            }
        }
    }
    private void searchWMPDevice(BluetoothDevice device){
        Log.d(Main.LOG_TAG, "CommsBT.searchWMPDevice: " + device.getName());
        if(wmp_device_addresses.contains(device.getAddress())){
            return;
        }
        ParcelUuid[] uuids = device.getUuids();
        if(uuids == null){
            fetchUuids(device);
            return;
        }
        for(ParcelUuid uuid : uuids){
            if(uuid.toString().equals(WMP_UUID)){
                foundWMPDevice(device);
                return;
            }
        }
        for(ParcelUuid uuid : uuids){
            if(uuid.toString().equals("5e8945b0-9525-11e3-a5e2-0800200c9a66") || //Wear
                    uuid.toString().equals("0000110a-0000-1000-8000-00805f9b34fb") //Audio source
            ){
                Log.d(Main.LOG_TAG, "CommsBT.searchWMPDevice device looks like: " + device.getName());
                main.gotStatus(String.format("%s %s", main.getString(R.string.looks_like), device.getName()));
                connectWMPDevice(device);
                return;
            }
        }
        fetchUuids(device);
    }
    private void fetchUuids(BluetoothDevice device){
        if(closeConnection || status != Status.SEARCHING) return;
        Log.d(Main.LOG_TAG, "CommsBT.fetchUuids: " + device.getName());
        devices_fetch_pending++;
        device.fetchUuidsWithSdp();
    }
    private void foundWMPDevice(BluetoothDevice wmp_device){
        Log.d(Main.LOG_TAG, "CommsBT.foundWMPDevice: " + wmp_device.getName());
        main.gotStatus(String.format("%s %s", main.getString(R.string.found_new), wmp_device.getName()));
        wmp_device_addresses.add(wmp_device.getAddress());
        Main.sharedPreferences_editor.putStringSet("wmp_device_addresses", wmp_device_addresses);
        Main.sharedPreferences_editor.apply();
        connectWMPDevice(wmp_device);
    }
    private void connectWMPDevice(BluetoothDevice wmp_device){
        if(closeConnection || (status != Status.SEARCHING && status != Status.CONNECTING)) return;
        Log.d(Main.LOG_TAG, "CommsBT.connectWMPDevice: " + wmp_device.getName());
        main.gotStatus(String.format("%s %s", main.getString(R.string.try_connect_to), wmp_device.getName()));
        devices_connect_pending++;
        (new CommsBT.CommsBTConnect(wmp_device)).start();
    }

    void stopComms(){
        Log.d(Main.LOG_TAG, "CommsBT.stopComms");
        closeConnection = true;
    }

    void sendRequestFileDetails(Library.LibItem libItem, String ipAddress){
        try{
            fileNameInRequestQueue = libItem.name;
            java.io.File file = new java.io.File(libItem.uri);
            libItem.length = file.length();
            JSONObject requestData = new JSONObject();
            requestData.put("path", libItem.path);
            requestData.put("length", file.length());
            requestData.put("ip", ipAddress);
            requestData.put("port", CommsWifi.PORT_NUMBER);
            sendRequest("fileDetails", requestData);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.sendFileDetails exception: " + e.getMessage());
        }
    }

    void sendRequestDeleteFile(String requestData){
        Log.d(Main.LOG_TAG, "CommsBT.sendRequestDeleteFile");
        try{
            JSONObject request = new JSONObject();
            request.put("requestType", "deleteFile");
            request.put("requestData", requestData);
            requestQueue.put(request);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.sendRequest Exception: " + e.getMessage());
        }
    }
    void sendRequest(String requestType, JSONObject requestData){
        Log.d(Main.LOG_TAG, "CommsBT.sendRequest: " + requestType);
        try{
            JSONObject request = new JSONObject();
            request.put("requestType", requestType);
            request.put("requestData", requestData);
            requestQueue.put(request);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBT.sendRequest Exception: " + e.getMessage());
        }
    }
    private void gotResponse(String response){
        Log.d(Main.LOG_TAG, "CommsBTConnected.gotResponse: " + response);
        try{
            JSONObject responseMessage = new JSONObject(response);
            main.gotResponse(responseMessage);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "CommsBTConnected.gotResponse: " + e.getMessage());
            main.toast(R.string.fail_response);
            main.gotError(String.format("%s %s", main.getString(R.string.response_error), e.getMessage()));
        }
    }
    private void gotError(String message){
        updateStatus(Status.FATAL);
        main.gotError(message);
    }
    void updateStatus(Status status){
        Log.d(Main.LOG_TAG, "CommsBT.updateStatus " + this.status + " > " + status);
        if(this.status != status) main.updateStatus(status);
        this.status = status;
    }

    private class CommsBTConnect extends Thread{
        private CommsBTConnect(BluetoothDevice device){
            Log.d(Main.LOG_TAG, "CommsBTConnect " + device.getName());
            try{
                bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(WMP_UUID));
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnect Exception: " + e.getMessage());
                main.toast(R.string.fail_BT_connect);
            }
        }
        public void run(){
            bluetoothAdapter.cancelDiscovery();
            try{
                bluetoothSocket.connect();
                (new CommsBTConnected()).start();
            }catch(Exception e){
                Log.d(Main.LOG_TAG, "CommsBTConnect.run failed: " + e.getMessage());
                try{
                    bluetoothSocket.close();
                }catch(Exception e2){
                    Log.d(Main.LOG_TAG, "CommsBTConnect.run close failed: " + e2.getMessage());
                }
                devices_connect_pending--;
                if(status == Status.CONNECTING && devices_connect_pending == 0){
                    updateStatus(Status.CONNECT_TIMEOUT);
                }
                if(status == Status.SEARCHING &&
                        devices_fetch_pending == 0 &&
                        devices_connect_pending == 0
                ){
                    updateStatus(Status.SEARCH_TIMEOUT);
                }
            }
        }
    }

    private class CommsBTConnected extends Thread{
        private InputStream inputStream;
        private OutputStream outputStream;

        CommsBTConnected(){
            Log.d(Main.LOG_TAG, "CommsBTConnected");
            try{
                inputStream = bluetoothSocket.getInputStream();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected getInputStream Exception: " + e.getMessage());
                updateStatus(Status.DISCONNECTED);
                main.toast(R.string.fail_BT_connect);
                return;
            }
            try{
                outputStream = bluetoothSocket.getOutputStream();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected getOutputStream Exception: " + e.getMessage());
                updateStatus(Status.DISCONNECTED);
                main.toast(R.string.fail_BT_connect);
                return;
            }
            updateStatus(Status.CONNECTED);
        }
        public void run(){process();}
        private void close(){
            Log.d(Main.LOG_TAG, "CommsBTConnected.close");
            updateStatus(Status.DISCONNECTED);
            try{
                bluetoothSocket.close();
                for(int i=requestQueue.length(); i>0; i--){
                    requestQueue.remove(i-1);
                }
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected.close exception: " + e.getMessage());
            }
        }
        private void process(){
            if(closeConnection){
                close();
                return;
            }
            try{
                outputStream.write("".getBytes());
            }catch(Exception e){
                Log.d(Main.LOG_TAG, "Connection closed");
                close();
                return;
            }
            if(!sendNextRequest()){
                close();
                return;
            }
            read();
            handler.postDelayed(this::process, 100);
        }
        private boolean sendNextRequest(){
            try{
                outputStream.write("".getBytes());
                if(requestQueue.length() < 1) return true;
                JSONObject request = (JSONObject) requestQueue.get(0);
                requestQueue.remove(0);
                Log.d(Main.LOG_TAG, "CommsBTConnected.sendNextRequest: " + request.toString());
                String requestType = request.getString("requestType");
                if(requestType.equals("fileDetails")){
                    main.gotStatus(String.format("%s %s", main.getString(R.string.send_file), fileNameInRequestQueue));
                }else{
                    main.gotStatus(String.format("%s %s", main.getString(R.string.send_request), requestType));
                }
                outputStream.write(request.toString().getBytes());
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected.sendNextRequest Exception: " + e.getMessage());
                main.toast(R.string.fail_send_message);
                main.gotStatus(String.format("%s %s", main.getString(R.string.send_error), e.getMessage()));
                return false;
            }
            return true;
        }
        private void read(){
            try{
                if(inputStream.available() < 5) return;
                long read_start = (new Date()).getTime();
                String response = "";

                while(inputStream.available() > 0){
                    byte[] buffer = new byte[inputStream.available()];
                    int numBytes = inputStream.read(buffer);
                    if(numBytes < 0){
                        Log.e(Main.LOG_TAG, "CommsBTConnected.read read error, response: " + response);
                        main.toast(R.string.fail_response);
                        main.gotError(main.getString(R.string.fail_response));
                        return;
                    }
                    String temp = new String(buffer);
                    response += temp;
                    if(isValidJSON(response)){
                        gotResponse(response);
                        return;
                    }
                    if((new Date()).getTime() - read_start > 3000){
                        Log.e(Main.LOG_TAG, "CommsBTConnected.read started to read, no complete message after 3 seconds: " + response);
                        main.toast(R.string.fail_response);
                        main.gotError(main.getString(R.string.incomplete_message));
                        return;
                    }
                    sleep100();
                }
                Log.e(Main.LOG_TAG, "CommsBTConnected.read inputStream.available() == 0, did not get valid json : " + response);
                main.toast(R.string.fail_response);
                main.gotError(main.getString(R.string.incomplete_message));
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "CommsBTConnected.read Exception: " + e.getMessage());
                main.toast(R.string.fail_response);
                main.gotError(String.format("%s %s", main.getString(R.string.response_error), e.getMessage()));
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