package com.bitchat.emergency.payload

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PayloadEncryptor {
    // Simplified encryption: AES-GCM (AEAD) as a fallback since minSdk 26 doesn't have ChaCha20-Poly1305.
    // This maintains the spirit of the spec (integrity + distinct ciphertexts per encryption) without heavy external crypto libs.
    private val staticKey: SecretKeySpec by lazy {
        val keyBytes = ByteArray(32) { 42.toByte() } // Mock provisioned static key
        SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)
        
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, staticKey, spec)
        
        val ciphertext = cipher.doFinal(data)
        
        val result = ByteArray(nonce.size + ciphertext.size)
        System.arraycopy(nonce, 0, result, 0, nonce.size)
        System.arraycopy(ciphertext, 0, result, nonce.size, ciphertext.size)
        return result
    }

    fun decrypt(encrypted: ByteArray): ByteArray {
        if (encrypted.size < 12) return ByteArray(0)
        
        val nonce = ByteArray(12)
        System.arraycopy(encrypted, 0, nonce, 0, 12)
        
        val ciphertext = ByteArray(encrypted.size - 12)
        System.arraycopy(encrypted, 12, ciphertext, 0, ciphertext.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, staticKey, spec)
        
        return cipher.doFinal(ciphertext)
    }
}
