package com.bitchat.emergency.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.bitchat.emergency.model.PowerTier
import com.bitchat.emergency.power.BatteryMonitor
import com.bitchat.emergency.power.PowerTierStateMachine

class GpsCapture(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val batteryMonitor = BatteryMonitor(context)
    private val stateMachine = PowerTierStateMachine()

    // Returns a pair of latitude, longitude. Default to 0.0, 0.0 if unable to get location.
    fun getLocation(): Pair<Double, Double> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Pair(0.0, 0.0)
        }

        val tier = stateMachine.updateBatteryLevel(batteryMonitor.getBatteryLevel())

        var location: Location? = null
        try {
            val providers = locationManager.getProviders(true)
            for (provider in providers) {
                val l = locationManager.getLastKnownLocation(provider)
                if (l != null) {
                    if (location == null || l.time > location.time) {
                        location = l
                    }
                }
            }
        } catch (e: SecurityException) {
            // Ignore
        }

        if (tier == PowerTier.CONSERVE || tier == PowerTier.CRITICAL) {
            return if (location != null) Pair(location.latitude, location.longitude) else Pair(0.0, 0.0)
        }

        // Try single update if NORMAL
        if (location == null) {
            try {
                // For a true single update synchronously, this is complex with LocationManager.
                // We'll rely on last known location to keep it simple and synchronous as required,
                // otherwise this method would need to be async (callback/suspend).
                // Given the constraints (utilitarian, simple), returning the best cached is optimal,
                // but we can request a single update to prime the cache for the NEXT call.
                val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                locationManager.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(loc: Location) {}
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }, null)
            } catch (e: SecurityException) {
                // Ignore
            } catch (e: IllegalArgumentException) {
                // Ignore
            }
        }

        return if (location != null) Pair(location.latitude, location.longitude) else Pair(0.0, 0.0)
    }
}
