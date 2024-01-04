package com.windkracht8.musicplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;

public abstract class Menu extends ConstraintLayout{
    private ScrollView menu_sv;
    private TextView menu_label;
    private LinearLayout menu_items;
    private final ArrayList<MenuItem> menuItems = new ArrayList<>();
    private boolean isInitialized;
    private int itemHeight;
    private boolean isItemHeightInitialized;
    private float scalePerPixel;
    private float bottom_quarter;
    private float below_screen;
    private String labelText = "";
    private int libraryScanVersion = 0;

    public Menu(Context context, AttributeSet attrs){
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if(inflater == null){
            Toast.makeText(context, R.string.fail_show_menu, Toast.LENGTH_SHORT).show();
            return;
        }
        inflater.inflate(R.layout.menu, this, true);
        menu_sv = findViewById(R.id.menu_sv);
        menu_label = findViewById(R.id.menu_label);
        menu_label.setMinimumHeight(Main.vh25);
        menu_items = findViewById(R.id.menu_items);
        ((LinearLayout.LayoutParams) menu_items.getLayoutParams()).bottomMargin += Main.vh25;
    }

    public void requestSVFocus(){
        menu_sv.requestFocus();
    }
    private void show(){
        setVisibility(View.VISIBLE);
        menu_sv.fullScroll(View.FOCUS_UP);
        if(isItemHeightInitialized) scaleMenuItems(0);
        menu_sv.requestFocus();
    }
    public void show(Main main, int size, String labelText){
        if(isInitialized &&
                libraryScanVersion == Library.libraryScanVersion &&
                this.labelText.equals(labelText)
        ){
            show();
            return;
        }
        if(!isInitialized){
            isInitialized = true;
            main.addOnTouch(menu_sv);
            menu_sv.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> scaleMenuItems(scrollY));
        }
        this.labelText = labelText;
        menu_label.setText(this.labelText);
        if(Main.isScreenRound){
            ((LinearLayout.LayoutParams) menu_label.getLayoutParams()).leftMargin = Main.vw20;
            ((LinearLayout.LayoutParams) menu_label.getLayoutParams()).rightMargin = Main.vw20;
        }
        libraryScanVersion = Library.libraryScanVersion;
        for (int i = menu_items.getChildCount(); i > 0; i--) {
            menu_items.removeViewAt(i - 1);
        }
        if(size < 1){
            show();
            return;
        }
        menuItems.clear();
        for(int i = 0; i < size; i++){
            MenuItem menuItem = new MenuItem(main, i, getItemPrimary(main, i), getItemSecondary(main, i));
            menuItems.add(menuItem);
            menu_items.addView(menuItem);
            menuItem.setOnClickListener(v -> onItemClick(main, menuItem));
        }

        getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if(!Main.isScreenRound || isItemHeightInitialized) return;
            isItemHeightInitialized = true;
            itemHeight = menuItems.get(0).getHeight();
            bottom_quarter = Main.vh75-itemHeight;
            below_screen = Main.heightPixels-itemHeight;
            scalePerPixel = 0.5f / Main.vh25;
            scaleMenuItems(0);
        });

        setVisibility(View.VISIBLE);
        menu_sv.fullScroll(View.FOCUS_UP);
    }

    private void scaleMenuItems(int scrollY){
        float top;
        float scale;
        for(MenuItem menuItem : menuItems){
            top = Main.vh25 + (menuItem.getY() - scrollY);//add vh25 which is the height of the label
            scale = 1.0f;
            if(top < 0){
                //the item is above the screen
                scale = 0.5f;
            }else if(top < Main.vh25){
                //the item is in the top quarter
                scale = 0.5f + (scalePerPixel * top);
            }else if(top > below_screen){
                //the item is below the screen
                scale = 0.5f;
            }else if(top > bottom_quarter){
                //the item is in the bottom quarter
                scale = 1.0f - (scalePerPixel * (top - bottom_quarter));
            }
            menuItem.setScaleX(scale);
            menuItem.setScaleY(scale);
        }
    }

    protected abstract void onItemClick(Main main, MenuItem menuItem);

    public String getItemPrimary(Main main, int index){
        return main.getString(R.string.fail_oops);
    }
    public String getItemSecondary(Main main, int index){
        return null;
    }

}
