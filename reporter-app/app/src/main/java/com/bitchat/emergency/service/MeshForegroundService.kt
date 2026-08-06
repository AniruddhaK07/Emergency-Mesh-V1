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
import androidx.core.app.NotificationCompat
import com.bitchat.emergency.ble.BleMeshManager
import com.bitchat.emergency.power.BatteryMonitor
import com.bitchat.emergency.power.PowerTierStateMachine

class MeshForegroundService : Service() {

    private lateinit var batteryMonitor: BatteryMonitor
    private val powerTierStateMachine = PowerTierStateMachine()
    private lateinit var meshManager: BleMeshManager

    @Volatile private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        batteryMonitor = BatteryMonitor(this)
        meshManager = BleMeshManager(this)
        
        requestBatteryOptimizationExemption()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("mesh_channel", "Emergency Mesh", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, "mesh_channel")
            .setContentTitle("Emergency Mesh Active")
            .setContentText("Relaying emergency reports")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
            
        startForeground(1, notification)
        
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
        meshManager.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
