package com.bitchat.emergency.payload

import com.bitchat.emergency.model.IncidentReport
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PayloadPipeline {

    /**
     * Overhead added by Noise_N encryption to the compressed notes:
     * 32 bytes ephemeral X25519 public key + 16 bytes Poly1305 auth tag.
     */
    private const val NOISE_N_OVERHEAD = 48

    /**
     * LZ4 prepends a 4-byte original-size header before the compressed data.
     */
    private const val LZ4_HEADER = 4

    /**
     * Prepare a report for BLE transmission.
     *
     * @param report The incident report to serialize and encrypt.
     * @param maxAdvertisingDataLength The BLE controller's maximum advertising data
     *   length (from [BluetoothAdapter.getLeMaximumAdvertisingDataLength]).
     *   If the full payload would exceed this limit, notes are truncated at the
     *   UTF-8 byte level before compression/encryption so the final payload fits.
     *   A value of 0 or negative means no limit (used in unit tests).
     */
    fun prepareForTransmission(
        report: IncidentReport,
        maxAdvertisingDataLength: Int = 0
    ): ByteArray {
        val reportToSerialize = if (maxAdvertisingDataLength > 0) {
            capNotesForControllerLimit(report, maxAdvertisingDataLength)
        } else {
            report
        }

        val serialized = PayloadSerializer.serialize(reportToSerialize)
        
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
        buffer.position(41)
        buffer.putShort(encrypted.size.toShort())
        
        return finalPayload
    }

    /**
     * Truncate notes so the final encrypted payload fits within the controller's
     * advertising data limit.
     *
     * The worst-case encrypted blob size for N bytes of UTF-8 notes is:
     *   NOISE_N_OVERHEAD (48) + LZ4_HEADER (4) + N  (LZ4 can expand incompressible data)
     * Total payload = HEADER_SIZE (43) + encrypted blob
     *
     * So max notes bytes = maxAdvLen - HEADER_SIZE - NOISE_N_OVERHEAD - LZ4_HEADER
     *
     * This is conservative (LZ4 usually compresses), but guarantees the payload
     * always fits without a retry loop.
     */
    private fun capNotesForControllerLimit(
        report: IncidentReport,
        maxAdvLen: Int
    ): IncidentReport {
        // Maximum raw notes bytes that can survive the pipeline and still fit.
        val maxNotesBytes = maxAdvLen - PayloadSerializer.HEADER_SIZE - NOISE_N_OVERHEAD - LZ4_HEADER
        if (maxNotesBytes <= 0) {
            // Controller can barely fit the header — strip notes entirely.
            return report.copy(notes = "")
        }

        val notesBytes = report.notes.toByteArray(Charsets.UTF_8)
        if (notesBytes.size <= maxNotesBytes) {
            return report // Notes already fit.
        }

        // Truncate at a safe UTF-8 boundary (don't split multi-byte chars).
        val truncated = truncateUtf8(notesBytes, maxNotesBytes)
        return report.copy(notes = String(truncated, Charsets.UTF_8))
    }

    /**
     * Truncate a UTF-8 byte array to at most [maxBytes] without splitting
     * multi-byte characters. Walks backward from the cut point to find the
     * last valid character boundary.
     */
    private fun truncateUtf8(bytes: ByteArray, maxBytes: Int): ByteArray {
        if (maxBytes >= bytes.size) return bytes
        var end = maxBytes
        // Walk back past any continuation bytes (10xxxxxx pattern).
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) {
            end--
        }
        // If we landed on a leading byte of a multi-byte sequence that would
        // be incomplete, skip it too.
        if (end > 0 && (bytes[end - 1].toInt() and 0x80) != 0) {
            // Check if the leading byte's sequence would extend past our cut.
            val lead = bytes[end - 1].toInt() and 0xFF
            val seqLen = when {
                lead < 0x80 -> 1
                lead < 0xE0 -> 2
                lead < 0xF0 -> 3
                else -> 4
            }
            if (end - 1 + seqLen > maxBytes) {
                end-- // Drop the incomplete leading byte.
            }
        }
        return bytes.copyOf(end)
    }
}
