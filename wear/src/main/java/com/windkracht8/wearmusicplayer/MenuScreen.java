package com.windkracht8.wearmusicplayer;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;

public abstract class MenuScreen extends Fragment{
    private MenuInterface menuInterface;
    private ScrollView menu_sv;
    TextView menu_label;
    private LinearLayout menu_items;
    final ArrayList<MenuItem> menuItems = new ArrayList<>();
    private static int itemHeight = 0;
    private static float scalePerPixel;
    private static float bottom_quarter;
    private static float below_screen;

    @Override
    public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = inflater.inflate(R.layout.menu_screen, container, false);
        menu_sv = rootView.findViewById(R.id.menu_sv);
        menu_label = rootView.findViewById(R.id.menu_label);
        menu_label.setMinimumHeight(Main.vh25);
        menu_items = rootView.findViewById(R.id.menu_items);
        ((LinearLayout.LayoutParams) menu_items.getLayoutParams()).bottomMargin += Main.vh25;

        if(Main.isScreenRound){
            ((LinearLayout.LayoutParams) menu_label.getLayoutParams()).leftMargin = Main.vw20;
            ((LinearLayout.LayoutParams) menu_label.getLayoutParams()).rightMargin = Main.vw20;
            rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){
                @Override
                public void onGlobalLayout(){
                    rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    if(itemHeight == 0){
                        itemHeight = menuItems.get(0).getHeight();
                        bottom_quarter = Main.vh75 - itemHeight;
                        below_screen = Main.heightPixels - itemHeight;
                        scalePerPixel = 0.2f / Main.vh25;
                    }
                    scaleMenuItems(0);
                }
            });
            menu_sv.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> scaleMenuItems(scrollY));
        }
        return rootView;
    }
    @Override
    public void onAttach(@NonNull Context context){
        super.onAttach(context);
        if(menuInterface == null && context instanceof MenuInterface) menuInterface = (MenuInterface) context;
    }
    @Override
    public void onResume(){
        super.onResume();
        menu_sv.requestFocus();
        if(menuInterface != null) menuInterface.animationStop();
    }
    void openMenuScreen(MenuScreen menuScreen){
        if(menuInterface != null){
            menuInterface.openMenuScreen(menuScreen);
        }else{
            Log.e(Main.LOG_TAG, "MenuScreen.openMenuScreen no menuScreenInterface");
            Toast.makeText(getContext(), R.string.fail_technical, Toast.LENGTH_SHORT).show();
        }
    }

    void addMenuItem(MenuItem menuItem){
        menuItems.add(menuItem);
        menu_items.addView(menuItem);
    }
    void openTrackList(ArrayList<Library.Track> tracks, int trackIndex){
        menuInterface.animationStart();
        Main.openTrackList = tracks;
        Main.openTrackListTrack = trackIndex;
        assert getActivity() != null;
        getActivity().finish();
    }

    private void scaleMenuItems(int scrollY){
        float top;
        float scale;
        for(MenuItem menuItem : menuItems){
            top = Main.vh25 + (menuItem.getY() - scrollY);//add vh25 which is the height of the label
            scale = 1.0f;
            if(top < 0){
                //the item is above the screen
                scale = 0.8f;
            }else if(top < Main.vh25){
                //the item is in the top quarter
                scale = 0.8f + (scalePerPixel * top);
            }else if(top > below_screen){
                //the item is below the screen
                scale = 0.8f;
            }else if(top > bottom_quarter){
                //the item is in the bottom quarter
                scale = 1.0f - (scalePerPixel * (top - bottom_quarter));
            }
            menuItem.setScaleX(scale);
            menuItem.setScaleY(scale);
        }
    }
}
