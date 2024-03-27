package com.windkracht8.wearmusicplayer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends AppCompatActivity{
    static final String LOG_TAG = "WearMusicPlayer";
    private CommsBT commsBT = null;
    private CommsWifi commsWifi = null;
    private final Library library = new Library();
    static SharedPreferences sharedPreferences;
    static SharedPreferences.Editor sharedPreferences_editor;
    ExecutorService executorService;

    private TextView main_available;
    private ImageView main_icon;
    private ScrollView main_sv_BT_log;
    private LinearLayout main_ll_BT_log;
    private LinearLayout main_ll;
    private final ArrayList<Item> items = new ArrayList<>();
    private Item itemDelete;
    private final ArrayList<String> prevStatuses = new ArrayList<>();
    private static boolean hasBTPermission = false;
    static boolean hasReadPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        sharedPreferences = getSharedPreferences("com.windkracht8.wearmusicplayer", Context.MODE_PRIVATE);
        sharedPreferences_editor = sharedPreferences.edit();
        executorService = Executors.newFixedThreadPool(4);

        main_available = findViewById(R.id.main_available);
        main_icon = findViewById(R.id.main_icon);
        main_icon.setOnClickListener(view -> onIconClick());
        main_sv_BT_log = findViewById(R.id.main_sv_BT_log);
        main_ll_BT_log = findViewById(R.id.main_ll_BT_log);
        main_ll = findViewById(R.id.main_ll);

        try{
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version;
            if(android.os.Build.VERSION.SDK_INT >= 28){
                version = String.format("Version %s (%s)", packageInfo.versionName, packageInfo.getLongVersionCode());
            }else{
                version = String.format("Version %s (%s)", packageInfo.versionName, packageInfo.versionCode);
            }
            Log.d(Main.LOG_TAG, version);
            gotStatus(version);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Main.onCreate getPackageInfo Exception: " + e.getMessage());
        }

        commsBT = new CommsBT(this);
        commsWifi = new CommsWifi(this);

        requestPermissions();
        executorService.submit(() -> library.scanFiles(this));
        startBT();
        //commsWifi.startP2PWifi(this);//TODO:test
    }

    void toast(int message){
        runOnUiThread(()->Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }
    void libraryFilesScanned(){
        runOnUiThread(()->{
            findViewById(R.id.main_loading).setVisibility(View.GONE);
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
        });
    }
    void libraryNewStatuses(){
        runOnUiThread(()->items.forEach(Item::newStatus));
    }
    private void startBT(){
        if(!hasBTPermission) return;
        executorService.submit(() -> commsBT.startComms());
    }

    private void requestPermissions(){
        if(Build.VERSION.SDK_INT >= 33){
            hasReadPermission = hasPermission(Manifest.permission.READ_MEDIA_AUDIO);
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH_SCAN)
                    && hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT);
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
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH_SCAN)
                    && hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT);
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
                    executorService.submit(() -> library.scanFiles(this));
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
                    updateStatus(CommsBT.Status.FATAL);
                    gotError(getString(R.string.fail_BT_denied));
                }
                break;
            }
        }
    }
    private boolean hasPermission(String permission){
        return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onBackPressed(){
        if(commsWifi.running){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.alert_exit_title);
            builder.setMessage(R.string.alert_exit_message);
            builder.setPositiveButton(R.string.alert_exit_positive, (dialog, which) -> {
                finish();
                System.exit(0);
            });
            builder.setNegativeButton(R.string.alert_exit_negative, (dialog, which) -> dialog.dismiss());
            builder.create().show();
        }else{
            finish();
            System.exit(0);
        }
    }

    private void onIconClick(){
        switch(commsBT.status){
            case CONNECTING:
                commsBT.updateStatus(CommsBT.Status.CONNECT_TIMEOUT);
                commsBT.stopComms();
                break;
            case SEARCHING:
                commsBT.updateStatus(CommsBT.Status.SEARCH_TIMEOUT);
                commsBT.stopComms();
                break;
            case CONNECT_TIMEOUT:
            case SEARCH_TIMEOUT:
            case DISCONNECTED:
                startBT();
                break;
            case CONNECTED:
                commsBT.status = CommsBT.Status.DISCONNECTED;
                commsBT.stopComms();
                main_icon.setColorFilter(getColor(R.color.icon_disabled));
        }
    }

    void onItemStatusPressed(Item item){
        if(cantSendRequest()){return;}
        switch(item.libItem.status){
            case FULL:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.alert_delete_title);
                builder.setMessage(R.string.alert_delete_message);
                builder.setPositiveButton(R.string.alert_delete_positive, (dialog, which)-> {
                    itemDelete = item;
                    executorService.submit(()-> commsBT.sendRequestDeleteFile(item.libItem.path));
                    dialog.dismiss();
                });
                builder.setNegativeButton(R.string.alert_delete_negative, (dialog, which) -> dialog.dismiss());
                builder.create().show();
                break;
            case PARTIAL:
                break;
            case NOT:
                executorService.submit(()-> commsWifi.queueFile(item));
                break;
        }
    }

    private boolean cantSendRequest(){
        if(commsBT != null && commsBT.status == CommsBT.Status.CONNECTED){
            return false;
        }
        gotError(getString(R.string.first_connect));
        return true;
    }

    void gotStatus(String status){
        if(prevStatuses.contains(status)){
            return;
        }
        prevStatuses.add(status);
        if(prevStatuses.size()>2) prevStatuses.remove(0);

        runOnUiThread(()-> {
            TextView tv = new TextView(this);
            tv.setText(status);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            main_ll_BT_log.addView(tv);
            tv.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout(){
                    tv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    main_sv_BT_log.fullScroll(View.FOCUS_DOWN);
                }
            });
        });
    }
    void gotError(String error){
        Log.d(Main.LOG_TAG, "Main.gotError: " + error);
        runOnUiThread(()-> {
            TextView tv = new TextView(this);
            tv.setText(error);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setTextColor(getColor(R.color.error));
            main_ll_BT_log.addView(tv);
            tv.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){
                @Override
                public void onGlobalLayout(){
                    tv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    main_sv_BT_log.fullScroll(View.FOCUS_DOWN);
                }
            });
        });
    }
    void updateStatus(CommsBT.Status status){
        runOnUiThread(()-> {
            switch(status){
                case FATAL:
                    main_icon.setBackgroundResource(R.drawable.icon_watch);
                    main_icon.setColorFilter(getColor(R.color.error));
                    return;
                case SEARCHING:
                    main_icon.setBackgroundResource(R.drawable.icon_watch_searching);
                    main_icon.setColorFilter(getColor(R.color.icon_disabled));
                    gotFreeSpace(0);
                    ((AnimatedVectorDrawable) main_icon.getBackground()).start();
                    prevStatuses.clear();
                    gotStatus(getString(R.string.status_SEARCHING));
                    break;
                case SEARCH_TIMEOUT:
                    main_icon.setBackgroundResource(R.drawable.icon_watch);
                    main_icon.setColorFilter(getColor(R.color.icon_disabled));
                    gotFreeSpace(0);
                    gotError(getString(R.string.status_SEARCH_TIMEOUT));
                    items.forEach(Item::clearStatus);
                    break;
                case CONNECTING:
                    main_icon.setBackgroundResource(R.drawable.icon_watch_searching);
                    main_icon.setColorFilter(getColor(R.color.icon_disabled));
                    gotFreeSpace(0);
                    ((AnimatedVectorDrawable) main_icon.getBackground()).start();
                    break;
                case CONNECT_TIMEOUT:
                    main_icon.setBackgroundResource(R.drawable.icon_watch);
                    main_icon.setColorFilter(getColor(R.color.icon_disabled));
                    gotStatus(getString(R.string.status_CONNECT_TIMEOUT));
                    break;
                case CONNECTED:
                    main_icon.setBackgroundResource(R.drawable.icon_watch);
                    main_icon.setColorFilter(getColor(R.color.text));
                    gotStatus(getString(R.string.status_CONNECTED));
                    sendSyncRequest();
                    break;
                case DISCONNECTED:
                    main_icon.setBackgroundResource(R.drawable.icon_watch);
                    main_icon.setColorFilter(getColor(R.color.icon_disabled));
                    gotStatus(getString(R.string.status_DISCONNECTED));
                    break;
            }
        });
    }
    void sendFileDetailsRequest(Library.LibItem libItem, String ipAddress){
        if(cantSendRequest()){return;}
        executorService.submit(()-> commsBT.sendRequestFileDetails(libItem, ipAddress));
    }
    private void sendSyncRequest(){
        Log.d(Main.LOG_TAG, "Main.sendSyncRequest");
        if(cantSendRequest()){return;}
        try{
            JSONObject requestData = new JSONObject();
            executorService.submit(()->commsBT.sendRequest("sync", requestData));
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Main.sendSyncRequest Exception: " + e.getMessage());
            toast(R.string.fail_sync);
        }
    }

    void gotResponse(JSONObject response){
        try{
            String requestType = response.getString("requestType");
            switch(requestType){
                case "sync":
                    JSONObject responseDataSync = response.getJSONObject("responseData");
                    JSONArray tracks = responseDataSync.getJSONArray("tracks");
                    long freeSpaceSync = responseDataSync.getLong("freeSpace");
                    runOnUiThread(()->gotFreeSpace(freeSpaceSync));
                    executorService.submit(()->library.updateLibWithFilesOnWatch(this, tracks));
                    gotStatus(String.format("%s %s", getString(R.string.received_response), requestType));
                    break;
                case "fileDetails":
                    executorService.submit(commsWifi::stop);
                    Log.e(LOG_TAG, "Main.gotResponse fileDetails responseData: " + response.get("responseData"));
                    toast(R.string.fail_send_file);
                    gotError(getString(R.string.fail_send_file));
                    break;
                case "fileBinary":
                    Object responseDataFileDetails = response.get("responseData");
                    if(responseDataFileDetails instanceof String){
                        Log.e(LOG_TAG, "Main.gotResponse fileBinary responseData: " + responseDataFileDetails);
                        toast(R.string.fail_send_file);
                        gotError(getString(R.string.fail_send_file));
                        break;
                    }
                    JSONObject responseData = response.getJSONObject("responseData");
                    long freeSpaceFileDetails = responseData.getLong("freeSpace");
                    runOnUiThread(() -> gotFreeSpace(freeSpaceFileDetails));
                    String path_done = responseData.getString("path");
                    runOnUiThread(()->items.forEach((i)->i.updateProgressDone(this, path_done)));
                    gotStatus(getString(R.string.received_file));
                    commsWifi.canSendNext = true;
                    break;
                case "deleteFile":
                    if(response.getString("responseData").equals("OK")){
                        itemDelete.libItem.status = Library.LibItem.Status.NOT;
                        runOnUiThread(()->itemDelete.newStatus());
                        gotStatus(String.format("%s %s", getString(R.string.received_response), requestType));
                        break;
                    }else{
                        Log.e(LOG_TAG, "Main.gotResponse deleteFile");
                        gotStatus(response.getString("responseData"));
                        toast(R.string.fail_delete_file);
                    }
                    break;
            }
        }catch(Exception e){
            Log.e(LOG_TAG, "Main.gotResponse: " + e.getMessage());
            toast(R.string.fail_response);
        }
    }
    private void gotFreeSpace(long freeSpace){
        if(freeSpace == 0){
            main_available.setText("");
            return;
        }
        main_available.setText(bytesToHuman(freeSpace));
    }
    private String bytesToHuman(long bytes){
        long GB = 1073741824;
        long MB = 1048576;
        long KB = 1024;
        if(bytes > GB*10){
            double gbs = (double) bytes / GB;
            return String.format(Locale.getDefault(), "%.0f GB", gbs);
        }else if(bytes > GB*5){
            double gbs = (double) bytes/GB;
            return String.format(Locale.getDefault(), "%.3f GB", gbs);
        }else if(bytes > MB){
            double gbs = (double) bytes/MB;
            return String.format(Locale.getDefault(), "%.0f MB", gbs);
        }else if(bytes > KB){
            double gbs = (double) bytes/KB;
            return String.format(Locale.getDefault(), "%.0f KB", gbs);
        }
        return bytes + "B";
    }
}
