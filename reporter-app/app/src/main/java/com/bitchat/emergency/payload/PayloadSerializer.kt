package com.bitchat.emergency.payload

import com.bitchat.emergency.model.EmergencyType
import com.bitchat.emergency.model.IncidentReport
import com.bitchat.emergency.model.Severity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object PayloadSerializer {
    const val HEADER_SIZE = 42

    fun serialize(report: IncidentReport): ByteArray {
        val notesBytes = report.notes.toByteArray(Charsets.UTF_8).take(256).toByteArray()
        val capacity = HEADER_SIZE + notesBytes.size
        val buffer = ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(1.toByte()) // version
        buffer.put(report.emergencyType.ordinal.toByte())
        buffer.put(report.severity.ordinal.toByte())
        buffer.putShort(report.casualtyCount.toShort())
        buffer.putLong(report.timestamp)
        buffer.putFloat(report.latitude.toFloat())
        buffer.putFloat(report.longitude.toFloat())
        buffer.putShort(report.corroborationCount.toShort())
        buffer.put(report.ttl.toByte())
        
        val uuid = UUID.fromString(report.reportId)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        buffer.putShort(notesBytes.size.toShort())
        buffer.put(notesBytes)
        
        return buffer.array()
    }

    fun deserialize(bytes: ByteArray): IncidentReport {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        
        val version = buffer.get()
        val emergencyType = EmergencyType.values()[buffer.get().toInt()]
        val severity = Severity.values()[buffer.get().toInt()]
        val casualtyCount = buffer.short.toInt()
        val timestamp = buffer.long
        val latitude = buffer.float.toDouble()
        val longitude = buffer.float.toDouble()
        val corroborationCount = buffer.short.toInt()
        val ttl = buffer.get().toInt()
        
        buffer.order(ByteOrder.BIG_ENDIAN)
        val msb = buffer.long
        val lsb = buffer.long
        val uuid = UUID(msb, lsb).toString()
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        val notesLength = buffer.short.toInt()
        val notesBytes = ByteArray(notesLength)
        buffer.get(notesBytes)
        val notes = String(notesBytes, Charsets.UTF_8)
        
        return IncidentReport(
            emergencyType = emergencyType,
            casualtyCount = casualtyCount,
            severity = severity,
            notes = notes,
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            corroborationCount = corroborationCount,
            ttl = ttl,
            reportId = uuid
        )
    }
}
