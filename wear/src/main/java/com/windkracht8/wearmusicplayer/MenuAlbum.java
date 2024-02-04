package com.windkracht8.wearmusicplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

public class MenuAlbum extends Menu{
    Library.Album album;
    public MenuAlbum(Context context, AttributeSet attrs){
        super(context, attrs);
    }

    public void show(Main main, int index){
        try{
            album = Main.library.albums.get(index);
            super.show(main, album.album_tracks.size(), album.name);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "MenuAlbum.show album not found");
        }
    }
    @Override
    protected void onItemClick(Main main, MenuItem menuItem){
        main.openTrackList(album.album_tracks, menuItem.index);
    }
    @Override
    String getItemPrimary(Main main, int index){
        return album.album_tracks.get(index).title;
    }
    @Override
    String getItemSecondary(Main main, int index){
        return album.album_tracks.get(index).artist.name;
    }
}
