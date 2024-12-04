package com.windkracht8.wearmusicplayer;

import android.content.Intent;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import org.json.JSONObject;

public class DeviceConnect extends FragmentActivity implements CommsBT.CommsBTInterface{
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_connect);
        Intent startDeviceConnect = getIntent();
        String text = getString(R.string.connecting_to) + " " + startDeviceConnect.getStringExtra("name");
        ((TextView)findViewById(R.id.device_connect_name)).setText(text);

        ImageView icon = findViewById(R.id.device_connect_icon);
        icon.setBackgroundResource(R.drawable.icon_watch_searching);
        ((AnimatedVectorDrawable) icon.getBackground()).start();

        Main.commsBT.addListener(this);
    }
    @Override
    public void onBTStartDone(){}
    @Override
    public void onBTConnecting(String x){}
    @Override
    public void onBTConnectFailed(){finish();}
    @Override
    public void onBTConnected(String x){finish();}
    @Override
    public void onBTDisconnected(){}
    @Override
    public void onBTSending(String x){}
    @Override
    public void onBTResponse(JSONObject x){}
    @Override
    public void onBTError(int x){}

}
