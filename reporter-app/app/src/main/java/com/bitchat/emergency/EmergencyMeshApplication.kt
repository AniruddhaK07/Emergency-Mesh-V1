package com.bitchat.emergency

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log

import com.bitchat.emergency.service.MeshForegroundService

/**
 * Application subclass that starts the MeshForegroundService when the app
 * process starts. This ensures continuous background BLE scan/advertise
 * per the power-tier state machine, independent of which Activity is open.
 */
class EmergencyMeshApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application created, starting MeshForegroundService")
        startMeshService()
    }

    private fun startMeshService() {
        val serviceIntent = Intent(this, MeshForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            // On some OEMs, starting a foreground service can throw if the app
            // is in a restricted background state. Log and continue — the
            // BootReceiver will retry on next device reboot.
            Log.e(TAG, "Failed to start MeshForegroundService", e)
        }
    }

    companion object {
        private const val TAG = "EmergencyMeshApp"
    }
}
