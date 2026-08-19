# Wiring & Systems Diagrams

## System-Level Architecture

```mermaid
graph LR
    subgraph "Tier 1 — Reporter Phones"
        P1["Phone A"]
        P2["Phone B"]
        P3["Phone N..."]
    end

    subgraph "Tier 2 — Relay Nodes"
        R1["ESP32-S3 + RFM95<br/>~$60/unit<br/>Solar/Battery"]
        R2["ESP32-S3 + RFM95"]
    end

    subgraph "Tier 3 — Command Node"
        C1["Laptop/Server<br/>+ Starlink Terminal"]
        D["React Dashboard"]
    end

    P1 -- "BLE 5 ext adv<br/>10-100m" --> R1
    P2 -- "BLE 5 ext adv<br/>10-100m" --> R1
    P3 -- "BLE 5 ext adv<br/>10-100m" --> R2
    P1 -. "BLE 5 mesh<br/>store-and-forward" .-> P2
    R1 -- "LoRa 915MHz<br/>2-15km<br/>encrypted blob" --> C1
    R2 -- "LoRa 915MHz<br/>2-15km<br/>encrypted blob" --> C1
    C1 -- "Decrypt + Dedup" --> D
    C1 -. "Satellite/Starlink<br/>uplink (only internet)" .-> Internet["External Systems"]
```

## Hardware Requirements

| Tier | Component | Hard Requirement | Reason |
|------|-----------|-----------------|--------|
| 1 — Phones | Android device | **BLE 5 with extended advertising support** | Payloads (43–370 bytes) exceed legacy BLE's ~31-byte limit. Extended advertising PDUs (`ADV_EXT_IND`/`AUX_ADV_IND`) cannot be decoded by BLE 4.x radios. |
| 1 — Phones | Android API | **minSdk 26** (Android 8.0) | `AdvertisingSet` API and BouncyCastle ChaCha20-Poly1305 AEAD both require API 26+. |
| 2 — Relays | ESP32 variant | **ESP32-S3 or ESP32-C3** (BLE 5.0) | Classic ESP32 (ESP32-WROOM-32) has BLE 4.2 only — cannot receive extended advertising PDUs. |
| 2 — Relays | LoRa module | SX1276 / RFM95W | Unchanged from original spec. |

**Fallback on devices without BLE 5:** Report creation (UI, GPS, queue, encryption) still works.
Mesh transmission is disabled at startup with a user-facing notification. The app checks
`BluetoothAdapter.isLeExtendedAdvertisingSupported()` and `getLeMaximumAdvertisingDataLength()`
before enabling the BLE mesh service. No silent failures.

## Power-Tier State Machine (Phone-Side)

```mermaid
stateDiagram-v2
    [*] --> NORMAL

    NORMAL --> CONSERVE : battery <= 40%
    CONSERVE --> NORMAL : battery > 40%
    CONSERVE --> CRITICAL : battery <= 15%
    CRITICAL --> CONSERVE : battery > 15%

    state NORMAL {
        note right of NORMAL
            Battery > 40%
            BLE: 3s scan / 7s advertise duty cycle
            GPS: on-demand (fresh fix per report)
        end note
    }

    state CONSERVE {
        note right of CONSERVE
            Battery 15–40%
            BLE: advertise-only every 30s (no scanning)
            GPS: last-known-cached (no new fixes)
        end note
    }

    state CRITICAL {
        note right of CRITICAL
            Battery < 15%
            BLE: single advertise burst every 5 min
            Payload: last unsent report only
            Then radio off, screen stays locked
        end note
    }
```

## Phase 0 Sequence — Relay Forwarding (BLE → LoRa)

```mermaid
sequenceDiagram
    participant Phone as Reporter Phone (Tier 1)
    participant Relay as ESP32-S3 Relay (Tier 2)
    participant Cmd as Command Node (Tier 3)

    Phone->>Relay: BLE advertisement (encrypted blob)
    Relay->>Relay: Read blob (no decryption)
    Relay->>Relay: Compute cluster key for dedup
    Relay->>Relay: Check dedup table
    alt New cluster key
        Relay->>Relay: Store in dedup table (corroborationCount = 1, original reportId)
        Relay->>Cmd: LoRa TX (encrypted blob)
    else Existing cluster key
        Relay->>Relay: Increment corroborationCount
        Relay->>Relay: Overwrite duplicate's reportId with original
        Relay->>Cmd: LoRa TX (updated corroboration only)
    end
    Cmd->>Cmd: Decrypt with Tier 3 key material
    Cmd->>Cmd: Ingest into dashboard
```

## Phase 1 Sequence — Report Creation (on Reporter Phone)

```mermaid
sequenceDiagram
    participant User
    participant UI as ReportActivity
    participant GPS as GpsCapture
    participant Queue as ReportQueue
    participant SM as PowerTierStateMachine

    User->>UI: Tap emergency type button (1 of 4)
    UI->>UI: Highlight selected, deselect others
    User->>UI: Select severity (radio group)
    User->>UI: Adjust casualty count (+/-)
    opt Voice dictation (mic permission granted)
        User->>UI: Tap mic button
        UI->>UI: Start SpeechRecognizer
        UI->>UI: Populate notes field with transcript
        User->>UI: Review/edit transcribed text
    end
    User->>UI: Tap SEND REPORT
    UI->>SM: Check current power tier
    SM-->>UI: Return tier (NORMAL/CONSERVE/CRITICAL)
    alt NORMAL tier
        UI->>GPS: Request fresh GPS fix
        GPS-->>UI: (latitude, longitude)
    else CONSERVE or CRITICAL tier
        UI->>GPS: Get last-known cached location
        GPS-->>UI: (cached latitude, longitude)
    end
    UI->>UI: Create IncidentReport with timestamp
    UI->>Queue: enqueue(report)
    Queue->>Queue: Persist to disk (JSON)
    UI->>UI: Show Toast confirmation, reset form
```


## Phase 2 Sequence — Payload Pipeline (on Reporter Phone)

```mermaid
sequenceDiagram
    participant UI as Reporter UI
    participant Ser as PayloadSerializer
    participant Pipe as PayloadPipeline
    participant Comp as LZ4 Compressor
    participant Enc as Noise_N Encryptor
    participant BLE as BLE Advertiser

    UI->>Ser: IncidentReport data class
    Ser->>Ser: Pack into 42-byte fixed header + notes
    Ser->>Pipe: Full binary struct
    Pipe->>Pipe: Split: header (bytes 0-41) + notes (bytes 42+)
    Pipe->>Comp: Notes bytes only
    Comp->>Comp: LZ4 compress (prepend 4-byte original size)
    Comp->>Enc: Compressed notes
    Enc->>Enc: Generate ephemeral X25519 keypair
    Enc->>Enc: DH(ephemeral, Tier3 static pubkey)
    Enc->>Enc: HKDF-SHA256 derive symmetric key
    Enc->>Enc: ChaCha20-Poly1305 encrypt (nonce=0, AD=handshake hash)
    Enc->>Pipe: ephemeral pubkey (32) + ciphertext + Poly1305 tag (16)
    Pipe->>Pipe: Reassemble: plaintext header + encrypted notes
    Pipe->>Pipe: Update notesLength field (offset 40) to encrypted size
    Pipe->>BLE: Final payload (header readable by relays)
```

## Phase 3 Sequence — BLE Mesh Transport (Phone ↔ Phone ↔ Relay)

```mermaid
sequenceDiagram
    participant Q as ReportQueue
    participant Mesh as BleMeshManager
    participant Adv as BleAdvertiser
    participant Scan as BleScanner
    participant Dedup as PayloadDedup
    participant SM as PowerTierStateMachine

    loop Every duty cycle wake
        Mesh->>SM: Get current BleConfig
        SM-->>Mesh: BleConfig (scan/advertise timing)

        alt NORMAL tier
            Mesh->>Q: Dequeue next report
            Q-->>Mesh: Report payload
            Mesh->>Adv: Advertise payload (7s window)
            Mesh->>Scan: Scan for peers (3s window)
            Scan-->>Mesh: Received payload from peer
            Mesh->>Dedup: Check reportId (bytes 24-39)
            alt Not seen before
                Dedup-->>Mesh: New — accept
                Mesh->>Mesh: Decrement TTL (byte 23)
                alt TTL > 0
                    Mesh->>Mesh: Add to relay queue for re-advertisement
                else TTL = 0
                    Mesh->>Mesh: Drop payload
                end
            else Already seen
                Dedup-->>Mesh: Duplicate — discard
            end

        else CONSERVE tier
            Mesh->>Q: Dequeue next report
            Mesh->>Adv: Advertise payload (30s interval)
            Note over Scan: Scanner disabled

        else CRITICAL tier
            Mesh->>Q: Get last unsent report only
            Mesh->>Adv: Single burst advertise
            Note over Adv: Radio off after burst
        end
    end
```

## Wire Format Table (Source of Truth)

This table defines the binary struct layout for the incident report payload
**before** compression and encryption. Any change to this table must be
reflected in the serialization code in the same commit.

| Byte Offset | Field             | Width (bytes) | Type / Encoding                          |
|-------------|-------------------|---------------|------------------------------------------|
| 0           | version           | 1             | uint8 — protocol version (currently `1`) |
| 1           | emergencyType     | 1             | uint8 enum: 0=Trapped, 1=Injured, 2=Fire, 3=NeedEvac |
| 2           | severity          | 1             | uint8 enum: 0=Low, 1=Medium, 2=High, 3=Critical |
| 3           | casualtyCount     | 2             | uint16 LE                                |
| 5           | timestamp         | 8             | int64 LE — Unix epoch millis             |
| 13          | hasLocation       | 1             | uint8 — 1=valid GPS, 0=NULL_LOC          |
| 14          | latitude          | 4             | float32 LE (IEEE 754)                    |
| 18          | longitude         | 4             | float32 LE (IEEE 754)                    |
| 22          | corroborationCount| 2             | uint16 LE — set/incremented by relay     |
| 24          | ttl               | 1             | uint8 — hop count, decremented per relay |
| 25          | reportId          | 16            | UUID v4, 128-bit, big-endian             |
| 41          | notesLength       | 2             | uint16 LE — byte length of notes field   |
| 43          | notes             | 0–256         | UTF-8 string, max 256 bytes              |

**Total fixed header:** 43 bytes. **Max payload (with notes):** 299 bytes.

**Security Split (Implementation Detail):** The first 43 bytes (the fixed header) are
transmitted in plaintext so that relays can read dedup fields (like `reportId`) and
mutate routing fields (like `ttl` and `corroborationCount`) without holding decryption
keys. Only the `notes` field (bytes 43+) is compressed with LZ4 and encrypted.

**Encrypted Notes Blob Format (Noise_N_25519_ChaChaPoly_SHA256):**

| Offset within blob | Field              | Width (bytes) | Description                                       |
|--------------------|--------------------|---------------|---------------------------------------------------|
| 0                  | ephemeral pubkey   | 32            | X25519 ephemeral public key (unique per message)  |
| 32                 | ciphertext         | variable      | ChaCha20-Poly1305 encrypted (LZ4-compressed notes)|
| 32 + ct_len        | auth tag           | 16            | Poly1305 authentication tag                       |

The `notesLength` field at header offset 41 stores the total length of this encrypted
blob (32 + ciphertext_len + 16). Tier 3 uses its static X25519 private key to perform
DH with the ephemeral public key, derive the symmetric key via HKDF-SHA256, and
decrypt. Extracting the embedded public key from the APK gives zero decryption
capability because the key relationship is asymmetric.

After LZ4 compression and Noise_N encryption, expect ~320–370 bytes worst case
for a full payload (20 bytes more than the previous AES-GCM stub due to the 32-byte
ephemeral key replacing the 12-byte nonce) — still within BLE extended advertisement
and LoRa packet limits.
