package com.windkracht8.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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
import androidx.core.app.NotificationCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends Activity{
    public static final String LOG_TAG = "MusicPlayer";
    public static boolean isScreenRound;
    private boolean showSplash = true;
    private boolean hasBTPermission = false;
    private TextView main_timer;
    private ImageView main_previous;
    private ImageView main_play_pause;
    private ImageView main_next;
    private TextView main_song_title;
    private TextView main_song_artist;
    private TextView main_library;
    private Progress main_progress;
    private MenuLibrary main_menu_library;
    public MenuArtists main_menu_artists;
    public MenuArtist main_menu_artist;
    public MenuAlbums main_menu_albums;
    public MenuAlbum main_menu_album;
    private View currentVisibleView;

    public ExecutorService executorService;
    private AudioManager audioManager;
    public final static int MESSAGE_TOAST = 101;
    public final static int MESSAGE_LIBRARY_READY = 201;
    public final static int MESSAGE_LIBRARY_SCANNING = 202;
    public final static int MESSAGE_COMMS_FILE_START = 301;
    public final static int MESSAGE_COMMS_FILE_PROGRESS = 302;
    public final static int MESSAGE_COMMS_FILE_DONE = 303;
    public final static int MESSAGE_COMMS_FILE_ERROR = 304;
    private final static int REQUEST_PERMISSION_CODE = 100;

    public static int heightPixels;
    public static int widthPixels;
    public static int vh25;
    public static int vw20;
    public static int vh75;

    private static float onTouchStartY = -1;
    private static float onTouchStartX = 0;
    private static int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 50;

    public final static Library library = new Library();
    private W8Player player;
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
        vw20 = (int) (widthPixels * .2);
        vh75 = (int) (heightPixels * .75);

        executorService = Executors.newFixedThreadPool(4);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        player = new W8Player(this);

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

        showSplash = false;
        executorService.submit(() -> library.scanMediaStore(this));
        executorService.submit(() -> requestPermissions());
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        stopOngoingNotification();
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
        if(hasBTPermission) initBT();
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

    public void initBT(){
        if(!hasBTPermission) return;
        commsBT = new CommsBT(this);
        executorService.submit(() -> commsBT.startComms());
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
                case MESSAGE_LIBRARY_SCANNING:
                    setScanning();
                    break;
                case MESSAGE_LIBRARY_READY:
                    main_library.setText(R.string.library);
                    if(current_tracks == null){
                        current_tracks = library.tracks;
                        loadFirstTrack();
                    }
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
                case MESSAGE_COMMS_FILE_ERROR:
                    main_progress.setVisibility(View.GONE);
                    break;
                case MESSAGE_COMMS_FILE_DONE:
                    if(msg.obj instanceof String){
                        commsFileDone((String) msg.obj);
                    }
                    main_progress.setVisibility(View.GONE);
                    break;
            }
        }
    };
    private void commsFileDone(String path){
        executorService.submit(() -> library.addFile(this, path));
        executorService.submit(() -> commsBT.sendResponse("fileBinary", path));
    }
    public void commsFileFailed(String path){
        executorService.submit(() -> library.addFile(this, path));
        executorService.submit(() -> commsBT.sendResponse("fileBinary", "failed"));
    }

    public void openTrackList(ArrayList<Library.Track> tracks, int index){
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
        player.playTrack(this, current_tracks.get(index).uri);
    }
    private void loadTrack(int index){
        if(current_tracks == null ||
                current_tracks.size() == 0 ||
                current_tracks.size() <= index ||
                index < 0) return;
        loadTrackUi(index);
        player.loadTrack(this, current_tracks.get(index).uri);
    }
    private void loadFirstTrack(){
        loadTrack(0);
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
    public void onRescanClick(){
        setScanning();
        main_menu_library.setVisibility(View.GONE);
        executorService.submit(() -> library.scanFiles(this, ""));
    }
    private void setScanning(){
        if(!isPlaying){
            main_song_title.setText("");
            main_song_artist.setText("");
            main_library.setText(R.string.scanning);
            current_tracks = null;
        }
    }
    public void bPreviousPressed(){
        loadTrack(current_index-1);
    }
    private void bPlayPausePressed(){
        player.playPause();
    }
    public void bNextPressed(){
        loadTrack(current_index+1);
    }

    public void onIsPlayingChanged(boolean isPlaying){
        if(this.isPlaying == isPlaying) return;
        this.isPlaying = isPlaying;
        if(isPlaying){
            main_play_pause.setImageResource(R.drawable.icon_pause);
            startOngoingNotification();
        }else{
            main_play_pause.setImageResource(R.drawable.icon_play);
            stopOngoingNotification();
        }
    }
    public void onProgressChanged(long currentPosition){
        main_timer.setText(prettyTimer(currentPosition));
    }
    public void onPlayerEnded(){
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
                AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.w8mp_alert));
                builder.setMessage(R.string.confirm_close);
                builder.setPositiveButton(R.string.yes, (dialog, which) -> System.exit(0));
                builder.setNegativeButton(R.string.back, (dialog, which) -> dialog.dismiss());
                builder.create().show();
            }else{
                System.exit(0);
            }
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
    private void startOngoingNotification(){
        String MP_Notification = "MP_Notification";
        int RRW_Notification_ID = 1;

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(MP_Notification, getString(R.string.open_mp), NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(notificationChannel);

        Intent actionIntent = new Intent(this, Main.class);
        PendingIntent actionPendingIntent = PendingIntent.getActivity(
                this,
                0,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                this
                ,MP_Notification
        )
                .setSmallIcon(R.drawable.icon_vector)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(
                        R.drawable.icon_vector, getString(R.string.open_mp),
                        actionPendingIntent
                )
                .setOngoing(true);

        Status ongoingActivityStatus = new Status.Builder()
                .addTemplate(getString(R.string.playing_track))
                .build();

        OngoingActivity ongoingActivity = new OngoingActivity.Builder(
                getBaseContext()
                ,RRW_Notification_ID
                ,notificationBuilder
        )
                .setStaticIcon(R.drawable.icon_vector)
                .setTouchIntent(actionPendingIntent)
                .setStatus(ongoingActivityStatus)
                .build();

        ongoingActivity.apply(getBaseContext());

        notificationManager.notify(RRW_Notification_ID, notificationBuilder.build());
    }
    private void stopOngoingNotification(){
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

}
