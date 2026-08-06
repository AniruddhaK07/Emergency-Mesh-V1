package com.bitchat.emergency.ble

class PayloadDedup {
    private val seenIds = object : java.util.LinkedHashMap<String, Boolean>(1000, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 1000
        }
    }

    fun isDuplicate(payload: ByteArray): Boolean {
        if (payload.size < 40) return true // Invalid payload size
        
        val uuidBytes = ByteArray(16)
        System.arraycopy(payload, 24, uuidBytes, 0, 16)
        
        val hexId = uuidBytes.joinToString("") { "%02x".format(it) }
        
        if (seenIds.containsKey(hexId)) {
            return true
        }
        
        seenIds[hexId] = true
        return false
    }
}
