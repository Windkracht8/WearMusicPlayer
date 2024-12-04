package com.windkracht8.wearmusicplayer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends Activity{
    static final String LOG_TAG = "WearMusicPlayer";
    static boolean isScreenRound;
    private boolean showSplash = true;
    private boolean hasBTPermission = false;
    static boolean hasReadPermission = false;
    private TextView main_timer;
    private ImageView main_previous;
    private ImageView main_play_pause;
    private ImageView main_next;
    private TextView main_song_title;
    private TextView main_song_artist;
    private TextView main_library;
    private ImageView main_loading;
    private Progress main_progress;

    static ExecutorService executorService;
    private Handler handler;
    private GestureDetector gestureDetector;
    private AudioManager audioManager;
    private static MediaController mediaController;

    static int heightPixels;
    static int vh25;
    static int vw20;
    static int vh75;

    static Library library;
    private CommsBT commsBT;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")//registerReceiver is wrapped in SDK_INT, still complains
    @Override
    protected void onCreate(Bundle savedInstanceState){
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> showSplash);
        super.onCreate(savedInstanceState);
        isScreenRound = getResources().getConfiguration().isScreenRound();
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        heightPixels = displayMetrics.heightPixels;
        int widthPixels = displayMetrics.widthPixels;
        vh25 = (int) (heightPixels * .25);
        vw20 = (int) (widthPixels * .2);
        vh75 = (int) (heightPixels * .75);

        executorService = Executors.newFixedThreadPool(4);
        handler = new Handler(Looper.getMainLooper());
        gestureDetector = new GestureDetector(this, simpleOnGestureListener, handler);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        library = new Library(this);

        setContentView(R.layout.main);
        main_progress = findViewById(R.id.main_progress);
        main_loading = findViewById(R.id.main_loading);
        main_loading.setBackgroundResource(R.drawable.icon_animate);
        main_timer = findViewById(R.id.main_timer);
        main_previous = findViewById(R.id.main_previous);
        main_play_pause = findViewById(R.id.main_play_pause);
        main_next = findViewById(R.id.main_next);
        main_song_title = findViewById(R.id.main_song_title);
        main_song_artist = findViewById(R.id.main_song_artist);
        main_library = findViewById(R.id.main_library);

        findViewById(R.id.main_volume_down).setOnClickListener(v ->
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        );
        findViewById(R.id.main_volume_up).setOnClickListener(v ->
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        );
        main_previous.setOnClickListener(v -> mediaController.seekToPrevious());
        main_play_pause.setOnClickListener(v -> {
            if(mediaController.isPlaying()){
                main_play_pause.setImageResource(R.drawable.icon_pause);
                mediaController.pause();
            }else{
                main_play_pause.setImageResource(R.drawable.icon_play);
                mediaController.play();
            }
        });
        main_next.setOnClickListener(v -> mediaController.seekToNext());
        main_library.setOnClickListener(v->{
            main_loading.setVisibility(View.VISIBLE);
            ((AnimatedVectorDrawable) main_loading.getBackground()).start();
            startActivity(new Intent(this, MenuActivity.class));
        });

        //TODO: scaledDensity in DisplayMetrics has been deprecated
        if(heightPixels < 68 * displayMetrics.scaledDensity + 144 * displayMetrics.density){
            main_song_title.setLines(1);
        }

        commsBT = new CommsBT(this);
        requestPermissions();
        executorService.submit(() -> library.scanMediaStore());
        initBT();
        showSplash = false;
    }
    @Override
    public void onStart(){
        super.onStart();
        main_loading.setVisibility(View.GONE);
        ComponentName cn = new ComponentName(this, W8Player.class);
        SessionToken st = new SessionToken(this, cn);
        ListenableFuture<MediaController> controllerFuture = new MediaController.Builder(this, st).buildAsync();
        controllerFuture.addListener(()-> {
            Log.d(LOG_TAG, "MediaController is ready");
            try{
                mediaController = controllerFuture.get();
                mediaController.addListener(playerListener);
                if(mediaController.getMediaItemCount() == 0)
                    loadTracks(this, library.tracks);
            }catch(Exception e){
                Log.e(LOG_TAG, "Main.onStart exception: " + e.getMessage());
            }
        },MoreExecutors.directExecutor());
    }

    private final Player.Listener playerListener = new Player.Listener(){
        @Override
        public void onIsPlayingChanged(boolean isPlaying){
            Log.d(LOG_TAG, "Main.onIsPlayingChanged " + isPlaying);
            if(isPlaying){
                main_play_pause.setImageResource(R.drawable.icon_pause);
                updateTimer();
            }else{
                main_play_pause.setImageResource(R.drawable.icon_play);
            }
        }
        @Override
        public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata){
            Log.d(LOG_TAG, "Main.onMediaMetadataChanged " + mediaMetadata.title);
            if(mediaMetadata.title == null) return;
            main_song_title.setText(mediaMetadata.title);
            main_song_artist.setText(mediaMetadata.artist);
            if(mediaController.hasPreviousMediaItem()){
                main_previous.setColorFilter(getColor(R.color.white));
            }else{
                main_previous.setColorFilter(getColor(R.color.icon_disabled));
            }
            if(mediaController.hasNextMediaItem()){
                main_next.setColorFilter(getColor(R.color.white));
            }else{
                main_next.setColorFilter(getColor(R.color.icon_disabled));
            }
        }
        @Override
        public void onPlayerError(PlaybackException error){
            Log.d(LOG_TAG, "Main.onPlayerError: " + error);
            Log.d(LOG_TAG, "Main.onPlayerError: " + error.getMessage());
        }
    };

    static void openTrackList(Context context, ArrayList<Library.Track> tracks, int index){
        loadTracks(context, tracks);
        mediaController.seekTo(index, 0);
        mediaController.play();
    }
    private static void loadTracks(Context context, ArrayList<Library.Track> tracks){
        if(mediaController == null || tracks.isEmpty()) return;
        Log.d(Main.LOG_TAG, "Main.loadTracks: " + tracks.size());
        mediaController.clearMediaItems();
        try{
            for(Library.Track track : tracks){
                mediaController.addMediaItem(MediaItem.fromUri(track.uri));
            }
            mediaController.prepare();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Main.loadTracks exception: " + e.getMessage());
            Toast.makeText(context, R.string.fail_load_tracks, Toast.LENGTH_SHORT).show();
        }
    }
    private void updateTimer(){
        if(!mediaController.isPlaying()) return;
        long pos = mediaController.getCurrentPosition();

        long tmp = pos % 1000;
        long secs = (pos - tmp) / 1000;
        tmp = secs % 60;
        long minutes = (secs - tmp) / 60;
        String pretty = Long.toString(tmp);
        if(tmp < 10){pretty = "0" + pretty;}
        pretty = minutes + ":" + pretty;

        main_timer.setText(pretty);
        handler.postDelayed(this::updateTimer, 1000-(pos%1000));
    }

    static void rescan(){
        executorService.submit(() -> library.scanFiles());
    }
    void librarySetScanning(){
        Log.d(LOG_TAG, "Main.librarySetScanning");
        runOnUiThread(()-> {
            if(mediaController.isPlaying()) return;
            main_song_title.setText("");
            main_song_artist.setText("");
            main_library.setText(R.string.scanning);
            mediaController.clearMediaItems();
            main_previous.setColorFilter(getColor(R.color.icon_disabled));
            main_next.setColorFilter(getColor(R.color.icon_disabled));
        });
    }
    void libraryReady(){
        runOnUiThread(()-> {
            if(mediaController != null && mediaController.isPlaying()) return;
            loadTracks(this, library.tracks);
            if(!library.tracks.isEmpty()){
                Library.Track track = library.tracks.get(0);
                main_song_title.setText(track.title);
                main_song_artist.setText(track.artist.name);
                if(library.tracks.size()>1){
                    main_next.setColorFilter(getColor(R.color.white));
                }
            }
            main_library.setText(getString(R.string.library).toLowerCase());
        });
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        commsBT.stopComms();
        mediaController.release();
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
    }
    private void requestPermissions(){
        if(Build.VERSION.SDK_INT >= 33){
            hasReadPermission = hasPermission(Manifest.permission.READ_MEDIA_AUDIO);
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH_CONNECT);
            if(!hasReadPermission
                    || !hasBTPermission
                    || !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            ){
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.POST_NOTIFICATIONS
                        ,Manifest.permission.READ_MEDIA_AUDIO
                        ,Manifest.permission.BLUETOOTH_CONNECT}, 1);
            }
        }else if(Build.VERSION.SDK_INT >= 31){
            hasReadPermission = hasPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH_CONNECT);
            if(!hasReadPermission || !hasBTPermission
            ){
                ActivityCompat.requestPermissions(this, new String[]{
                                Manifest.permission.MANAGE_EXTERNAL_STORAGE
                                ,Manifest.permission.BLUETOOTH_CONNECT}, 1);
            }
        }else{//30
            hasReadPermission = hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH);
            if(!hasReadPermission || !hasBTPermission
            ){
                ActivityCompat.requestPermissions(this, new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE
                                ,Manifest.permission.BLUETOOTH}, 1);
            }
        }
    }
    private boolean hasPermission(String permission){
        return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for(int i=0; i<permissions.length; i++){
            if(permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE)
                    || permissions[i].equals(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                    || permissions[i].equals(Manifest.permission.READ_MEDIA_AUDIO)
            ){
                if(grantResults[i] == PackageManager.PERMISSION_GRANTED){
                    hasReadPermission = true;
                    executorService.submit(() -> library.scanMediaStore());
                }
                break;
            }
        }
        for(int i=0; i<permissions.length; i++){
            if(permissions[i].equals(Manifest.permission.BLUETOOTH_CONNECT) ||
                    permissions[i].equals(Manifest.permission.BLUETOOTH)){
                if(grantResults[i] == PackageManager.PERMISSION_GRANTED){
                    hasBTPermission = true;
                    initBT();
                }else{
                    hasBTPermission = false;
                }
                break;
            }
        }
    }
    private void initBT(){
        Log.d(LOG_TAG, "Main.initBT " + hasBTPermission);
        if(!hasBTPermission) return;
        executorService.submit(()-> commsBT.startComms());
    }

    void toast(int message){
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
    void commsFileStart(String path){
        Log.d(LOG_TAG, "Main.commsFileStart " + path);
        runOnUiThread(()-> main_progress.show(path));
    }
    void commsProgress(int progress){
        runOnUiThread(()-> main_progress.setProgress(progress));
    }
    void commsConnectionInfo(int value){
        runOnUiThread(()-> main_progress.setConnectionInfo(value));
    }
    void commsFileDone(String path){
        Log.d(LOG_TAG, "Main.commsFileDone " + path);
        executorService.submit(()-> library.addFile(path));
        runOnUiThread(()-> main_progress.setVisibility(View.GONE));
        executorService.submit(()-> commsBT.sendFileBinaryResponse(path));
    }
    void commsFileFailed(String path, int reason){
        executorService.submit(()-> library.deleteFile(path));
        runOnUiThread(()-> main_progress.setVisibility(View.GONE));
        executorService.submit(()-> commsBT.sendResponse("fileBinary", getString(reason)));
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 5){
            if(resultCode == 0){
                Log.i(Main.LOG_TAG, "Main.onActivityResult: file delete permission denied");
                executorService.submit(()-> commsBT.sendResponse(
                        "deleteFile"
                        ,getString(R.string.fail_no_permission))
                );
            }else{
                executorService.submit(()-> commsBT.sendResponse(
                        "deleteFile"
                        ,"OK")
                );
                executorService.submit(()-> library.scanFile(Library.filePendingDelete));
            }
        }
    }

    //TODO: onBackPressed() in Activity has been deprecated
    @Override
    public void onBackPressed(){
        if(CommsWifi.isReceiving){
            AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.wmp_alert));
            builder.setMessage(R.string.confirm_close_transfer);
            builder.setPositiveButton(R.string.yes, (dialog, which) -> System.exit(0));
            builder.setNegativeButton(R.string.back, (dialog, which) -> dialog.dismiss());
            builder.create().show();
        }else if(mediaController != null && mediaController.isPlaying()){
            AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.wmp_alert));
            builder.setMessage(R.string.confirm_close_play);
            builder.setPositiveButton(R.string.yes, (dialog, which) -> System.exit(0));
            builder.setNegativeButton(R.string.back, (dialog, which) -> dialog.dismiss());
            builder.create().show();
        }else{
            System.exit(0);
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev){
        super.dispatchGenericMotionEvent(ev);
        return true; //Just to let Google know we are listening to rotary events
    }
    @Override
    public boolean onTouchEvent(MotionEvent event){return gestureDetector.onTouchEvent(event);}
    private final GestureDetector.SimpleOnGestureListener simpleOnGestureListener = new GestureDetector.SimpleOnGestureListener(){
        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY){
            if(Math.abs(velocityX) < Math.abs(velocityY)) return false;
            if(velocityX > 0) onBackPressed();
            return true;
        }
    };
}
