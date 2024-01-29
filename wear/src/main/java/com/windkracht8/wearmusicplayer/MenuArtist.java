package com.windkracht8.wearmusicplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

public class MenuArtist extends Menu{
    Library.Artist artist;
    public MenuArtist(Context context, AttributeSet attrs){
        super(context, attrs);
    }

    public void show(Main main, int index){
        //TODO: first show albums, than tracks
        try{
            artist = Main.library.artists.get(index);
            super.show(main, artist.artist_tracks.size(), artist.name);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "MenuArtist.show artist not found");
        }
    }
    @Override
    protected void onItemClick(Main main, MenuItem menuItem){
        main.openTrackList(artist.artist_tracks, menuItem.index);
    }
    @Override
    public String getItemPrimary(Main main, int index){
        return artist.artist_tracks.get(index).title;
    }
    @Override
    public String getItemSecondary(Main main, int index){
        return artist.artist_tracks.get(index).artist.name;
    }
}
