package com.windkracht8.wearmusicplayer;

import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

public class MenuActivity extends FragmentActivity implements MenuScreen.MenuScreenInterface{
    private ImageView menu_loading;
    private AnimatedVectorDrawable menu_loading_animate;
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);
        menu_loading = findViewById(R.id.menu_loading);
        menu_loading.setBackgroundResource(R.drawable.icon_animate);
        menu_loading_animate = (AnimatedVectorDrawable) menu_loading.getBackground();
    }
    @Override
    public void openMenuScreen(MenuScreen menuScreen){
        try{
            animationStart();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.menu_container, menuScreen, menuScreen.getClass().getSimpleName())
                    .addToBackStack(menuScreen.getClass().getSimpleName())
                    .commit();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "MenuActivity.openMenuScreen: " + e.getMessage());
            Toast.makeText(getApplicationContext(), R.string.fail_technical, Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void animationStart(){
        menu_loading_animate.start();
        menu_loading.setVisibility(View.VISIBLE);
    }
    @Override
    public void animationStop(){
        menu_loading.setVisibility(View.GONE);
        menu_loading_animate.stop();
    }
}
