package com.windkracht8.wearmusicplayer;

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
import androidx.core.app.NotificationCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends Activity{
    static final String LOG_TAG = "WearMusicPlayer";
    static boolean isScreenRound;
    private boolean showSplash = true;
    private boolean hasBTPermission = false;
    private TextView main_timer;
    private ImageView main_previous;
    private ImageView main_play_pause;
    private ImageView main_next;
    private TextView main_song_title;
    private TextView main_song_artist;
    private TextView main_library;
    Progress main_progress;
    private MenuLibrary main_menu_library;
    MenuArtists main_menu_artists;
    MenuArtist main_menu_artist;
    MenuAlbums main_menu_albums;
    MenuAlbum main_menu_album;
    private View currentVisibleView;

    ExecutorService executorService;
    private AudioManager audioManager;

    static int heightPixels;
    static int widthPixels;
    static int vh25;
    static int vw20;
    static int vh75;

    private static float onTouchStartY = -1;
    private static float onTouchStartX = 0;
    private static int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 50;

    final static Library library = new Library();
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
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        heightPixels = displayMetrics.heightPixels;
        widthPixels = displayMetrics.widthPixels;
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

        if(heightPixels < 68 * displayMetrics.scaledDensity + 144 * displayMetrics.density){
            main_song_title.setLines(1);
        }

        showSplash = false;
        executorService.submit(() -> library.scanMediaStore(this));
        executorService.submit(() -> requestPermissions());
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        commsBT.stopComms();
        stopOngoingNotification();
    }
    private void requestPermissions(){
        if(Build.VERSION.SDK_INT >= 33){
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    && hasPermission(Manifest.permission.BLUETOOTH_SCAN);
            if(!hasBTPermission
                    || !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            ){
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.POST_NOTIFICATIONS
                        ,Manifest.permission.BLUETOOTH_CONNECT
                        ,Manifest.permission.BLUETOOTH_SCAN}, 1);
            }
        }else if(Build.VERSION.SDK_INT >= 31){
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    && hasPermission(Manifest.permission.BLUETOOTH_SCAN);
            if(!hasBTPermission
                    || !hasPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            ){
                ActivityCompat.requestPermissions(this, new String[]{
                                Manifest.permission.MANAGE_EXTERNAL_STORAGE
                                ,Manifest.permission.BLUETOOTH_CONNECT
                                ,Manifest.permission.BLUETOOTH_SCAN}, 1);
            }
        }else{//30
            hasBTPermission = hasPermission(Manifest.permission.BLUETOOTH);
            if(!hasBTPermission
                    || !hasPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                    || !hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            ){
                ActivityCompat.requestPermissions(this, new String[]{
                                Manifest.permission.MANAGE_EXTERNAL_STORAGE
                                ,Manifest.permission.READ_EXTERNAL_STORAGE
                                ,Manifest.permission.BLUETOOTH}, 1);
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
    void initBT(){
        if(!hasBTPermission) return;
        commsBT = new CommsBT(this);
        executorService.submit(() -> commsBT.startComms());
    }

    void toast(int message){
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
    void commsFileStart(String path){
        runOnUiThread(()->main_progress.show(path));
    }
    void commsProgress(int progress){
        runOnUiThread(()->main_progress.setProgress(progress));
    }
    void commsFileDone(String path){
        executorService.submit(()->library.addFile(this, path));
        runOnUiThread(()->main_progress.setVisibility(View.GONE));
        executorService.submit(()->commsBT.sendFileBinaryResponse(path));
    }
    void commsFileFailed(String path){
        executorService.submit(()->library.deleteFile(this, path));
        runOnUiThread(()->main_progress.setVisibility(View.GONE));
        executorService.submit(()->commsBT.sendResponse("fileBinary", "failed"));
    }
    void libraryReady(){
        runOnUiThread(()->main_library.setText(R.string.library));
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
        player.playTrack(this, current_tracks.get(index).uri);
    }
    private void loadTrack(int index){
        if(current_tracks == null ||
                current_tracks.size() == 0 ||
                current_tracks.size() <= index ||
                index < 0) return;
        runOnUiThread(()->{
            loadTrackUi(index);
            player.loadTrack(this, current_tracks.get(index).uri);
        });
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
    void librarySetScanning(){
        if(!isPlaying){
            runOnUiThread(()->{
                main_song_title.setText("");
                main_song_artist.setText("");
                main_library.setText(R.string.scanning);
                current_tracks = null;
            });
        }
    }
    void bPreviousPressed(){
        loadTrack(current_index-1);
    }
    private void bPlayPausePressed(){
        player.playPause();
    }
    void bNextPressed(){
        loadTrack(current_index+1);
    }

    void onIsPlayingChanged(boolean isPlaying){
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
    void onProgressChanged(long currentPosition){
        main_timer.setText(prettyTimer(currentPosition));
    }
    void onPlayerEnded(){
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

    void onMainClick(){
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
    static String prettyTimer(long milli_secs){
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
        String WMP_Notification = "WMP_Notification";
        int RRW_Notification_ID = 1;

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(WMP_Notification, getString(R.string.open_wmp), NotificationManager.IMPORTANCE_DEFAULT);
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
                ,WMP_Notification
        )
                .setSmallIcon(R.drawable.icon_vector)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(
                        R.drawable.icon_vector, getString(R.string.open_wmp),
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
