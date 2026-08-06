package com.bitchat.emergency.model

// Wire format byte 1: 0=Trapped, 1=Injured, 2=Fire, 3=NeedEvac
enum class EmergencyType {
    TRAPPED, INJURED, FIRE, NEED_EVAC
}

enum class Severity {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class IncidentReport(
    val emergencyType: EmergencyType,
    val casualtyCount: Int,
    val severity: Severity,
    val notes: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val corroborationCount: Int = 1,
    val ttl: Int = 7,
    val reportId: String = java.util.UUID.randomUUID().toString()
)
