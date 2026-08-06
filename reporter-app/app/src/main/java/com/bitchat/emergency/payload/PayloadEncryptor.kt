package com.bitchat.emergency.payload

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implements Noise_N_25519_ChaChaPoly_SHA256 (one-way pattern).
 *
 * Noise_N: the sender knows the recipient's static public key and encrypts
 * using a fresh ephemeral keypair per message. No handshake, no back-channel.
 * Extracting the embedded public key from the APK gives zero decryption
 * capability because it is asymmetric — only the Tier 3 private key decrypts.
 *
 * Wire output: [32-byte ephemeral pubkey] [ciphertext + 16-byte Poly1305 tag]
 */
object PayloadEncryptor {

    private const val PROTOCOL_NAME = "Noise_N_25519_ChaChaPoly_SHA256"

    // Tier 3 command node's static X25519 public key (32 bytes).
    // The corresponding private key is held ONLY at Tier 3 (server/ingest.js).
    // Embedding this public key in the APK is safe — it can only encrypt, not decrypt.
    @Suppress("MagicNumber")
    private val TIER3_STATIC_PUBLIC_KEY = byteArrayOf(
        0x7b, 0xfc.toByte(), 0x76, 0xe8.toByte(),
        0x2f, 0x21, 0xc7.toByte(), 0x43,
        0x2d, 0xe9.toByte(), 0x15, 0x58,
        0x66, 0xa6.toByte(), 0x94.toByte(), 0x7f,
        0x49, 0xba.toByte(), 0x3c, 0xae.toByte(),
        0x87.toByte(), 0x11, 0xff.toByte(), 0xc3.toByte(),
        0xaf.toByte(), 0x93.toByte(), 0x9c.toByte(), 0x19,
        0x3b, 0x3d, 0x16, 0x44
    )

    // BouncyCastle provider instance used directly (not registered globally)
    // to avoid conflicts with Android's built-in stripped BouncyCastle.
    private val bcProvider = BouncyCastleProvider()

    /**
     * Encrypt data using Noise_N pattern.
     * Each call generates a fresh ephemeral X25519 keypair, guaranteeing
     * distinct ciphertexts even for identical plaintext.
     *
     * @return [32-byte ephemeral pubkey || ciphertext || 16-byte Poly1305 tag]
     */
    fun encrypt(data: ByteArray): ByteArray {
        // --- Noise_N handshake: -> e, es ---

        // 1. Initialize symmetric state
        //    Protocol name is 31 bytes, <= HASHLEN (32), so pad with zeros to 32.
        val protocolNameBytes = PROTOCOL_NAME.toByteArray(Charsets.US_ASCII)
        val h = ByteArray(32)
        System.arraycopy(protocolNameBytes, 0, h, 0, protocolNameBytes.size)
        var ck = h.clone()
        var hState = h.clone()

        // 2. Pre-message pattern: MixHash(rs)
        //    rs = responder's (Tier 3) static public key
        hState = sha256(hState + TIER3_STATIC_PUBLIC_KEY)

        // 3. Token 'e': generate ephemeral X25519 keypair
        val random = SecureRandom()
        val ephPriv = X25519PrivateKeyParameters(random)
        val ephPub = ephPriv.generatePublicKey()
        val ephPubBytes = ByteArray(32)
        ephPub.encode(ephPubBytes, 0)

        // MixHash(e.public)
        hState = sha256(hState + ephPubBytes)

        // 4. Token 'es': DH(ephemeral, rs)
        val agreement = X25519Agreement()
        agreement.init(ephPriv)
        val tier3Key = X25519PublicKeyParameters(TIER3_STATIC_PUBLIC_KEY, 0)
        val dhResult = ByteArray(32)
        agreement.calculateAgreement(tier3Key, dhResult, 0)

        // 5. MixKey(DH result) -> derive new ck and encryption key k
        val hkdfResult = hkdf(ck, dhResult)
        ck = hkdfResult.first
        val k = hkdfResult.second

        // 6. EncryptAndHash: ChaCha20-Poly1305 with key=k, nonce=0, AD=hState
        val nonce = ByteArray(12) // n=0 for first (and only) transport message
        val ciphertext = chacha20Poly1305Encrypt(k, nonce, hState, data)

        // Output: ephPubBytes (32) || ciphertext (includes 16-byte Poly1305 tag)
        val result = ByteArray(32 + ciphertext.size)
        System.arraycopy(ephPubBytes, 0, result, 0, 32)
        System.arraycopy(ciphertext, 0, result, 32, ciphertext.size)
        return result
    }

    /**
     * Decryption requires Tier 3's private key and is implemented in
     * command-dashboard/server/ingest.js, not on the phone.
     */
    fun decrypt(@Suppress("UNUSED_PARAMETER") encrypted: ByteArray): ByteArray {
        throw UnsupportedOperationException(
            "Noise_N decryption requires Tier 3 private key. " +
            "Use command-dashboard/server/ingest.js."
        )
    }

    // --- Noise framework primitives ---

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    /**
     * HKDF per Noise spec: extract-then-expand producing two 32-byte outputs.
     * output1 = new chaining key, output2 = encryption key.
     */
    private fun hkdf(chainingKey: ByteArray, inputKeyMaterial: ByteArray): Pair<ByteArray, ByteArray> {
        val tempKey = hmacSha256(chainingKey, inputKeyMaterial)
        val output1 = hmacSha256(tempKey, byteArrayOf(0x01))
        val output2 = hmacSha256(tempKey, output1 + byteArrayOf(0x02))
        return Pair(output1, output2)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * ChaCha20-Poly1305 AEAD encryption via BouncyCastle provider instance
     * (passed directly, not registered globally, to avoid Android provider conflicts).
     *
     * @return ciphertext || 16-byte Poly1305 authentication tag
     */
    private fun chacha20Poly1305Encrypt(
        key: ByteArray,
        nonce: ByteArray,
        ad: ByteArray,
        plaintext: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("ChaCha20-Poly1305", bcProvider)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
        cipher.updateAAD(ad)
        return cipher.doFinal(plaintext)
    }
}
