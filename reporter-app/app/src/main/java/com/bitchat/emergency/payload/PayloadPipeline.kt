package com.bitchat.emergency.payload

import com.bitchat.emergency.model.IncidentReport
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PayloadPipeline {

    fun prepareForTransmission(report: IncidentReport): ByteArray {
        val serialized = PayloadSerializer.serialize(report)
        
        val headerSize = PayloadSerializer.HEADER_SIZE
        val notesLength = serialized.size - headerSize
        
        if (notesLength == 0) {
            return serialized
        }
        
        val notesBytes = ByteArray(notesLength)
        System.arraycopy(serialized, headerSize, notesBytes, 0, notesLength)
        
        val compressed = PayloadCompressor.compress(notesBytes)
        val encrypted = PayloadEncryptor.encrypt(compressed)
        
        val finalPayload = ByteArray(headerSize + encrypted.size)
        System.arraycopy(serialized, 0, finalPayload, 0, headerSize)
        System.arraycopy(encrypted, 0, finalPayload, headerSize, encrypted.size)
        
        val buffer = ByteBuffer.wrap(finalPayload).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(40)
        buffer.putShort(encrypted.size.toShort())
        
        return finalPayload
    }
}
