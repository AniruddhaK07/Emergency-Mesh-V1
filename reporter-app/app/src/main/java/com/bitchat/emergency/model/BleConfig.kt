package com.bitchat.emergency.model

data class BleConfig(
    val scanIntervalMs: Long,
    val scanWindowMs: Long,
    val advertiseIntervalMs: Long,
    val gpsMode: GpsMode
)

enum class GpsMode {
    ON_DEMAND,
    LAST_KNOWN_CACHED,
    DISABLED
}
