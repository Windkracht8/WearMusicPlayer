package com.windkracht8.musicplayer;

import android.content.Context;
import android.util.AttributeSet;

public class MenuAlbums extends Menu{
    public MenuAlbums(Context context, AttributeSet attrs){
        super(context, attrs);
    }

    public void show(Main main){
        super.show(main, Main.library.albums.size(), main.getString(R.string.albums));
    }
    @Override
    protected void onItemClick(Main main, MenuItem menuItem){
        main.main_menu_album.show(main, menuItem.index);
    }
    @Override
    public String getItemPrimary(Main main, int index){
        return Main.library.albums.get(index).name;
    }
    @Override
    public String getItemSecondary(Main main, int index){
        return Main.library.albums.get(index).artist;
    }
}
