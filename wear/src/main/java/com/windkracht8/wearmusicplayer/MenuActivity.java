package com.windkracht8.wearmusicplayer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import java.util.List;

public class MenuActivity extends FragmentActivity{
    private GestureDetector gestureDetector;
    private ImageView menu_loading;
    private AnimatedVectorDrawable menu_loading_animate;
    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        if(getIntent().getBooleanExtra("close", false)) finish();
        gestureDetector = new GestureDetector(this, simpleOnGestureListener, new Handler(Looper.getMainLooper()));
        setContentView(R.layout.menu);
        menu_loading = findViewById(R.id.menu_loading);
        menu_loading_animate = (AnimatedVectorDrawable) menu_loading.getBackground();
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.addOnBackStackChangedListener(()->{
            List<Fragment> fragments = fragmentManager.getFragments();
            if(fragments.get(fragments.size()-1) instanceof MenuScreen menuScreen){
                //This is needed because MenuScreen.onResume is not called when pop-ing the stack
                menuScreen.menu_sv.requestFocus();
            }
        });
        deeplink(getIntent());
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        if(intent.getBooleanExtra("close", false)) finish();
        deeplink(intent);
    }
    @Override public void onResume(){
        super.onResume();
        Main.isMenuVisible = true;
    }
    @Override public void onPause(){
        super.onPause();
        Main.isMenuVisible = false;
    }
    private void deeplink(Intent intent){
        if(!intent.getBooleanExtra("deeplink", false)) return;
        switch(Main.trackListType){
            case ALL:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.menu_container, new MenuScreenAll(Main.trackListIndex))
                        .commit();
                break;
            case ALBUM:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.menu_container, new MenuScreenAlbum(Main.trackListId, Main.trackListIndex))
                        .commit();
                break;
            case ARTIST:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.menu_container, new MenuScreenArtist(Main.trackListId, Main.trackListIndex))
                        .commit();
                break;
        }
    }
    void openMenuScreen(MenuScreen menuScreen){
        animationStart();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.menu_container, menuScreen, menuScreen.getClass().getSimpleName())
                .addToBackStack(menuScreen.getClass().getSimpleName())
                .commit();
    }
    void animationStart(){
        menu_loading_animate.start();
        menu_loading.setVisibility(View.VISIBLE);
    }
    void animationStop(){
        menu_loading.setVisibility(View.GONE);
        menu_loading_animate.stop();
    }
    @Override public boolean onTouchEvent(MotionEvent event){return gestureDetector.onTouchEvent(event);}
    private final GestureDetector.SimpleOnGestureListener simpleOnGestureListener = new GestureDetector.SimpleOnGestureListener(){
        @Override public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY){
            if(Math.abs(velocityX) < Math.abs(velocityY)) return false;
            if(velocityX > 0) onBack();
            return true;
        }
    };
    @SuppressLint("ClickableViewAccessibility")
    void addOnTouch(View view){view.setOnTouchListener((v, e)->gestureDetector.onTouchEvent(e));}
    private void onBack(){
        if(getSupportFragmentManager().getBackStackEntryCount() == 0){
            finish();
        }else{
            getSupportFragmentManager().popBackStack();
        }
    }
}
