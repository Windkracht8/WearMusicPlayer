package com.windkracht8.wearmusicplayer;

import android.content.Context;
import android.util.AttributeSet;

public class MenuArtists extends Menu{

    public MenuArtists(Context context, AttributeSet attrs){
        super(context, attrs);
    }

    public void show(Main main){
        super.show(main, Main.library.artists.size(), main.getString(R.string.artists));
    }
    @Override
    protected void onItemClick(Main main, MenuItem menuItem){
        main.main_menu_artist.show(main, menuItem.index);
    }
    @Override
    public String getItemPrimary(Main main, int index){
        return Main.library.artists.get(index).name;
    }
    @Override
    public String getItemSecondary(Main main, int index){
        Library.Artist artist = Main.library.artists.get(index);
        return artist.artist_albums.size() + " albums|" + artist.artist_tracks.size() + " tracks";
    }
}
