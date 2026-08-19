package com.bitchat.emergency.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bitchat.emergency.ble.BleAdvertiser
import com.bitchat.emergency.ble.BleMeshManager
import com.bitchat.emergency.power.BatteryMonitor
import com.bitchat.emergency.power.PowerTierStateMachine

class MeshForegroundService : Service() {

    private lateinit var batteryMonitor: BatteryMonitor
    private val powerTierStateMachine = PowerTierStateMachine()
    private lateinit var meshManager: BleMeshManager

    @Volatile private var isRunning = false

    /** true if BLE 5 extended advertising is available on this device. */
    private var bleCapable = false

    override fun onCreate() {
        super.onCreate()
        batteryMonitor = BatteryMonitor(this)
        meshManager = BleMeshManager(this)
        
        requestBatteryOptimizationExemption()

        // --- BLE 5 capability check ---
        bleCapable = BleAdvertiser.isExtendedAdvertisingSupported(this)
        val maxAdvDataLen = BleAdvertiser.getMaxAdvertisingDataLength(this)
        Log.i(TAG, "BLE 5 extended advertising supported: $bleCapable, max data length: $maxAdvDataLen")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("mesh_channel", "Emergency Mesh", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        if (!bleCapable) {
            // Device lacks BLE 5 extended advertising. Report creation still works,
            // but mesh transmission is impossible — payloads exceed legacy BLE limits.
            Log.w(TAG, "BLE 5 extended advertising NOT supported. Mesh disabled.")
            val notification = NotificationCompat.Builder(this, "mesh_channel")
                .setContentTitle("Emergency Mesh — Incompatible Hardware")
                .setContentText("This device does not support BLE 5. Reports are saved locally but cannot be broadcast.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(true)
                .build()
            startForeground(1, notification)
            // Do NOT start the mesh manager — no silent failures.
            return
        }
        
        val notification = NotificationCompat.Builder(this, "mesh_channel")
            .setContentTitle("Emergency Mesh Active")
            .setContentText("Relaying emergency reports via BLE 5")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
            
        startForeground(1, notification)

        // Pass the controller's max advertising data length to the mesh manager
        // so PayloadPipeline can cap notes length at serialization time if needed.
        meshManager.maxAdvertisingDataLength = maxAdvDataLen
        
        isRunning = true
        
        Thread {
            while (isRunning) {
                val batteryLevel = batteryMonitor.getBatteryLevel()
                powerTierStateMachine.updateBatteryLevel(batteryLevel)
                val config = powerTierStateMachine.getCurrentBleConfig()
                
                meshManager.updateConfig(config)
                
                Thread.sleep(10000)
            }
        }.start()
        
        meshManager.start()
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Ignore exception if activity cannot be started
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        if (bleCapable) {
            meshManager.stop()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "MeshForegroundService"
    }
}
