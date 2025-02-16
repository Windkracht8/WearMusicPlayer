package com.windkracht8.wearmusicplayer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

public class DeviceConnect extends Activity implements CommsBT.BTInterface{
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_connect);

        findViewById(R.id.root).setOnApplyWindowInsetsListener(Main.onApplyWindowInsetsListener);

        Intent startDeviceConnect = getIntent();
        String text = getString(R.string.connecting_to) + " " + startDeviceConnect.getStringExtra("name");
        ((TextView)findViewById(R.id.device_connect_name)).setText(text);

        ImageView icon = findViewById(R.id.device_connect_icon);
        ((AnimatedVectorDrawable) icon.getBackground()).start();

        if(Main.commsBT.status != CommsBT.Status.CONNECTING) finishAndRemoveTask();
        try{
            Main.commsBT.addListener(this);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "DeviceConnect.onCreate Failed to add as a listener: " + e.getMessage());
        }
    }
    @Override
    public void onBTStartDone(){finishAndRemoveTask();}
    @Override
    public void onBTConnecting(String x){}
    @Override
    public void onBTConnectFailed(){finishAndRemoveTask();}
    @Override
    public void onBTConnected(String x){finishAndRemoveTask();}
    @Override
    public void onBTDisconnected(){}
    @Override
    public void onBTSending(String x){}
    @Override
    public void onBTResponse(JSONObject x){}
    @Override
    public void onBTError(int x){}

}
