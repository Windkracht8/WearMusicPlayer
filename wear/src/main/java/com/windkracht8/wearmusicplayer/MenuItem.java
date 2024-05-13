package com.windkracht8.wearmusicplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MenuItem extends LinearLayout{
    MenuItem(LayoutInflater inflater, String primary, String secondary){
        super(inflater.getContext());
        inflater.inflate(R.layout.menu_item, this);

        TextView menu_item_primary = findViewById(R.id.menu_item_primary);
        menu_item_primary.setText(primary);

        TextView menu_item_secondary = findViewById(R.id.menu_item_secondary);
        if(secondary == null){
            menu_item_secondary.setVisibility(View.GONE);
        }else{
            menu_item_secondary.setText(secondary);
        }
    }
}

