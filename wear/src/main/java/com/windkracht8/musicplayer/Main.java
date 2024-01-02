package com.windkracht8.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.splashscreen.SplashScreen;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends Activity{
    public static final String LOG_TAG = "MusicPlayer";
    public static boolean isScreenRound;
    private boolean showSplash = true;
    private boolean hasBTPermission = false;
    private TextView main_timer;
    private ImageView main_back;
    private ImageView main_play_pause;
    private ImageView main_forward;
    private TextView main_song_title;
    private TextView main_song_artist;
    private Progress main_progress;
    private MenuLibrary main_menu_library;
    public MenuArtists main_menu_artists;
    public MenuArtist main_menu_artist;
    public MenuAlbums main_menu_albums;
    public MenuAlbum main_menu_album;
    private View touchView;

    private ExecutorService executorService;
    private AudioManager audioManager;
    public final static int MESSAGE_TOAST = 101;
    public final static int MESSAGE_PLAYER_COMPLETION = 201;
    public final static int MESSAGE_PLAYER_POSITION = 202;
    public final static int MESSAGE_PLAYER_STOP = 203;
    public final static int MESSAGE_COMMS_FILE_START = 301;
    public final static int MESSAGE_COMMS_FILE_PROGRESS = 302;
    public final static int MESSAGE_COMMS_FILE_DONE = 303;
    public final static int MESSAGE_LIBRARY_READY = 401;
    private final static int REQUEST_PERMISSION_CODE = 100;

    public static int heightPixels;
    public static int widthPixels;
    public static int vh25;
    public static int vh75;

    private static float onTouchStartY = -1;
    private static float onTouchStartX = 0;
    private static int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 50;

    public final static Library library = new Library();
    private final Player player = new Player();
    private CommsBT commsBT = null;
    private ArrayList<Library.Track> current_tracks;
    private int current_index;
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> showSplash);
        super.onCreate(savedInstanceState);
        isScreenRound = getResources().getConfiguration().isScreenRound();
        heightPixels = getWindowManager().getMaximumWindowMetrics().getBounds().height();
        widthPixels = getWindowManager().getMaximumWindowMetrics().getBounds().width();
        SWIPE_THRESHOLD = widthPixels / 3;
        vh25 = (int) (heightPixels * .25);
        vh75 = (int) (heightPixels * .75);

        executorService = Executors.newFixedThreadPool(4);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        setContentView(R.layout.main);

        main_progress = findViewById(R.id.main_progress);
        main_menu_library = findViewById(R.id.main_menu_library);
        main_menu_artists = findViewById(R.id.main_menu_artists);
        main_menu_artist = findViewById(R.id.main_menu_artist);
        main_menu_albums = findViewById(R.id.main_menu_albums);
        main_menu_album = findViewById(R.id.main_menu_album);
        main_timer = findViewById(R.id.main_timer);
        main_back = findViewById(R.id.main_back);
        main_play_pause = findViewById(R.id.main_play_pause);
        main_forward = findViewById(R.id.main_forward);
        main_song_title = findViewById(R.id.main_song_title);
        main_song_artist = findViewById(R.id.main_song_artist);

        findViewById(R.id.main_volume_down).setOnClickListener(v ->
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        );
        findViewById(R.id.main_volume_up).setOnClickListener(v ->
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        );
        main_back.setOnClickListener(v -> bBackPressed());
        main_play_pause.setOnClickListener(v -> bPlayPausePressed());
        main_forward.setOnClickListener(v -> bForwardPressed());
        findViewById(R.id.main_library).setOnClickListener(v -> main_menu_library.show(this));

        // We need to listen for touch on all objects that have a click listener
        int[] ids = new int[]{R.id.main, R.id.main_timer, R.id.main_volume_down, R.id.main_volume_up,
            R.id.main_back, R.id.main_play_pause, R.id.main_forward, R.id.main_song_title,
            R.id.main_song_artist, R.id.main_library
        };
        for(int id : ids){
            findViewById(id).setOnTouchListener(this::onTouch);
        }
        findViewById(R.id.main).setOnClickListener(v -> onMainClick());

        executorService.submit(() -> library.scanMediaStore(this));

        showSplash = false;
        requestPermissions();
        if(hasBTPermission) initBT();
    }
    private void requestPermissions(){
        if(Build.VERSION.SDK_INT >= 33){
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH) &&
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
                    hasPermission(Manifest.permission.BLUETOOTH_SCAN);
            if(!hasPermission(Manifest.permission.POST_NOTIFICATIONS) ||
                    !hasPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE) ||
                    !hasPermission(android.Manifest.permission.BLUETOOTH) ||
                    !hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ||
                    !hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)
            ){
                ActivityCompat.requestPermissions(this, new String[]{
                        android.Manifest.permission.POST_NOTIFICATIONS,
                        android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                        android.Manifest.permission.BLUETOOTH,
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_PERMISSION_CODE);
            }
        }else if(Build.VERSION.SDK_INT >= 31){
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH) &&
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
                    hasPermission(Manifest.permission.BLUETOOTH_SCAN);
            if(!hasPermission(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE) ||
                    !hasPermission(android.Manifest.permission.BLUETOOTH) ||
                    !hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ||
                    !hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)
            ){
                ActivityCompat.requestPermissions(this, new String[]{
                                android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                                android.Manifest.permission.BLUETOOTH,
                                android.Manifest.permission.BLUETOOTH_CONNECT,
                                android.Manifest.permission.BLUETOOTH_SCAN},
                        REQUEST_PERMISSION_CODE);
            }
        }else{//30
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH);
            if(!hasPermission(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE) ||
                    !hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ||
                    !hasPermission(android.Manifest.permission.BLUETOOTH)
            ){
                ActivityCompat.requestPermissions(this, new String[]{
                                android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                android.Manifest.permission.BLUETOOTH},
                        REQUEST_PERMISSION_CODE);
            }
        }
    }
    private boolean hasPermission(String permission){
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
                    return;
                }else{
                    bt_granted = true;
                }
            }
        }
        if(bt_granted){
            hasBTPermission = true;
            initBT();
        }
    }

    private void initBT(){
        if(!hasBTPermission) return;
        if(commsBT == null) commsBT = new CommsBT(this);
        commsBT.connect();
    }

    public final Handler handler_message = new Handler(Looper.getMainLooper()){
        public void handleMessage(Message msg){
            switch(msg.what){
                case MESSAGE_TOAST:
                    if(msg.obj instanceof String){
                        runOnUiThread(() -> Toast.makeText(getBaseContext(), (String) msg.obj, Toast.LENGTH_SHORT).show());
                    }else if(msg.obj instanceof Integer){
                        String msg_str = getString((Integer) msg.obj);
                        runOnUiThread(() -> Toast.makeText(getBaseContext(), msg_str, Toast.LENGTH_SHORT).show());
                    }
                    break;
                case MESSAGE_PLAYER_COMPLETION:
                    play(current_index+1);
                    break;
                case MESSAGE_PLAYER_POSITION:
                    isPlaying = true;
                    main_play_pause.setImageResource(R.drawable.icon_pause);
                    if(!(msg.obj instanceof Integer)) return;
                    main_timer.setText(prettyTimer((Integer) msg.obj));
                    break;
                case MESSAGE_PLAYER_STOP:
                    isPlaying = false;
                    main_play_pause.setImageResource(R.drawable.icon_play);
                    break;
                case MESSAGE_COMMS_FILE_START:
                    if(msg.obj instanceof String){
                        main_progress.show((String) msg.obj);
                    }
                    break;
                case MESSAGE_COMMS_FILE_PROGRESS:
                    if(msg.obj instanceof Integer){
                        main_progress.setProgress((Integer) msg.obj);
                    }
                    break;
                case MESSAGE_COMMS_FILE_DONE:
                    runOnUiThread(() -> Toast.makeText(getBaseContext(), R.string.file_received, Toast.LENGTH_SHORT).show());
                    if(msg.obj instanceof String){
                        commsNewFile((String) msg.obj);
                    }
                    runOnUiThread(() -> main_progress.setVisibility(View.GONE));
                    break;
                case MESSAGE_LIBRARY_READY:
                    if(current_tracks == null){
                        current_tracks = library.tracks;
                        loadFirstTrack();
                    }
                    break;
            }
        }
    };
    private void commsNewFile(String path){
        library.addFile(this, path);
    }

    public void openTrackList(ArrayList<Library.Track> tracks, int index){
        current_tracks = tracks;
        main_menu_library.setVisibility(View.GONE);
        main_menu_artists.setVisibility(View.GONE);
        main_menu_artist.setVisibility(View.GONE);
        main_menu_albums.setVisibility(View.GONE);
        main_menu_album.setVisibility(View.GONE);
        play(index);
    }
    private void play(int index){
        if(current_tracks.size() < index) return;
        current_index = index;
        loadTrackUi(index);
        executorService.submit(() -> player.play(this, current_tracks.get(current_index).uri));
    }
    private void loadFirstTrack(){
        if(current_tracks.size() == 0) return;
        loadTrackUi(0);
        executorService.submit(() -> player.load(this, current_tracks.get(0).uri));
    }
    private void loadTrackUi(int index){
        current_index = index;
        Library.Track track = current_tracks.get(current_index);
        main_song_title.setText(track.title);
        main_song_artist.setText(track.artist.name);
        if(current_index == 0){
            main_back.setColorFilter(getColor(R.color.icon_disabled), android.graphics.PorterDuff.Mode.SRC_IN);
        }else{
            main_back.setColorFilter(getColor(R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if(current_index >= current_tracks.size()-1){
            main_forward.setColorFilter(getColor(R.color.icon_disabled), android.graphics.PorterDuff.Mode.SRC_IN);
        }else{
            main_forward.setColorFilter(getColor(R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }
    private void bBackPressed(){
        if(current_index < 1) return;
        if(isPlaying){
            play(current_index-1);
        }else{
            loadTrackUi(current_index-1);
        }
    }
    private void bPlayPausePressed(){
        executorService.submit(() -> player.playPause(handler_message));
        if(isPlaying){
            main_play_pause.setImageResource(R.drawable.icon_play);
        }else{
            main_play_pause.setImageResource(R.drawable.icon_pause);
        }
    }
    private void bForwardPressed(){
        if(current_tracks.size() <= current_index+1) return;
        if(isPlaying){
            play(current_index+1);
        }else{
            loadTrackUi(current_index+1);
        }
    }
    @Override
    public void onBackPressed(){
        View view = getTouchView();
        if(view == null){
            System.exit(0);
        }else{
            view.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev){
        super.dispatchGenericMotionEvent(ev);
        return true; //Just to let Google know we are listening to rotary events
    }

    public void onMainClick(){
        //We need to do this to make sure that we can listen for onTouch on main
        Log.i(LOG_TAG, "onMainClick");
    }

    public void addOnTouch(View v){
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
                if(touchView == null) return false;

                int diffX1 = getBackSwipeDiffX(event);
                if(getBackSwipeVelocity(event, diffX1) < SWIPE_VELOCITY_THRESHOLD){
                    touchView.animate()
                            .x(0)
                            .scaleX(1f).scaleY(1f)
                            .setDuration(300).start();
                }else if(diffX1 > 0){
                    float move = event.getRawX() - onTouchStartX;
                    float scale = 1 - move/widthPixels;
                    if(isScreenRound){
                        touchView.setBackgroundResource(R.drawable.round_bg);
                    }
                    touchView.animate().x(move)
                            .scaleX(scale).scaleY(scale)
                            .setDuration(0).start();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(touchView != null){
                    touchView.animate()
                            .x(0)
                            .scaleX(1f).scaleY(1f)
                            .setDuration(150).start();
                    if(isScreenRound){
                        touchView.setBackgroundResource(0);
                        touchView.setBackgroundColor(getResources().getColor(R.color.black, null));
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
        touchView = getTouchView();
    }
    private View getTouchView(){
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
    public static String prettyTimer(long milli_secs){
        long tmp = milli_secs % 1000;
        long secs = (milli_secs - tmp) / 1000;
        tmp = secs % 60;
        long minutes = (secs - tmp) / 60;

        String pretty = Long.toString(tmp);
        if(tmp < 10){pretty = "0" + pretty;}
        pretty = minutes + ":" + pretty;

        return pretty;
    }


}
