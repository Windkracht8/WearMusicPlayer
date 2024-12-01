package com.windkracht8.wearmusicplayer;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MenuScreenAlbums extends MenuScreen{
    @Override
    public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        menu_label.setText(getString(R.string.albums));
        try{
            for(int index = 0; index < Main.library.albums.size(); index++){
                Library.Album album = Main.library.albums.get(index);
                MenuItem menuItem = new MenuItem(inflater, album.name, album.artist);
                int finalIndex = index;
                menuItem.setOnClickListener((v)-> openMenuScreen(new MenuScreenAlbum(finalIndex)));
                addMenuItem(menuItem);
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library albums: " + e.getMessage());
        }
        return rootView;
    }
}
