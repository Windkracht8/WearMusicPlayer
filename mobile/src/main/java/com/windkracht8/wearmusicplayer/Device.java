package com.windkracht8.wearmusicplayer;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

@SuppressLint("MissingPermission")//Handled by main
class Device extends LinearLayout{
    final BluetoothDevice bluetoothDevice;
    Device(LayoutInflater inflater, BluetoothDevice bluetoothDevice){
        super(inflater.getContext());
        this.bluetoothDevice = bluetoothDevice;
        inflater.inflate(R.layout.device, this);
        ((TextView)findViewById(R.id.device_name)).setText(bluetoothDevice.getName());
    }
}
