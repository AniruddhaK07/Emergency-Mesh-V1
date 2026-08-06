import asyncio
import os
import struct
import uuid
from bleak import BleakScanner

from cryptography.hazmat.primitives.asymmetric import x25519
from cryptography.hazmat.primitives.ciphers.aead import ChaCha20Poly1305
import hmac
import hashlib
import lz4.block

SERVICE_UUID = "0000b17c-0000-1000-8000-00805f9b34fb"
PROTOCOL_NAME = b'Noise_N_25519_ChaChaPoly_SHA256'
TIER3_STATIC_PUBLIC_KEY_HEX = '7bfc76e82f21c7432de9155866a6947f49ba3cae8711ffc3af939c193b3d1644'

def load_private_key():
    key = os.environ.get("TIER3_PRIVATE_KEY")
    if key:
        return key.strip()
    key_file = os.path.join(os.path.dirname(__file__), '..', 'command-dashboard', '.tier3_key')
    if os.path.exists(key_file):
        with open(key_file, 'r') as f:
            return f.read().strip()
    raise RuntimeError("Tier 3 private key not found. Set TIER3_PRIVATE_KEY env var or create .tier3_key file.")

def hmac_sha256(key, data):
    return hmac.new(key, data, hashlib.sha256).digest()

def sha256(data):
    return hashlib.sha256(data).digest()

def noise_hkdf(ck, ikm):
    temp_key = hmac_sha256(ck, ikm)
    output1 = hmac_sha256(temp_key, b"\x01")
    output2 = hmac_sha256(temp_key, output1 + b"\x02")
    return output1, output2

def noise_n_decrypt(encrypted_blob):
    priv_hex = load_private_key()
    if len(encrypted_blob) < 48:
        raise ValueError("Encrypted blob too short for Noise_N")

    # 1. Initialize symmetric state
    # pad protocol name to 32 bytes with zeros
    h = bytearray(32)
    h[:len(PROTOCOL_NAME)] = PROTOCOL_NAME
    h = bytes(h)
    ck = h

    # 2. Pre-message: MixHash(rs)
    rs = bytes.fromhex(TIER3_STATIC_PUBLIC_KEY_HEX)
    h = sha256(h + rs)

    # 3. Read ephemeral public key (first 32 bytes)
    eph_pub_raw = encrypted_blob[:32]
    ciphertext_with_tag = encrypted_blob[32:]

    # MixHash(e.public)
    h = sha256(h + eph_pub_raw)

    # 4. es: DH(s, e)
    static_priv = x25519.X25519PrivateKey.from_private_bytes(bytes.fromhex(priv_hex))
    eph_pub = x25519.X25519PublicKey.from_public_bytes(eph_pub_raw)
    dh_result = static_priv.exchange(eph_pub)

    # 5. MixKey(DH result)
    ck, k = noise_hkdf(ck, dh_result)

    # 6. DecryptAndHash: ChaCha20-Poly1305 with key=k, nonce=0, AD=h
    nonce = bytes(12) # n=0
    chacha = ChaCha20Poly1305(k)
    
    # cryptography library expects exactly: ciphertext + tag concatenated
    try:
        plaintext = chacha.decrypt(nonce, ciphertext_with_tag, h)
        return plaintext
    except Exception as e:
        raise ValueError(f"Decryption failed: {e}")

EMERGENCY_TYPES = ['TRAPPED', 'INJURED', 'FIRE', 'NEED_EVAC']
SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

def parse_payload(payload):
    if len(payload) < 42:
        print(f"Payload too short: {len(payload)} bytes")
        return None

    # Parse 42-byte header
    version = payload[0]
    emergency_type = EMERGENCY_TYPES[payload[1]] if payload[1] < len(EMERGENCY_TYPES) else 'UNKNOWN'
    severity = SEVERITIES[payload[2]] if payload[2] < len(SEVERITIES) else 'UNKNOWN'
    casualty_count = struct.unpack_from("<H", payload, 3)[0]
    timestamp = struct.unpack_from("<q", payload, 5)[0]
    latitude = struct.unpack_from("<f", payload, 13)[0]
    longitude = struct.unpack_from("<f", payload, 17)[0]
    corroboration_count = struct.unpack_from("<H", payload, 21)[0]
    ttl = payload[23]
    
    # reportId: 16 bytes, big-endian UUID
    uuid_bytes = payload[24:40]
    report_id = str(uuid.UUID(bytes=uuid_bytes))
    
    notes_length = struct.unpack_from("<H", payload, 40)[0]
    notes = ""
    
    if notes_length > 0 and len(payload) > 42:
        encrypted_notes = payload[42:42+notes_length]
        print(f"Encrypted blob size: {len(encrypted_notes)}")
        try:
            decrypted_compressed = noise_n_decrypt(encrypted_notes)
            print(f"Decrypted successfully, length: {len(decrypted_compressed)}")
            
            # Decompress LZ4: first 4 bytes are original size (uint32 LE)
            original_size = struct.unpack_from("<I", decrypted_compressed, 0)[0]
            lz4_data = decrypted_compressed[4:]
            
            notes = lz4.block.decompress(lz4_data, uncompressed_size=original_size).decode('utf-8')
            print("Decompressed successfully.")
        except Exception as e:
            print(f"Failed to decrypt/decompress notes: {e}")
            notes = f"[Error: {e}]"

    return {
        "reportId": report_id,
        "version": version,
        "emergencyType": emergency_type,
        "severity": severity,
        "casualtyCount": casualty_count,
        "timestamp": timestamp,
        "latitude": latitude,
        "longitude": longitude,
        "corroborationCount": corroboration_count,
        "ttl": ttl,
        "notes": notes
    }

async def main():
    print(f"Scanning for BLE advertisements with Service UUID: {SERVICE_UUID}...")
    
    stop_event = asyncio.Event()

    def detection_callback(device, advertisement_data):
        service_data = advertisement_data.service_data
        
        # Bleak returns service UUIDs in lowercase
        target_uuid = SERVICE_UUID.lower()
        
        for uuid_key, payload in service_data.items():
            if uuid_key.lower() == target_uuid:
                print(f"\n--- Received Report from {device.address} ---")
                print(f"Raw payload size: {len(payload)} bytes")
                report = parse_payload(payload)
                if report:
                    for k, v in report.items():
                        print(f"  {k}: {v}")
                stop_event.set()

    scanner = BleakScanner(detection_callback)
    await scanner.start()
    
    print("Waiting for report (timeout 30s)...")
    try:
        await asyncio.wait_for(stop_event.wait(), timeout=30.0)
    except asyncio.TimeoutError:
        print("Scan timed out. No matching advertisement found.")
    finally:
        await scanner.stop()

if __name__ == "__main__":
    asyncio.run(main())
