package com.windkracht8.musicplayer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends AppCompatActivity{
    public static final String LOG_TAG = "MusicPlayer";
    public static final int MESSAGE_TOAST = 100;
    public static final int MESSAGE_LIBRARY_READY = 200;
    public static final int MESSAGE_LIBRARY_UPDATE_STATUS = 201;
    public static final int MESSAGE_COMMS_BT_OFF = 300;
    public static final int MESSAGE_COMMS_ERROR = 301;
    public static final int MESSAGE_COMMS_STATUS = 302;
    public static final int MESSAGE_COMMS_RESPONSE = 303;
    public static final int MESSAGE_COMMS_PROGRESS = 304;
    private final static int REQUEST_PERMISSION_CODE = 1;

    public enum Status {FULL, PARTIAL, NOT, UNKNOWN}

    private CommsBT commsBT = null;
    private CommsWifi commsWifi = null;
    private final Library library = new Library();

    private long back_press_time;
    private Handler handler_main;
    private ExecutorService executorService;

    private TextView main_status;
    private TextView main_error;
    private LinearLayout main_ll;
    private final ArrayList<Item> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        executorService = Executors.newFixedThreadPool(4);
        handler_main = new Handler(Looper.getMainLooper());
        commsWifi = new CommsWifi();

        main_status = findViewById(R.id.main_status);
        main_error = findViewById(R.id.main_error);
        main_ll = findViewById(R.id.main_ll);

        executorService.submit(this::initBT);
        executorService.submit(() -> library.scanFiles(this));
        requestPermissions();
    }

    public final Handler handler_message = new Handler(Looper.getMainLooper()){
        public void handleMessage(Message msg){
            switch(msg.what){
                case MESSAGE_TOAST:
                    if(!(msg.obj instanceof Integer)) return;
                    Toast.makeText(getApplicationContext(), getString((Integer) msg.obj), Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_LIBRARY_READY:
                    loadFileList();
                    break;
                case MESSAGE_LIBRARY_UPDATE_STATUS:
                    for(Item item : items){
                        item.newStatus();
                    }
                    break;
                case MESSAGE_COMMS_BT_OFF:
                    gotError(getString(R.string.fail_BT_off));
                    break;
                case MESSAGE_COMMS_ERROR:
                    if(!(msg.obj instanceof String)) return;
                    gotError((String) msg.obj);
                    break;
                case MESSAGE_COMMS_STATUS:
                    if(!(msg.obj instanceof String)) return;
                    updateStatus((String) msg.obj);
                    break;
                case MESSAGE_COMMS_RESPONSE:
                    if(!(msg.obj instanceof JSONObject)) return;
                    gotResponse((JSONObject) msg.obj);
                    break;
                case MESSAGE_COMMS_PROGRESS:
                    Log.d(LOG_TAG, "Main.MESSAGE_COMMS_PROGRESS 1");
                    if(!(msg.obj instanceof Item)) return;
                    Log.d(LOG_TAG, "Main.MESSAGE_COMMS_PROGRESS 2");
                    ((Item) msg.obj).updateProgress();
                    Log.d(LOG_TAG, "Main.MESSAGE_COMMS_PROGRESS 3");
                    break;
            }
        }
    };

    private void loadFileList(){
        for(Library.LibDir libDir : library.dir_music.libDirs){
            Item item = new Item(this, libDir);
            items.add(item);
            main_ll.addView(item);
        }
        for(Library.LibTrack libTrack : library.dir_music.libTracks){
            Item item = new Item(this, libTrack);
            items.add(item);
            main_ll.addView(item);
        }
    }
    private void initBT(){
        if(Build.VERSION.SDK_INT >= 31){
            if(doesNotHavePermission(android.Manifest.permission.BLUETOOTH) ||
                    doesNotHavePermission(android.Manifest.permission.BLUETOOTH_CONNECT) ||
                    doesNotHavePermission(android.Manifest.permission.BLUETOOTH_SCAN)){
                return;
            }
        }else{
            if(doesNotHavePermission(android.Manifest.permission.BLUETOOTH)){
                return;
            }
        }
        if(commsBT == null) commsBT = new CommsBT(this);
        commsBT.startListening();
    }

    private void requestPermissions(){
        if(Build.VERSION.SDK_INT >= 31){
            if(doesNotHavePermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ||
                    doesNotHavePermission(android.Manifest.permission.BLUETOOTH) ||
                    doesNotHavePermission(android.Manifest.permission.BLUETOOTH_CONNECT) ||
                    doesNotHavePermission(android.Manifest.permission.BLUETOOTH_SCAN)){
                ActivityCompat.requestPermissions(this, new String[]{
                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                android.Manifest.permission.BLUETOOTH,
                                android.Manifest.permission.BLUETOOTH_CONNECT,
                                android.Manifest.permission.BLUETOOTH_SCAN},
                        REQUEST_PERMISSION_CODE);
            }
        }else{
            if(doesNotHavePermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ||
                    doesNotHavePermission(android.Manifest.permission.BLUETOOTH)){
                ActivityCompat.requestPermissions(this, new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.BLUETOOTH},
                        REQUEST_PERMISSION_CODE);
            }
        }
    }
    private boolean doesNotHavePermission(String permission){
        return ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean bt_granted = false;
        for(int i=0; i<permissions.length; i++){
            if(permissions[i].equals(Manifest.permission.BLUETOOTH_CONNECT) ||
                permissions[i].equals(Manifest.permission.BLUETOOTH_SCAN) ||
                permissions[i].equals(Manifest.permission.BLUETOOTH)){
                if(grantResults[i] == PackageManager.PERMISSION_DENIED){
                    updateStatus("FATAL");
                    gotError(getString(R.string.fail_BT_denied));
                    return;
                }else{
                    bt_granted = true;
                }
            }
        }
        if(bt_granted) initBT();
    }

    @Override
    public void onBackPressed(){
        handler_main.removeCallbacks(this::explainDoubleBack);
        Date date = new Date();
        if(date.getTime() - back_press_time < 1000){
            finish();
            System.exit(0);
        }else{
            handler_main.postDelayed(this::explainDoubleBack, 1000);
        }
        back_press_time = date.getTime();
    }
    private void explainDoubleBack(){
        Toast.makeText(getApplicationContext(), R.string.explainDoubleBack, Toast.LENGTH_SHORT).show();
    }

    public void onItemStatusPressed(Item item){
        Log.i(LOG_TAG, "Main.onItemStatusPressed: " + item.libItem.path + " " + item.libItem.status);
        switch(item.libItem.status){
            case FULL:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Confirm");
                builder.setMessage("Delete on watch?");
                builder.setPositiveButton("delete", (dialog, which) -> {
                    Log.i(LOG_TAG, "confirm: delete");
                    executorService.submit(() -> commsBT.sendRequest("deleteFile", item.libItem.path));
                    dialog.dismiss();
                });
                builder.setNegativeButton("cancel", (dialog, which) -> {
                    Log.i(LOG_TAG, "confirm: cancel");
                    dialog.dismiss();
                });
                builder.create().show();
                break;
            case PARTIAL:
                //TODO: ask what to do
                break;
            case NOT:
                if(CommsWifi.sendingFile){
                    Log.e(LOG_TAG, "Main.onItemStatusPressed: Can't send file, other in progress");
                    return;
                }
                if(cantSendRequest()){
                    Log.e(LOG_TAG, "Main.onItemStatusPressed: Can't send file, other in progress");
                    return;
                }
                executorService.submit(() -> commsWifi.sendFile(handler_message, item));
                String ipAddress = commsWifi.getIpAddress(this);
                executorService.submit(() -> commsBT.sendFileDetails(item.libItem, ipAddress));
                break;
        }
    }

    private boolean cantSendRequest(){
        if(commsBT != null && commsBT.status.equals("CONNECTED")){
            return false;
        }
        gotError(getString(R.string.first_connect));
        return true;
    }

    public void updateStatus(final String status){
        switch(status){
            case "FATAL":
                main_status.setVisibility(View.GONE);
                return;
            case "LISTENING":
                main_status.setText(getString(R.string.status_LISTENING));
                main_status.setVisibility(View.VISIBLE);
                main_error.setText("");
                break;
            case "CONNECTED":
                main_status.setText(getString(R.string.status_CONNECTED));
                main_status.setVisibility(View.VISIBLE);
                main_error.setText("");
                sendSyncRequest();
                break;
            case "DISCONNECTED":
                main_status.setText(getString(R.string.status_DISCONNECTED));
                main_status.setVisibility(View.VISIBLE);
                main_error.setText("");
                for(Item item : items){
                    item.clearStatus();
                }
                break;
            default:
                main_status.setText(status);//TODO: replace this with generic, should not happen
                main_status.setVisibility(View.VISIBLE);
                //main_status.setVisibility(View.GONE);
                main_error.setText(getString(R.string.status_ERROR));
        }
    }
    private void sendSyncRequest(){
        gotError("");
        if(cantSendRequest()){return;}
        try {
            JSONObject requestData = new JSONObject();
            commsBT.sendRequest("sync", requestData);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Main.sendSyncRequest Exception: " + e.getMessage());
            Toast.makeText(getApplicationContext(), R.string.fail_sync, Toast.LENGTH_SHORT).show();
        }
    }

    public void gotResponse(final JSONObject response){
        try{
            String requestType = response.getString("requestType");
            switch(requestType){
                case "sync":
                    JSONArray filesOnWatch = response.getJSONArray("responseData");
                    executorService.submit(() -> library.updateLibWithFilesOnWatch(handler_message, filesOnWatch));
                    break;
                case "fileDetails":
                    //executorService.submit(() -> commsBT.sendFileBinary());
                    commsWifi.stopSendFile();
                    String responseData = response.getString("responseData");
                    Log.e(Main.LOG_TAG, "Main.gotResponse fileDetails responseData: " + responseData);
                    Toast.makeText(getApplicationContext(), R.string.fail_response, Toast.LENGTH_SHORT).show();
                    break;
                case "deleteFile":
                    //TODO: update the status in the library and UI

                    break;
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Main.gotResponse: " + e.getMessage());
            Toast.makeText(getApplicationContext(), R.string.fail_response, Toast.LENGTH_SHORT).show();
        }
    }
    private void gotError(String error){
        main_error.setText(error);
    }
}
