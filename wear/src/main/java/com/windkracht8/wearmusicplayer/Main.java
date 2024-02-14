package com.windkracht8.wearmusicplayer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.splashscreen.SplashScreen;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends Activity{
    static final String LOG_TAG = "WearMusicPlayer";
    static final String INTENT_ACTION = "com.windkracht8.wearmusicplayer";
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
    private Progress main_progress;
    private MenuLibrary main_menu_library;
    MenuArtists main_menu_artists;
    MenuArtist main_menu_artist;
    MenuAlbums main_menu_albums;
    MenuAlbum main_menu_album;
    private View currentVisibleView;

    ExecutorService executorService;
    private AudioManager audioManager;
    private BroadcastReceiver broadcastReceiver;

    static int heightPixels;
    private static int widthPixels;
    static int vh25;
    static int vw20;
    static int vh75;

    private static float onTouchStartY = -1;
    private static float onTouchStartX = 0;
    private static int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 50;

    final static Library library = new Library();
    static final W8Player player = new W8Player();
    private static final ForegroundService service = new ForegroundService();
    private CommsBT commsBT = null;
    private ArrayList<Library.Track> current_tracks;
    private int current_index;
    private boolean isPlaying = false;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")//registerReceiver is wrapped in SDK_INT, still complains
    @Override
    protected void onCreate(Bundle savedInstanceState){
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> showSplash);
        super.onCreate(savedInstanceState);
        isScreenRound = getResources().getConfiguration().isScreenRound();
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        heightPixels = displayMetrics.heightPixels;
        widthPixels = displayMetrics.widthPixels;
        SWIPE_THRESHOLD = widthPixels / 3;
        vh25 = (int) (heightPixels * .25);
        vw20 = (int) (widthPixels * .2);
        vh75 = (int) (heightPixels * .75);

        executorService = Executors.newFixedThreadPool(4);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        broadcastReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent){
                if(!intent.hasExtra("intent_type")){return;}
                switch(Objects.requireNonNull(intent.getStringExtra("intent_type"))){
                    case "onIsPlayingChanged":
                        if(!intent.hasExtra("isPlaying")){return;}
                        onIsPlayingChanged(intent.getBooleanExtra("isPlaying", false));
                        break;
                    case "onPlayerEnded":
                        onPlayerEnded();
                        break;
                    case "bPreviousPressed":
                        bPreviousPressed();
                        break;
                    case "bNextPressed":
                        bNextPressed();
                        break;
                    case "onProgressChanged":
                        if(!intent.hasExtra("currentPosition")){return;}
                        onProgressChanged(intent.getLongExtra("currentPosition", 0));
                        break;
                }
            }
        };
        if(Build.VERSION.SDK_INT >= 33){
            registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION), Context.RECEIVER_NOT_EXPORTED);
        }else{
            registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION));
        }

        player.init(getApplicationContext());

        setContentView(R.layout.main);
        main_progress = findViewById(R.id.main_progress);
        main_menu_library = findViewById(R.id.main_menu_library);
        main_menu_artists = findViewById(R.id.main_menu_artists);
        main_menu_artist = findViewById(R.id.main_menu_artist);
        main_menu_albums = findViewById(R.id.main_menu_albums);
        main_menu_album = findViewById(R.id.main_menu_album);
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
        main_previous.setOnClickListener(v -> bPreviousPressed());
        main_play_pause.setOnClickListener(v -> bPlayPausePressed());
        main_next.setOnClickListener(v -> bNextPressed());
        findViewById(R.id.main_library).setOnClickListener(v -> main_menu_library.show(this));

        // We need to listen for touch on all objects that have a click listener
        int[] ids = new int[]{R.id.main, R.id.main_timer, R.id.main_volume_down, R.id.main_volume_up,
            R.id.main_previous, R.id.main_play_pause, R.id.main_next, R.id.main_song_title,
            R.id.main_song_artist, R.id.main_library
        };
        for(int id : ids){
            findViewById(id).setOnTouchListener(this::onTouch);
        }
        findViewById(R.id.main).setOnClickListener(v -> onMainClick());

        if(heightPixels < 68 * displayMetrics.scaledDensity + 144 * displayMetrics.density){
            main_song_title.setLines(1);
        }

        commsBT = new CommsBT(this);
        requestPermissions();
        executorService.submit(() -> library.scanMediaStore(this));
        initBT();
        showSplash = false;
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
        commsBT.stopComms();
    }
    private void requestPermissions(){
        if(Build.VERSION.SDK_INT >= 33){
            hasReadPermission = hasPermission(Manifest.permission.READ_MEDIA_AUDIO);
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    && hasPermission(Manifest.permission.BLUETOOTH_SCAN);
            if(!hasReadPermission
                    || !hasBTPermission
                    || !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            ){
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.POST_NOTIFICATIONS
                        ,Manifest.permission.READ_MEDIA_AUDIO
                        ,Manifest.permission.BLUETOOTH_CONNECT
                        ,Manifest.permission.BLUETOOTH_SCAN}, 1);
            }
        }else if(Build.VERSION.SDK_INT >= 31){
            hasReadPermission = hasPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    && hasPermission(Manifest.permission.BLUETOOTH_SCAN);
            if(!hasReadPermission || !hasBTPermission
            ){
                ActivityCompat.requestPermissions(this, new String[]{
                                Manifest.permission.MANAGE_EXTERNAL_STORAGE
                                ,Manifest.permission.BLUETOOTH_CONNECT
                                ,Manifest.permission.BLUETOOTH_SCAN}, 1);
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
                    executorService.submit(() -> library.scanMediaStore(this));
                }
                break;
            }
        }
        for(int i=0; i<permissions.length; i++){
            if(permissions[i].equals(Manifest.permission.BLUETOOTH_CONNECT) ||
                    permissions[i].equals(Manifest.permission.BLUETOOTH_SCAN) ||
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
        if(!hasBTPermission) return;
        executorService.submit(()-> commsBT.startComms());
    }

    void toast(int message){
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
    void commsFileStart(String path){
        Log.d(LOG_TAG, "commsFileStart " + path);
        runOnUiThread(()-> main_progress.show(path));
    }
    void commsProgress(int progress){
        runOnUiThread(()-> main_progress.setProgress(progress));
    }
    void commsConnectionInfo(int value){
        runOnUiThread(()-> main_progress.setConnectionInfo(value));
    }
    void commsFileDone(String path){
        Log.d(LOG_TAG, "commsFileDone " + path);
        executorService.submit(()-> library.addFile(this, path));
        runOnUiThread(()-> main_progress.setVisibility(View.GONE));
        executorService.submit(()-> commsBT.sendFileBinaryResponse(path));
    }
    void commsFileFailed(String path){
        executorService.submit(()-> library.deleteFile(this, path));
        runOnUiThread(()-> main_progress.setVisibility(View.GONE));
        executorService.submit(()-> commsBT.sendResponse("fileBinary", "failed"));
    }
    void libraryReady(){
        runOnUiThread(()-> main_library.setText(R.string.library));
        if(current_tracks == null){
            current_tracks = library.tracks;
            loadTrack(0);
        }
    }

    void openTrackList(ArrayList<Library.Track> tracks, int index){
        current_tracks = tracks;
        main_play_pause.setImageResource(R.drawable.icon_pause);
        main_menu_library.setVisibility(View.GONE);
        main_menu_artists.setVisibility(View.GONE);
        main_menu_artist.setVisibility(View.GONE);
        main_menu_albums.setVisibility(View.GONE);
        main_menu_album.setVisibility(View.GONE);
        playTrack(index);
    }
    private void playTrack(int index){
        if(current_tracks == null ||
                current_tracks.size() == 0 ||
                current_tracks.size() < index ||
                index < 0) return;
        loadTrackUi(index);
        sendIntent(getApplicationContext(), "player.playTrack", current_tracks.get(index).uri);
    }
    private void loadTrack(int index){
        if(current_tracks == null ||
                current_tracks.size() == 0 ||
                current_tracks.size() <= index ||
                index < 0) return;
        runOnUiThread(()-> loadTrackUi(index));
        sendIntent(getApplicationContext(), "player.loadTrack", current_tracks.get(index).uri);
    }
    private void loadTrackUi(int index){
        if(current_tracks == null ||
                current_tracks.size() == 0 ||
                current_tracks.size() <= index ||
                index < 0) return;
        current_index = index;
        Library.Track track = current_tracks.get(current_index);
        main_song_title.setText(track.title);
        main_song_artist.setText(track.artist.name);
        if(hasPrevious()){
            main_previous.setColorFilter(getColor(R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);
        }else{
            main_previous.setColorFilter(getColor(R.color.icon_disabled), android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if(hasNext()){
            main_next.setColorFilter(getColor(R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);
        }else{
            main_next.setColorFilter(getColor(R.color.icon_disabled), android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }
    void onRescanClick(){
        librarySetScanning();
        main_menu_library.setVisibility(View.GONE);
        executorService.submit(() -> library.scanFiles(this));
    }
    private void librarySetScanning(){
        if(!isPlaying){
            runOnUiThread(()->{
                main_song_title.setText("");
                main_song_artist.setText("");
                main_library.setText(R.string.scanning);
                current_tracks = null;
            });
        }
    }
    private void bPreviousPressed(){
        loadTrack(current_index-1);
    }
    private void bPlayPausePressed(){
        sendIntent(getApplicationContext(), "player.playPause");
    }
    private void bNextPressed(){
        loadTrack(current_index+1);
    }

    private void onIsPlayingChanged(boolean isPlaying){
        if(this.isPlaying == isPlaying) return;
        this.isPlaying = isPlaying;
        if(isPlaying){
            main_play_pause.setImageResource(R.drawable.icon_pause);
            service.start(getApplicationContext());
        }else{
            main_play_pause.setImageResource(R.drawable.icon_play);
            service.stopDelayed(getApplicationContext());
        }
    }
    private void onProgressChanged(long currentPosition){
        main_timer.setText(prettyTimer(currentPosition));
    }
    private void onPlayerEnded(){
        if(hasNext()) playTrack(current_index+1);
    }
    private boolean hasPrevious(){
        return current_index > 0;
    }
    private boolean hasNext(){
        return current_tracks.size() > current_index+1;
    }
    @Override
    public void onBackPressed(){
        View view = getCurrentVisibleView();
        if(view != null){
            view.setVisibility(View.GONE);
            Menu menu = getCurrentVisibleMenu();
            if(menu != null){
                menu.requestSVFocus();
            }
        }else{
            if(isPlaying){
                AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.wmp_alert));
                builder.setMessage(R.string.confirm_close);
                builder.setPositiveButton(R.string.yes, (dialog, which) -> exit());
                builder.setNegativeButton(R.string.back, (dialog, which) -> dialog.dismiss());
                builder.create().show();
            }else{
                exit();
            }
        }
    }
    private void exit(){
        sendIntent(getApplicationContext(), "player.exit");
        service.stop(getApplicationContext());
        System.exit(0);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev){
        super.dispatchGenericMotionEvent(ev);
        return true; //Just to let Google know we are listening to rotary events
    }

    private void onMainClick(){
        //We need to do this to make sure that we can listen for onTouch on main
        Log.i(LOG_TAG, "onMainClick");
    }

    void addOnTouch(View v){
        v.setOnTouchListener(this::onTouch);
    }
    private boolean onTouch(View ignoredV, MotionEvent event){
        switch(event.getAction()){
            case MotionEvent.ACTION_DOWN:
                onTouchInit(event);
                super.onTouchEvent(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if(onTouchStartY == -1) onTouchInit(event);
                if(currentVisibleView == null) return false;

                int diffX1 = getBackSwipeDiffX(event);
                if(getBackSwipeVelocity(event, diffX1) < SWIPE_VELOCITY_THRESHOLD){
                    currentVisibleView.animate()
                            .x(0)
                            .scaleX(1f).scaleY(1f)
                            .setDuration(300).start();
                }else if(diffX1 > 0){
                    float move = event.getRawX() - onTouchStartX;
                    float scale = 1 - move/widthPixels;
                    if(isScreenRound){
                        currentVisibleView.setBackgroundResource(R.drawable.round_bg);
                    }
                    currentVisibleView.animate().x(move)
                            .scaleX(scale).scaleY(scale)
                            .setDuration(0).start();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(currentVisibleView != null){
                    currentVisibleView.animate()
                            .x(0)
                            .scaleX(1f).scaleY(1f)
                            .setDuration(150).start();
                    if(isScreenRound){
                        currentVisibleView.setBackgroundResource(0);
                        currentVisibleView.setBackgroundColor(getResources().getColor(R.color.black, null));
                    }
                }
                int diffX2 = getBackSwipeDiffX(event);
                float velocity2 = getBackSwipeVelocity(event, diffX2);
                onTouchStartY = -1;
                if(diffX2 > SWIPE_THRESHOLD && velocity2 > SWIPE_VELOCITY_THRESHOLD){
                    onBackPressed();
                    return true;
                }
        }
        return false;
    }
    private void onTouchInit(MotionEvent event){
        onTouchStartY = event.getRawY();
        onTouchStartX = event.getRawX();
        currentVisibleView = getCurrentVisibleView();
    }
    private View getCurrentVisibleView(){
        if(main_progress.getVisibility() == View.VISIBLE){
            return main_progress;
        }
        return getCurrentVisibleMenu();
    }
    private Menu getCurrentVisibleMenu(){
        if(main_menu_album.getVisibility() == View.VISIBLE){
            return main_menu_album;
        }else if(main_menu_albums.getVisibility() == View.VISIBLE){
            return main_menu_albums;
        }else if(main_menu_artist.getVisibility() == View.VISIBLE){
            return main_menu_artist;
        }else if(main_menu_artists.getVisibility() == View.VISIBLE){
            return main_menu_artists;
        }else if(main_menu_library.getVisibility() == View.VISIBLE){
            return main_menu_library;
        }
        return null;
    }
    private int getBackSwipeDiffX(MotionEvent event){
        float diffY = event.getRawY() - onTouchStartY;
        float diffX = event.getRawX() - onTouchStartX;
        if(diffX > 0 && Math.abs(diffX) > Math.abs(diffY)) return Math.round(diffX);
        return -1;
    }
    private float getBackSwipeVelocity(MotionEvent event, float diffX){
        return (diffX / (event.getEventTime() - event.getDownTime())) * 1000;
    }
    private static String prettyTimer(long milli_secs){
        long tmp = milli_secs % 1000;
        long secs = (milli_secs - tmp) / 1000;
        tmp = secs % 60;
        long minutes = (secs - tmp) / 60;

        String pretty = Long.toString(tmp);
        if(tmp < 10){pretty = "0" + pretty;}
        pretty = minutes + ":" + pretty;

        return pretty;
    }
    static void sendIntent(Context context, String intent_type){
        Intent intent = new Intent(Main.INTENT_ACTION);
        intent.putExtra("intent_type", intent_type);
        context.sendBroadcast(intent);
    }
    static void sendIntentProgressChanged(Context context, long extra_value){
        Intent intent = new Intent(Main.INTENT_ACTION);
        intent.putExtra("intent_type", "onProgressChanged");
        intent.putExtra("currentPosition", extra_value);
        context.sendBroadcast(intent);
    }
    static void sendIntentIsPlayingChanged(Context context, boolean extra_value){
        Intent intent = new Intent(Main.INTENT_ACTION);
        intent.putExtra("intent_type", "onIsPlayingChanged");
        intent.putExtra("isPlaying", extra_value);
        context.sendBroadcast(intent);
    }
    private static void sendIntent(Context context, String intent_type, Uri extra_value){
        Intent intent = new Intent(Main.INTENT_ACTION);
        intent.putExtra("intent_type", intent_type);
        intent.putExtra("uri", extra_value);
        context.sendBroadcast(intent);
    }
}
