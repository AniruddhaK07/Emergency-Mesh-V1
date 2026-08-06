package com.bitchat.emergency.payload

import com.bitchat.emergency.model.EmergencyType
import com.bitchat.emergency.model.IncidentReport
import com.bitchat.emergency.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PayloadPipelineTest {

    @Test
    fun testDistinctCiphertexts() {
        val report = IncidentReport(
            emergencyType = EmergencyType.TRAPPED,
            casualtyCount = 2,
            severity = Severity.CRITICAL,
            notes = "Need medical assistance immediately",
            timestamp = 1620000000000L,
            latitude = 40.7128,
            longitude = -74.0060
        )
        
        val payload1 = PayloadPipeline.prepareForTransmission(report)
        val payload2 = PayloadPipeline.prepareForTransmission(report)
        
        var allMatch = true
        for (i in PayloadSerializer.HEADER_SIZE until payload1.size) {
            if (payload1[i] != payload2[i]) {
                allMatch = false
                break
            }
        }
        
        assertNotEquals("Ciphertexts must be distinct due to unique nonces", true, allMatch)
    }

    @Test
    fun testPlaintextHeaderReadable() {
        val report = IncidentReport(
            emergencyType = EmergencyType.FIRE,
            casualtyCount = 5,
            severity = Severity.HIGH,
            notes = "Test notes",
            timestamp = 1620000000000L,
            latitude = 37.7749,
            longitude = -122.4194
        )
        
        val payload = PayloadPipeline.prepareForTransmission(report)
        
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        
        // Verify version at byte 0
        assertEquals(1.toByte(), buffer.get(0))
        
        // Verify emergency type at byte 1 (FIRE is 2)
        assertEquals(2.toByte(), buffer.get(1))
        
        // Verify severity at byte 2 (HIGH is 2)
        assertEquals(2.toByte(), buffer.get(2))
        
        // Verify casualty count at byte 3
        assertEquals(5.toShort(), buffer.getShort(3))
        
        // Verify notesLength is updated to the encrypted size
        val encryptedLength = payload.size - PayloadSerializer.HEADER_SIZE
        assertEquals(encryptedLength.toShort(), buffer.getShort(40))
    }
}
