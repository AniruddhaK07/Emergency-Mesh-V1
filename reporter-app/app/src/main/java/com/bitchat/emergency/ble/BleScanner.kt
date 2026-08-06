package com.bitchat.emergency.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

@SuppressLint("MissingPermission")
class BleScanner(context: Context, private val onPayloadReceived: (ByteArray) -> Unit) {
    private val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner
    private val serviceUuid = ParcelUuid.fromString("0000b17c-0000-1000-8000-00805f9b34fb")

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.scanRecord?.serviceData?.let { serviceData ->
                serviceData[serviceUuid]?.let { payload ->
                    onPayloadReceived(payload)
                }
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e("BleScanner", "Scan failed with error: $errorCode")
        }
    }
    
    fun startScan() {
        if (scanner == null) return
        
        val filter = ScanFilter.Builder()
            .setServiceUuid(serviceUuid)
            .build()
            
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
            
        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            Log.e("BleScanner", "Missing Bluetooth scan permission", e)
        }
    }
    
    fun stopScan() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            // Ignore missing permissions on stop
        }
    }
}
