package com.windkracht8.wearmusicplayer;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MenuScreenMain extends MenuScreen{
    @Override
    public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        menu_label.setText(getString(R.string.library));
        int size = 0;
        try{
            size = Main.library.tracks.size();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library tracks size: " + e.getMessage());
        }
        MenuItem menuItem = new MenuItem(inflater, getString(R.string.all), String.valueOf(size));
        menuItem.setOnClickListener((v)-> openMenuScreen(new MenuScreenAll()));
        addMenuItem(menuItem);

        try{
            size = Main.library.albums.size();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library albums size: " + e.getMessage());
            size = 0;
        }
        menuItem = new MenuItem(inflater, getString(R.string.albums), String.valueOf(size));
        menuItem.setOnClickListener((v)-> openMenuScreen(new MenuScreenAlbums()));
        addMenuItem(menuItem);

        try{
            size = Main.library.artists.size();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library artists size: " + e.getMessage());
            size = 0;
        }
        menuItem = new MenuItem(inflater, getString(R.string.artists), String.valueOf(size));
        menuItem.setOnClickListener((v)-> openMenuScreen(new MenuScreenArtists()));
        addMenuItem(menuItem);

        menuItem = new MenuItem(inflater, getString(R.string.rescan), null);
        menuItem.setOnClickListener((v)-> rescan());
        addMenuItem(menuItem);

        return rootView;
    }
    @Override
    public void onAttach(@NonNull Context context){
        super.onAttach(context);
        if(context instanceof MenuScreenInterface)
            menuScreenInterface = (MenuScreenInterface) context;
    }
    private void rescan(){
        Main.rescan();
        assert getActivity() != null;
        getActivity().finish();
    }
}
