package com.windkracht8.wearmusicplayer;

import android.Manifest;
import android.app.Activity;
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
import android.view.KeyEvent;
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
    static boolean isMenuVisible = false;
    private static boolean hasBTPermission = false;
    private static boolean hasReadPermission = false;
    private TextView main_timer;
    private ImageView main_previous;
    private ImageView main_play_pause;
    private ImageView main_next;
    private TextView main_song_title;
    private TextView main_song_artist;
    private TextView main_library;
    private ImageView main_loading;
    private Progress main_progress;

    private ExecutorService executorService;
    private Handler handler;
    private GestureDetector gestureDetector;
    private AudioManager audioManager;
    private MediaController mediaController;

    static int heightPixels;
    static int vh25;
    static int vw20;
    static int vh75;

    static Library library;
    private CommsBT commsBT;

    static ArrayList<Library.Track> openTrackList = new ArrayList<>();
    static int openTrackListTrack = -1;
    static boolean doRescan = false;

    @Override protected void onCreate(Bundle savedInstanceState){
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(()->showSplash);
        super.onCreate(savedInstanceState);
        isScreenRound = getResources().getConfiguration().isScreenRound();
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        heightPixels = displayMetrics.heightPixels;
        int widthPixels = displayMetrics.widthPixels;
        vh25 = (int) (heightPixels * .25);
        vw20 = (int) (widthPixels * .2);
        vh75 = (int) (heightPixels * .75);

        handler = new Handler(Looper.getMainLooper());
        gestureDetector = new GestureDetector(this, simpleOnGestureListener, handler);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        library = new Library(this);

        setContentView(R.layout.main);
        main_progress = findViewById(R.id.main_progress);
        main_loading = findViewById(R.id.main_loading);
        main_timer = findViewById(R.id.main_timer);
        main_previous = findViewById(R.id.main_previous);
        main_play_pause = findViewById(R.id.main_play_pause);
        main_next = findViewById(R.id.main_next);
        main_song_title = findViewById(R.id.main_song_title);
        main_song_artist = findViewById(R.id.main_song_artist);
        main_library = findViewById(R.id.main_library);

        findViewById(R.id.main_volume_down).setOnClickListener(v->
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        );
        findViewById(R.id.main_volume_up).setOnClickListener(v->
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        );
        main_previous.setOnClickListener(v->{if(mediaController != null) mediaController.seekToPrevious();});
        main_play_pause.setOnClickListener(v->{
            if(mediaController == null) return;
            if(mediaController.isPlaying()){
                main_play_pause.setImageResource(R.drawable.icon_pause);
                mediaController.pause();
            }else{
                main_play_pause.setImageResource(R.drawable.icon_play);
                mediaController.play();
            }
        });
        main_next.setOnClickListener(v->{if(mediaController != null) mediaController.seekToNext();});
        main_library.setOnClickListener(v->{
            main_loading.setVisibility(View.VISIBLE);
            ((AnimatedVectorDrawable) main_loading.getBackground()).start();
            startActivity(new Intent(this, MenuActivity.class));
        });

        if(heightPixels < getResources().getDimensionPixelSize(R.dimen._200dp)) main_song_title.setLines(1);

        requestPermissions();
        if(hasReadPermission) runInBackground(library::scanMediaStore);
        commsBT = new CommsBT(this);
        if(hasBTPermission) runInBackground(commsBT::startBT);
        showSplash = false;
    }
    @Override public void onRestart(){
        super.onRestart();
        if(doRescan){
            runInBackground(library::scanFiles);
            doRescan = false;
        }else if(openTrackListTrack > -1){
            runInBackground(()->loadTracks(openTrackList));
        }
    }
    @Override public void onStart(){
        super.onStart();
        main_loading.setVisibility(View.GONE);
        if(mediaController == null){
            ListenableFuture<MediaController> controllerFuture =
                    new MediaController.Builder(this,
                            new SessionToken(this,
                                    new ComponentName(this, W8Player.class))).buildAsync();
            controllerFuture.addListener(()->{
                try{
                    mediaController = controllerFuture.get();
                    mediaController.addListener(playerListener);
                    if(mediaController.getMediaItemCount() == 0)
                        runInBackground(()->loadTracks(library.tracks));
                }catch(Exception e){
                    Log.e(LOG_TAG, "MediaController exception: " + e.getMessage());
                }
            }, MoreExecutors.directExecutor());
        }
        if(hasBTPermission) runInBackground(()->commsBT.startBT());
    }
    private final Player.Listener playerListener = new Player.Listener(){
        @Override public void onIsPlayingChanged(boolean isPlaying){
            Log.d(LOG_TAG, "Main.onIsPlayingChanged " + isPlaying);
            if(isPlaying){
                main_play_pause.setImageResource(R.drawable.icon_pause);
                updateTimer.run();
            }else{
                main_play_pause.setImageResource(R.drawable.icon_play);
                handler.removeCallbacksAndMessages(null);
            }
        }
        @Override public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata){
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
        @Override public void onPlayerError(PlaybackException error){
            Log.e(LOG_TAG, "Main.onPlayerError: " + error);
            Log.e(LOG_TAG, "Main.onPlayerError: " + error.getMessage());
        }
    };
    @Override public void onDestroy(){
        super.onDestroy();
        runInBackground(()->{
            if(commsBT != null) commsBT.stopBT();
            commsBT = null;
        });
        if(mediaController != null){
            mediaController.removeListener(playerListener);
            mediaController.release();
        }
        if(executorService != null){
            executorService.shutdownNow();
        }
        System.exit(0);
    }

    private void loadTracks(ArrayList<Library.Track> tracks){
        if(mediaController == null || tracks.isEmpty()) return;
        Log.d(Main.LOG_TAG, "Main.loadTracks: " + tracks.size());
        ArrayList<MediaItem> mediaItems = new ArrayList<>();
        for(int i=0; i<tracks.size(); i++) mediaItems.add(MediaItem.fromUri(tracks.get(i).uri));
        runOnUiThread(()->loadMediaItems(mediaItems));
    }
    private void loadMediaItems(ArrayList<MediaItem> mediaItems){
        if(mediaController == null || mediaItems.isEmpty()) return;
        Log.d(Main.LOG_TAG, "Main.loadMediaItems: " + mediaItems.size());
        try{
            mediaController.clearMediaItems();
            mediaController.addMediaItems(mediaItems);
            mediaController.prepare();
            if(openTrackListTrack > -1){
                mediaController.seekTo(openTrackListTrack, 0);
                mediaController.play();
                openTrackListTrack = -1;
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Main.loadMediaItems exception: " + e.getMessage());
            Toast.makeText(getBaseContext(), R.string.fail_load_tracks, Toast.LENGTH_SHORT).show();
        }
    }
    private final Runnable updateTimer = new Runnable(){@Override public void run(){
        if(mediaController == null || !mediaController.isPlaying()) return;
        long pos = mediaController.getCurrentPosition();

        long tmp = pos % 1000;
        long secs = (pos - tmp) / 1000;
        tmp = secs % 60;
        long minutes = (secs - tmp) / 60;
        String pretty = Long.toString(tmp);
        if(tmp < 10){pretty = "0" + pretty;}
        pretty = minutes + ":" + pretty;

        main_timer.setText(pretty);
        handler.postDelayed(updateTimer, 1000-(pos%1000));
    }};

    void librarySetScanning(){
        Log.d(LOG_TAG, "Main.librarySetScanning");
        runOnUiThread(()->{
            if(mediaController.isPlaying()) return;
            main_song_title.setText("");
            main_song_artist.setText("");
            main_library.setBackgroundResource(0);
            main_library.setText(R.string.scanning);
            mediaController.clearMediaItems();
            main_previous.setColorFilter(getColor(R.color.icon_disabled));
            main_next.setColorFilter(getColor(R.color.icon_disabled));
        });
    }
    void libraryReady(){
        runOnUiThread(()->{
            if(mediaController != null && mediaController.isPlaying()) return;
            runInBackground(()->loadTracks(library.tracks));
            if(!library.tracks.isEmpty()){
                Library.Track track = library.tracks.get(0);
                main_song_title.setText(track.title);
                main_song_artist.setText(track.artist.name);
                if(library.tracks.size()>1){
                    main_next.setColorFilter(getColor(R.color.white));
                }
            }
            main_library.setText("");
            main_library.setBackgroundResource(R.drawable.icon_library);
        });
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
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for(int i=0; i<permissions.length; i++){
            if(permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE)
                    || permissions[i].equals(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                    || permissions[i].equals(Manifest.permission.READ_MEDIA_AUDIO)
            ){
                if(grantResults[i] == PackageManager.PERMISSION_GRANTED){
                    hasReadPermission = true;
                    runInBackground(()->library.scanMediaStore());
                }
                break;
            }
        }
        for(int i=0; i<permissions.length; i++){
            if(permissions[i].equals(Manifest.permission.BLUETOOTH_CONNECT) ||
                    permissions[i].equals(Manifest.permission.BLUETOOTH)){
                if(grantResults[i] == PackageManager.PERMISSION_GRANTED){
                    hasBTPermission = true;
                    runInBackground(()->commsBT.startBT());
                }else{
                    hasBTPermission = false;
                }
                break;
            }
        }
    }

    void toast(int message){
        runOnUiThread(()->Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
    private void runInBackground(Runnable runnable){
        if(executorService == null) executorService = Executors.newCachedThreadPool();
        executorService.execute(runnable);
    }

    void commsFileStart(String path){
        Log.d(LOG_TAG, "Main.commsFileStart " + path);
        runOnUiThread(()->{
            Log.d(LOG_TAG, "isMenuVisible: " + isMenuVisible);
            main_progress.show(path);
            if(isMenuVisible)
                startActivity((new Intent(this, MenuActivity.class)).putExtra("close", true));
        });
    }
    void commsProgress(int progress){runOnUiThread(()->main_progress.setProgress(progress));}
    void commsConnectionInfo(int value){runOnUiThread(()->main_progress.setConnectionInfo(value));}
    void commsFileDone(String path){
        Log.d(LOG_TAG, "Main.commsFileDone " + path);
        runInBackground(()->library.addFile(path));
        runOnUiThread(()->main_progress.setVisibility(View.GONE));
        runInBackground(()->commsBT.sendFileBinaryResponse(path));
    }
    void commsFileFailed(String path, int reason){
        runInBackground(()->library.deleteFile(path));
        runOnUiThread(()->main_progress.setVisibility(View.GONE));
        runInBackground(()->commsBT.sendResponse("fileBinary", getString(reason)));
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 5){
            if(resultCode == 0){
                Log.i(Main.LOG_TAG, "Main.onActivityResult: file delete permission denied");
                runInBackground(()->commsBT.sendResponse(
                        "deleteFile"
                        ,getString(R.string.fail_no_permission))
                );
            }else{
                runInBackground(()->commsBT.sendResponse(
                        "deleteFile"
                        ,"OK")
                );
                runInBackground(()->{
                    if(Library.filePendingDelete != null)
                        library.scanFile(Library.filePendingDelete.toString());
                });
            }
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event){
        if(keyCode == KeyEvent.KEYCODE_BACK){
            onBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    private void onBack(){
        if(CommsWifi.isReceiving){
            AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.wmp_alert));
            builder.setMessage(R.string.confirm_close_transfer);
            builder.setPositiveButton(R.string.yes, (d, w)->finish());
            builder.setNegativeButton(R.string.back, (d, w)->d.dismiss());
            builder.create().show();
        }else if(mediaController != null && mediaController.isPlaying()){
            AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.wmp_alert));
            builder.setMessage(R.string.confirm_close_play);
            builder.setPositiveButton(R.string.yes, (d, w)->finish());
            builder.setNegativeButton(R.string.back, (d, w)->d.dismiss());
            builder.create().show();
        }else{
            finish();
        }
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent ev){
        super.dispatchGenericMotionEvent(ev);
        return true; //Just to let Google know we are listening to rotary events
    }
    @Override public boolean onTouchEvent(MotionEvent event){return gestureDetector.onTouchEvent(event);}
    private final GestureDetector.SimpleOnGestureListener simpleOnGestureListener = new GestureDetector.SimpleOnGestureListener(){
        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY){
            if(Math.abs(velocityX) < Math.abs(velocityY)) return false;
            if(velocityX > 0) onBack();
            return true;
        }
    };
}
