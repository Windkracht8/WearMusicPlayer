package com.windkracht8.wearmusicplayer;

import android.content.Context;
import android.util.AttributeSet;

public class MenuLibrary extends Menu{

    public MenuLibrary(Context context, AttributeSet attrs){
        super(context, attrs);
    }

    public void show(Main main){
        super.show(main, 3, main.getString(R.string.menu_library_label));
    }
    @Override
    protected void onItemClick(Main main, MenuItem menuItem){
        switch(menuItem.index){
            case 0:
                main.main_menu_albums.show(main);
                break;
            case 1:
                main.main_menu_artists.show(main);
                break;
            case 2:
                main.onRescanClick();
                break;
        }
    }

    @Override
    String getItemPrimary(Main main, int index){
        switch(index){
            case 0:
                return main.getString(R.string.albums);
            case 1:
                return main.getString(R.string.artists);
            case 2:
                return main.getString(R.string.rescan);
        }
        return main.getString(R.string.fail_oops);
    }
    @Override
    String getItemSecondary(Main main, int index){
        switch(index){
            case 0:
                return String.valueOf(Main.library.albums.size());
            case 1:
                return String.valueOf(Main.library.artists.size());
            case 2:
                return null;
        }
        return main.getString(R.string.fail_oops);
    }
}
