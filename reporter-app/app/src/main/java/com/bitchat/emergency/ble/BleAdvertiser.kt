package com.bitchat.emergency.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

@SuppressLint("MissingPermission")
class BleAdvertiser(context: Context) {
    private val advertiser = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeAdvertiser
    private val serviceUuid = ParcelUuid.fromString("0000b17c-0000-1000-8000-00805f9b34fb")
    
    private var currentAdvertisingSet: AdvertisingSet? = null
    
    private val advertiseCallback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
            if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                Log.d("BleAdvertiser", "Extended advertise started successfully")
                currentAdvertisingSet = advertisingSet
            } else {
                Log.e("BleAdvertiser", "Extended advertise failed with status: $status")
            }
        }
        
        override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
            Log.d("BleAdvertiser", "Extended advertise stopped successfully")
            if (advertisingSet == currentAdvertisingSet) {
                currentAdvertisingSet = null
            }
        }
    }
    
    fun startAdvertising(payload: ByteArray) {
        if (advertiser == null) return
        
        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(false)
            .setConnectable(false)
            .setScannable(false)
            .setInterval(AdvertisingSetParameters.INTERVAL_LOW) // 160 = 100ms
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
            .setSecondaryPhy(BluetoothDevice.PHY_LE_1M)
            .build()
            
        val data = AdvertiseData.Builder()
            .addServiceData(serviceUuid, payload)
            .build()
            
        try {
            advertiser.startAdvertisingSet(parameters, data, null, null, null, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e("BleAdvertiser", "Missing Bluetooth advertise permission", e)
        } catch (e: IllegalArgumentException) {
            Log.e("BleAdvertiser", "Payload too large or invalid parameters", e)
        }
    }
    
    fun stopAdvertising() {
        try {
            currentAdvertisingSet?.enableAdvertising(false, 0, 0)
            advertiser?.stopAdvertisingSet(advertiseCallback)
        } catch (e: SecurityException) {
            // Ignore missing permissions on stop
        }
    }

    companion object {
        fun isExtendedAdvertisingSupported(context: Context): Boolean {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            return adapter?.isLeExtendedAdvertisingSupported ?: false
        }

        fun getMaxAdvertisingDataLength(context: Context): Int {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            return adapter?.leMaximumAdvertisingDataLength ?: 31
        }
    }
}
