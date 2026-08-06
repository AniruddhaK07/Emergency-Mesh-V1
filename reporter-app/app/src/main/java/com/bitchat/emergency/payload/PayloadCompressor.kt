package com.bitchat.emergency.payload

import net.jpountz.lz4.LZ4Factory
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PayloadCompressor {
    private val factory = LZ4Factory.fastestInstance()

    fun compress(data: ByteArray): ByteArray {
        val compressor = factory.fastCompressor()
        val maxCompressedLength = compressor.maxCompressedLength(data.size)
        
        val buffer = ByteBuffer.allocate(4 + maxCompressedLength).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(data.size)
        
        val compressedLength = compressor.compress(data, 0, data.size, buffer.array(), 4, maxCompressedLength)
        
        val result = ByteArray(4 + compressedLength)
        System.arraycopy(buffer.array(), 0, result, 0, result.size)
        return result
    }

    fun decompress(compressed: ByteArray, originalSize: Int = -1): ByteArray {
        if (compressed.size < 4) return ByteArray(0)
        
        val buffer = ByteBuffer.wrap(compressed).order(ByteOrder.LITTLE_ENDIAN)
        val storedOriginalSize = buffer.int
        
        val sizeToUse = if (originalSize > 0) originalSize else storedOriginalSize
        
        val decompressor = factory.fastDecompressor()
        val decompressed = ByteArray(sizeToUse)
        decompressor.decompress(compressed, 4, decompressed, 0, sizeToUse)
        
        return decompressed
    }
}
