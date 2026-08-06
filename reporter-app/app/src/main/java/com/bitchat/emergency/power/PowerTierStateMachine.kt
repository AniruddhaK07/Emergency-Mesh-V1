package com.bitchat.emergency.power

import com.bitchat.emergency.model.BleConfig
import com.bitchat.emergency.model.GpsMode
import com.bitchat.emergency.model.PowerTier

/**
 * Pure Kotlin state machine for power-tier management. No Android dependencies.
 *
 * Thresholds (fixed, not configurable at runtime):
 *   NORMAL   — battery > 40%:  3s scan / 7s advertise duty cycle, GPS on-demand
 *   CONSERVE — battery 15-40%: advertise-only every 30s, GPS at last-known-cached
 *   CRITICAL — battery < 15%:  single advertise burst every 5 min, then radio off
 */
class PowerTierStateMachine {

    var currentTier: PowerTier = PowerTier.NORMAL
        private set

    /**
     * Update battery level and recompute the current power tier.
     * Thresholds per spec:
     *   > 40  → NORMAL
     *   15-40 → CONSERVE  (inclusive of both boundaries)
     *   < 15  → CRITICAL
     */
    fun updateBatteryLevel(batteryLevel: Int): PowerTier {
        currentTier = when {
            batteryLevel < 15 -> PowerTier.CRITICAL
            batteryLevel <= 40 -> PowerTier.CONSERVE
            else -> PowerTier.NORMAL
        }
        return currentTier
    }

    fun getCurrentBleConfig(): BleConfig {
        return when (currentTier) {
            PowerTier.NORMAL -> BleConfig(
                scanIntervalMs = 10_000,   // 10s total cycle: 3s scan + 7s advertise
                scanWindowMs = 3_000,
                advertiseIntervalMs = 7_000,
                gpsMode = GpsMode.ON_DEMAND
            )
            PowerTier.CONSERVE -> BleConfig(
                scanIntervalMs = 0,        // No scanning in CONSERVE
                scanWindowMs = 0,
                advertiseIntervalMs = 30_000,
                gpsMode = GpsMode.LAST_KNOWN_CACHED
            )
            PowerTier.CRITICAL -> BleConfig(
                scanIntervalMs = 0,        // No scanning in CRITICAL
                scanWindowMs = 0,
                advertiseIntervalMs = 300_000, // 5 minutes
                gpsMode = GpsMode.DISABLED
            )
        }
    }
}
