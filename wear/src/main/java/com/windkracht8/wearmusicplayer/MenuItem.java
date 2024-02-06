package com.windkracht8.wearmusicplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MenuItem extends LinearLayout{
    int index;

    public MenuItem(Context context, AttributeSet attrs){super(context, attrs);}
    MenuItem(Main main, int index, String primary, String secondary){
        super(main);
        LayoutInflater inflater = (LayoutInflater) main.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if(inflater == null){Toast.makeText(main, R.string.fail_show_menu, Toast.LENGTH_SHORT).show();return;}
        inflater.inflate(R.layout.menu_item, this, true);

        this.index = index;
        TextView menu_item_primary = findViewById(R.id.menu_item_primary);
        TextView menu_item_secondary = findViewById(R.id.menu_item_secondary);

        menu_item_primary.setText(primary);
        if(secondary == null){
            menu_item_secondary.setVisibility(View.GONE);
        }else{
            menu_item_secondary.setText(secondary);
        }

        main.addOnTouch(this);
        main.addOnTouch(menu_item_primary);
        main.addOnTouch(menu_item_secondary);
    }
}

