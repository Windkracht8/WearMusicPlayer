package com.windkracht8.wearmusicplayer;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;

public class MenuScreenAll extends MenuScreen{
    private ArrayList<Library.Track> tracks;
    MenuScreenAll(){}
    MenuScreenAll(int scrollToTrack){this.scrollToTrack = scrollToTrack;}
    @Override public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        menu_label.setText(getString(R.string.all));
        MenuItem menuItem = new MenuItem(inflater, getString(R.string.randomise), null);
        menuItem.setOnClickListener(v->randomise());
        addMenuItem(menuItem);

        try{
            tracks = new ArrayList<>(Main.library.tracks);
            for(int trackIndex = 0; trackIndex < tracks.size(); trackIndex++){
                Library.Track track = tracks.get(trackIndex);
                MenuItem menuItemTrack = new MenuItem(inflater, track.title, track.artist.name);
                int finalTrackIndex = trackIndex;
                menuItemTrack.setOnClickListener(v->openTrackList(Library.TrackListType.ALL, 0, tracks, finalTrackIndex));
                addMenuItem(menuItemTrack);
            }
            if(scrollToTrack > 0 && scrollToTrack < tracks.size()) scrollToItem(scrollToTrack+1);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library tracks: " + e.getMessage());
        }
        return rootView;
    }
    private void randomise(){
        if(tracks == null) return;
        Collections.shuffle(tracks);
        for(int i = 1; i < menuItems.size(); i++){
            MenuItem menuItem = menuItems.get(i);
            Library.Track track = tracks.get(i-1);
            menuItem.setPrimary(track.title);
            menuItem.setSecondary(track.artist.name);
        }
    }
}
