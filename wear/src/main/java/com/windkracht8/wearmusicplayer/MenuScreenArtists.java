package com.windkracht8.wearmusicplayer;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MenuScreenArtists extends MenuScreen{
    @Override public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        menu_label.setText(getString(R.string.artists));
        try{
            for(int index = 0; index < Main.library.artists.size(); index++){
                Library.Artist artist = Main.library.artists.get(index);
                String second = "";
                if(artist.albums.size() == 1){
                    second = "1 " + getString(R.string.menu_album) + " | ";
                }else if(artist.albums.size() > 1){
                    second = artist.albums.size() + " " + getString(R.string.menu_albums) + " | ";
                }
                if(artist.tracks.size() == 1){
                    second += "1 " + getString(R.string.menu_track);
                }else{
                    second += artist.tracks.size() + " " + getString(R.string.menu_tracks);
                }
                MenuItem menuItem = new MenuItem(inflater, artist.name, second);
                int finalIndex = index;
                menuItem.setOnClickListener((v)-> openMenuScreen(new MenuScreenArtist(finalIndex)));
                addMenuItem(menuItem);
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library artists: " + e.getMessage());
        }
        return rootView;
    }
}
