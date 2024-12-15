package com.windkracht8.wearmusicplayer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.splashscreen.SplashScreen;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends AppCompatActivity implements CommsBT.CommsBTInterface{
    static final String LOG_TAG = "WearMusicPlayer";
    static CommsBT commsBT;
    private CommsWifi commsWifi;
    static SharedPreferences sharedPreferences;
    static SharedPreferences.Editor sharedPreferences_editor;
    private ExecutorService executorService;

    private TextView main_available;
    private TextView main_device;
    private ImageView main_icon;
    private TextView main_status;
    private LinearLayout main_ll;
    private ImageView main_loading_icon;
    private final ArrayList<Item> items = new ArrayList<>();
    Item itemInProgress;
    private boolean showSplash = true;
    private static boolean hasBTPermission = false;
    static boolean hasReadPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> showSplash);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        sharedPreferences = getSharedPreferences("com.windkracht8.wearmusicplayer", Context.MODE_PRIVATE);
        sharedPreferences_editor = sharedPreferences.edit();

        main_available = findViewById(R.id.main_available);
        main_icon = findViewById(R.id.main_icon);
        main_icon.setOnClickListener(view -> onIconClick());
        main_icon.setColorFilter(getColor(R.color.icon_disabled));
        main_device = findViewById(R.id.main_device);
        main_status = findViewById(R.id.main_status);
        main_ll = findViewById(R.id.main_ll);
        main_loading_icon = findViewById(R.id.main_loading_icon);
        main_loading_icon.setBackgroundResource(R.drawable.icon_animate);
        ((AnimatedVectorDrawable) main_loading_icon.getBackground()).start();

        runInBackground(() -> commsWifi = new CommsWifi(this));

        checkPermissions();
        startBT();
        runInBackground(() -> Library.scanFiles(this));
        showSplash = false;
    }

    private void checkPermissions(){
        if(Build.VERSION.SDK_INT >= 33){
            hasReadPermission = hasPermission(Manifest.permission.READ_MEDIA_AUDIO);
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    && hasPermission(android.Manifest.permission.BLUETOOTH_SCAN);
            if(!hasReadPermission || !hasBTPermission){
                ActivityCompat.requestPermissions(this
                        ,new String[]{
                            Manifest.permission.READ_MEDIA_AUDIO
                            ,Manifest.permission.BLUETOOTH_CONNECT
                            ,Manifest.permission.BLUETOOTH_SCAN
                        }
                        ,1
                );
            }
        }else if(Build.VERSION.SDK_INT >= 31){
            hasReadPermission = hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    && hasPermission(android.Manifest.permission.BLUETOOTH_SCAN);
            if(!hasReadPermission || !hasBTPermission){
                ActivityCompat.requestPermissions(this
                        ,new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE
                            ,Manifest.permission.BLUETOOTH_CONNECT
                            ,Manifest.permission.BLUETOOTH_SCAN
                        }
                        ,1
                );
            }
        }else{
            hasReadPermission = hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH);
            if(!hasReadPermission || !hasBTPermission){
                ActivityCompat.requestPermissions(this
                        ,new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE
                            ,Manifest.permission.BLUETOOTH
                        }
                        ,1
                );
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(LOG_TAG, "Main.onRequestPermissionsResult");
        for(int i=0; i<permissions.length; i++){
            if(permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE)
                    || permissions[i].equals(Manifest.permission.READ_MEDIA_AUDIO)
            ){
                if(grantResults[i] == PackageManager.PERMISSION_GRANTED){
                    hasReadPermission = true;
                    runInBackground(() -> Library.scanFiles(this));
                }
                break;
            }
        }
        for(int i=0; i<permissions.length; i++){
            if(permissions[i].equals(Manifest.permission.BLUETOOTH_CONNECT)
                    || permissions[i].equals(Manifest.permission.BLUETOOTH)
            ){
                if(grantResults[i] == PackageManager.PERMISSION_GRANTED){
                    hasBTPermission = true;
                    startBT();
                }else{
                    onBTError(R.string.fail_BT_denied);
                }
                break;
            }
        }
    }
    private boolean hasPermission(String permission){
        return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }
    private void runInBackground(Runnable runnable){
        if(executorService == null) executorService = Executors.newCachedThreadPool();
        executorService.execute(runnable);
    }

    void libraryFilesScanned(){
        runOnUiThread(()->{
            findViewById(R.id.main_loading).setVisibility(View.GONE);
            for(Library.LibDir libDir : Library.dir_music.libDirs){
                Item item = new Item(this, libDir);
                items.add(item);
                main_ll.addView(item);
            }
            for(Library.LibTrack libTrack : Library.dir_music.libTracks){
                Item item = new Item(this, libTrack);
                items.add(item);
                main_ll.addView(item);
            }
        });
    }
    void libraryNewStatuses(){
        runOnUiThread(()->items.forEach(Item::newStatus));
    }
    private void startBT(){
        if(!hasBTPermission){
            onBTStartDone();
            return;
        }
        if(commsBT == null){
            commsBT = new CommsBT(this);
            commsBT.addListener(this);
        }
        runInBackground(() -> commsBT.startComms());
    }

    @Override
    public void onBackPressed(){
        if(commsWifi.running){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.alert_exit_title);
            builder.setMessage(R.string.alert_exit_message);
            builder.setPositiveButton(R.string.alert_exit_positive, (dialog, which) -> finishAndRemoveTask());
            builder.setNegativeButton(R.string.alert_exit_negative, (dialog, which) -> dialog.dismiss());
            builder.create().show();
        }else{
            finishAndRemoveTask();
        }
    }

    private void onIconClick(){
        if(commsBT == null) return;
        switch(commsBT.status){
            case CONNECTED -> commsBT.disconnect();
            case DISCONNECTED -> startActivity(new Intent(this, DeviceSelect.class));
        }
    }

    void onItemStatusPressed(Item item){
        if(cantSendRequest()) return;
        switch(item.libItem.status){
            case FULL:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.alert_delete_title);
                builder.setMessage(R.string.alert_delete_message);
                builder.setPositiveButton(R.string.alert_delete_positive, (dialog, which)-> {
                    itemInProgress = item;
                    runInBackground(()-> commsBT.sendRequestDeleteFile(item.libItem.path));
                    dialog.dismiss();
                });
                builder.setNegativeButton(R.string.alert_delete_negative, (dialog, which) -> dialog.dismiss());
                builder.create().show();
                break;
            case PARTIAL:
                break;
            case NOT:
                runInBackground(()-> commsWifi.queueFile(item));
                break;
        }
    }

    private boolean cantSendRequest(){
        if(commsBT != null && commsBT.status == CommsBT.Status.CONNECTED) return false;
        main_status.setText(R.string.fail_BT);
        return true;
    }

    void sendFileDetailsRequest(Library.LibItem libItem, String ipAddress){
        if(cantSendRequest()) return;
        runInBackground(()-> commsBT.sendRequestFileDetails(libItem, ipAddress));
    }
    private void sendSyncRequest(){
        Log.d(Main.LOG_TAG, "Main.sendSyncRequest");
        if(cantSendRequest()) return;
        try{
            JSONObject requestData = new JSONObject();
            runInBackground(()->commsBT.sendRequest("sync", requestData));
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Main.sendSyncRequest Exception: " + e.getMessage());
            onBTError(R.string.fail_sync);
        }
    }

    private void gotFreeSpace(long freeSpace){
        main_available.setText(bytesToHuman(freeSpace));
    }
    private String bytesToHuman(long bytes){
        long GB = 1073741824;
        long MB = 1048576;
        long KB = 1024;
        if(bytes > GB*10){
            double gbs = (double) bytes/GB;
            return String.format(Locale.getDefault(), "%.0f GB", gbs);
        }else if(bytes > GB*5){
            double gbs = (double) bytes/GB;
            return String.format(Locale.getDefault(), "%.3f GB", gbs);
        }else if(bytes > MB){
            double mbs = (double) bytes/MB;
            return String.format(Locale.getDefault(), "%.0f MB", mbs);
        }else if(bytes > KB){
            double kbs = (double) bytes/KB;
            return String.format(Locale.getDefault(), "%.0f KB", kbs);
        }
        return bytes + " B";
    }

    private String rps(int resource, String string){return getString(resource) + " " + string;}

    @Override
    public void onBTStartDone(){
        Log.d(LOG_TAG, "Main.onBTStartDone");
        runOnUiThread(()->{
            if(commsBT != null  && commsBT.status == CommsBT.Status.DISCONNECTED){
                startActivity(new Intent(this, DeviceSelect.class));
            }
            main_loading_icon.setVisibility(View.GONE);
        });
    }
    @Override
    public void onBTConnecting(String deviceName){
        Log.d(LOG_TAG, "Main.onBTConnecting");
        runOnUiThread(()-> {
            main_icon.setColorFilter(getColor(R.color.text));
            main_device.setTextColor(getColor(R.color.text));
            main_device.setText(rps(R.string.connecting_to, deviceName));
        });
    }
    @Override
    public void onBTConnectFailed(){
        runOnUiThread(()->{
            main_icon.setColorFilter(getColor(R.color.error));
            main_device.setTextColor(getColor(R.color.error));
            main_device.setText(R.string.fail_BT);
        });
    }
    @Override
    public void onBTConnected(String deviceName){
        runOnUiThread(()->{
            main_icon.setColorFilter(getColor(R.color.text));
            main_device.setTextColor(getColor(R.color.text));
            main_device.setText(rps(R.string.connected_to, deviceName));
            sendSyncRequest();
        });
    }
    @Override
    public void onBTDisconnected(){
        runOnUiThread(()->{
            main_available.setText("");
            main_icon.setColorFilter(getColor(R.color.icon_disabled));
            main_device.setTextColor(getColor(R.color.text));
            main_device.setText(R.string.disconnected);
            main_status.setText("");
            items.forEach(Item::clearStatus);
        });
    }
    @Override
    public void onBTSending(String requestType){
        runOnUiThread(()->{
            switch(requestType){
                case "fileDetails"-> main_status.setText(R.string.sending_file);
                case "deleteFile"-> main_status.setText(R.string.deleting_file);
            }
        });
    }
    @Override
    public void onBTResponse(JSONObject response){
        try{
            String requestType = response.getString("requestType");
            switch(requestType){
                case "sync":
                    JSONObject responseDataSync = response.getJSONObject("responseData");
                    JSONArray tracks = responseDataSync.getJSONArray("tracks");
                    long freeSpaceSync = responseDataSync.getLong("freeSpace");
                    runOnUiThread(()->gotFreeSpace(freeSpaceSync));
                    runInBackground(()->Library.updateLibWithFilesOnWatch(this, tracks));
                    break;
                case "fileDetails":
                    commsWifi.stop();
                    String fileDetailsError = response.getString("responseData");
                    Log.e(LOG_TAG, "Main.gotResponse fileDetails responseData: " + fileDetailsError);
                    onBTError(R.string.fail_send_file);
                    break;
                case "fileBinary":
                    Object responseDataFileDetails = response.get("responseData");
                    if(responseDataFileDetails instanceof String){
                        Log.e(LOG_TAG, "Main.gotResponse fileBinary responseData: " + responseDataFileDetails);
                        onBTError(R.string.fail_send_file);
                        break;
                    }
                    JSONObject responseData = response.getJSONObject("responseData");
                    long freeSpaceFileDetails = responseData.getLong("freeSpace");
                    runOnUiThread(()->{
                        gotFreeSpace(freeSpaceFileDetails);
                        itemInProgress.libItem.setStatus(this, Library.LibItem.Status.FULL);
                    });
                    main_status.setText(R.string.received_file);
                    commsWifi.canSendNext = true;
                    break;
                case "deleteFile":
                    if(response.getString("responseData").equals("OK")){
                        itemInProgress.libItem.setStatus(this, Library.LibItem.Status.NOT);
                        main_status.setText(R.string.deleted_file);
                        break;
                    }else{
                        Log.e(LOG_TAG, "Main.gotResponse deleteFile");
                        onBTError(R.string.fail_delete_file);
                    }
                    break;
            }
        }catch(Exception e){
            Log.e(LOG_TAG, "Main.gotResponse: " + e.getMessage());
            onBTError(R.string.fail_response);
        }
    }
    @Override
    public void onBTError(int message){
        runOnUiThread(()->{
            main_icon.setColorFilter(getColor(R.color.error));
            main_device.setTextColor(getColor(R.color.error));
            main_device.setText(message);
            main_status.setText("");
        });
    }
}
