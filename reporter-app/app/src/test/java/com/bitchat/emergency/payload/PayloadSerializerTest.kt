package com.bitchat.emergency.payload

import com.bitchat.emergency.model.EmergencyType
import com.bitchat.emergency.model.IncidentReport
import com.bitchat.emergency.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PayloadSerializerTest {

    @Test
    fun testRoundTrip() {
        val original = IncidentReport(
            emergencyType = EmergencyType.FIRE,
            casualtyCount = 5,
            severity = Severity.HIGH,
            notes = "Building is on fire, send halp",
            timestamp = 1620000000000L,
            latitude = 37.7749,
            longitude = -122.4194,
            corroborationCount = 2,
            ttl = 6,
            reportId = UUID.randomUUID().toString()
        )
        
        val serialized = PayloadSerializer.serialize(original)
        val deserialized = PayloadSerializer.deserialize(serialized)
        
        assertEquals(original.emergencyType, deserialized.emergencyType)
        assertEquals(original.casualtyCount, deserialized.casualtyCount)
        assertEquals(original.severity, deserialized.severity)
        assertEquals(original.notes, deserialized.notes)
        assertEquals(original.timestamp, deserialized.timestamp)
        assertEquals(original.latitude, deserialized.latitude, 0.0001)
        assertEquals(original.longitude, deserialized.longitude, 0.0001)
        assertEquals(original.corroborationCount, deserialized.corroborationCount)
        assertEquals(original.ttl, deserialized.ttl)
        assertEquals(original.reportId, deserialized.reportId)
    }

    @Test
    fun testNotesTruncation() {
        val longNotes = "A".repeat(300)
        val report = IncidentReport(
            emergencyType = EmergencyType.INJURED,
            casualtyCount = 1,
            severity = Severity.LOW,
            notes = longNotes,
            timestamp = 123456L,
            latitude = 0.0,
            longitude = 0.0
        )
        
        val serialized = PayloadSerializer.serialize(report)
        val deserialized = PayloadSerializer.deserialize(serialized)
        
        assertEquals(256, deserialized.notes.length)
        assertTrue(deserialized.notes.all { it == 'A' })
    }
}
