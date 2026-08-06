package com.bitchat.emergency.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

@SuppressLint("MissingPermission")
class BleAdvertiser(context: Context) {
    private val advertiser = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeAdvertiser
    private val serviceUuid = ParcelUuid.fromString("0000b17c-0000-1000-8000-00805f9b34fb")
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BleAdvertiser", "Advertise started successfully")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e("BleAdvertiser", "Advertise failed: $errorCode")
        }
    }
    
    fun startAdvertising(payload: ByteArray) {
        if (advertiser == null) return
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()
            
        val data = AdvertiseData.Builder()
            .addServiceData(serviceUuid, payload)
            .build()
            
        try {
            advertiser.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e("BleAdvertiser", "Missing Bluetooth advertise permission", e)
        } catch (e: IllegalArgumentException) {
            Log.e("BleAdvertiser", "Payload too large for legacy advertising or device incompatible", e)
        }
    }
    
    fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: SecurityException) {
            // Ignore missing permissions on stop
        }
    }
}
