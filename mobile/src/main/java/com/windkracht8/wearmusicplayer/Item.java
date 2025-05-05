/*
 *  Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 *  This file is part of WearMusicPlayer
 *  WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *  WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *  You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.windkracht8.wearmusicplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;

class Item extends ConstraintLayout{
    private final boolean isDir;
    final Library.LibItem libItem;

    private TextView item_status;
    private TextView item_label;
    private LinearLayout item_items;
    private View item_line;

    private final ArrayList<Item> items = new ArrayList<>();

    Item(Main main, Library.LibDir libDir){
        super(main);
        libItem = libDir;
        isDir = true;
        show(main);
        item_label.setTextAppearance(R.style.w8TextViewStyleBold);
        item_status.setContentDescription(main.getString(R.string.item_status_desc) + libItem.name);
    }
    Item(Main main, Library.LibTrack libTrack){
        super(main);
        libItem = libTrack;
        isDir = false;
        show(main);
    }
    private void show(Main main){
        ((LayoutInflater) main.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
            .inflate(R.layout.item, this, true);
        item_status = findViewById(R.id.item_status);
        item_label = findViewById(R.id.item_label);
        item_items = findViewById(R.id.item_items);
        item_line = findViewById(R.id.item_line);
        if(!isDir) item_status.setOnClickListener(v->main.onItemStatusPressed(this));
        item_label.setOnClickListener(v->onLabelPressed(main));

        newStatus();

        if(libItem.depth > 0){
            ((ConstraintLayout.LayoutParams) findViewById(R.id.item_root).getLayoutParams()).setMarginStart(Main._5dp * libItem.depth);
        }
        item_label.setText(libItem.name);
    }
    void hideLine(){item_line.setVisibility(View.GONE);}

    void clearStatus(){
        libItem.clearStatus();
        items.forEach(Item::clearStatus);
        newStatus();
    }
    void newStatus(Main main, Library.LibItem.Status status){
        libItem.status = status;
        main.runOnUiThread(this::newStatus);
    }
    void newStatus(){
        if(!isDir) item_status.setText("");
        switch(libItem.status){
            case FULL:
                if(isDir){
                    item_status.setText("V");
                }else{
                    item_status.setBackgroundResource(R.drawable.icon_delete);
                }
                break;
            case PARTIAL:
                if(isDir) item_status.setText("/");
                break;
            case NOT:
                if(isDir){
                    item_status.setText("");
                }else{
                    item_status.setBackgroundResource(R.drawable.icon_upload);
                }
                break;
            case UNKNOWN:
                if(isDir){
                    item_status.setText("");
                }else{
                    item_status.setBackgroundResource(0);
                }
                break;
        }
        items.forEach(Item::newStatus);
    }
    void updateProgress(Main main, long progress){
        if(libItem.length <= 0) return;
        main.runOnUiThread(()->item_status.setText(String.valueOf((progress * 100) / libItem.length)));
    }
    private void onLabelPressed(Main main){
        if(!isDir) return;
        if(item_items.getVisibility() == VISIBLE){
            item_items.setVisibility(GONE);
        }else{
            if(items.isEmpty()){
                Library.LibDir libDir = (Library.LibDir)libItem;
                for(Library.LibDir libDirSub : libDir.libDirs){
                    Item item = new Item(main, libDirSub);
                    items.add(item);
                    item_items.addView(item);
                }
                for(Library.LibTrack libTrack : libDir.libTracks){
                    Item item = new Item(main, libTrack);
                    items.add(item);
                    item_items.addView(item);
                }
            }
            if(!items.isEmpty()) items.get(items.size()-1).hideLine();
            item_items.setVisibility(VISIBLE);
        }
    }
}
