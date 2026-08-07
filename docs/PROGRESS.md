# Project Progress

## Current State (update this section every session)
- Last worked on: 2026-08-07
- Active phase: Testing & Audits (Phases 0-4 architecture implementations complete)
- Immediate next step: Resolve local environment issues (missing Android SDK, Gradle, and Bluetooth radio) to unblock Testing Stages 2 and 3.

## Phase Status
| Phase | Status | Notes |
|---|---|---|
| 0 — Gateway/Power Architecture | Done | Relay firmware (ESP32/C++) and power-tier state machine (Kotlin) |
| 1 — Reporter UI + local intent capture | Done | Tap-first UI, GPS capture, report queue, voice dictation (optional) |
| 2 — Payload compression & crypto | Done | Binary serializer (43-byte header), LZ4 compression, Noise_N_25519_ChaChaPoly_SHA256 encryption. |
| 3 — BLE mesh transport | Done | BLE advertiser/scanner, foreground service with Android 14 API compliance and auto-resurrect. |
| 3.5 — Dedup & anti-flooding | Done | Phone-side reportId dedup, relay-side cluster-key dedup with header-only original-identity corroboration updates. |
| 4 — Command dashboard | Done | React + Vite + Tailwind, Express ingest server, sort by weighting formula. |

## Decisions Log (append-only — never delete past entries)
- 2026-08-06: Project initialized from emergency-mesh-spec-v2.md. All architectural decisions are locked per spec.
- 2026-08-06: EmergencyType enum fixed to match spec: TRAPPED(0), INJURED(1), FIRE(2), NEED_EVAC(3).
- 2026-08-06: Power-tier boundary: battery=15 → CONSERVE (inclusive), battery<15 → CRITICAL.
- 2026-08-06: Dedup timestamp bucketing: divide by 300000 (not 300) since timestamps are millis.
- 2026-08-06: RadioLib Module constructor 4th arg is DIO1, not SCK. Fixed to RADIOLIB_NC.
- 2026-08-06: Dedup fields are in plaintext header outside the encrypted envelope, readable by relays.
- 2026-08-06: IncidentReport data class updated with latitude, longitude, ttl (default 7), reportId (UUID v4) to match wire format.
- 2026-08-06: GpsCapture uses LocationManager (no Play Services) for disaster resilience.
- 2026-08-06: DEVIATION FROM SPEC: The project spec mandates Noise_XX_25519_ChaChaPoly_SHA256 for payload encryption. However, the implemented algorithm in PayloadEncryptor is AES-GCM. This was chosen because minSdk=26 and ChaCha20 requires Android API 28+ via javax.crypto. AES-GCM provides equivalent AEAD guarantees without external dependencies, but this is a deviation from the locked spec.
- 2026-08-06: The wire format uses a plaintext-header / encrypted-body split. The first 42 bytes (header) stay plaintext, and only the notes portion (bytes 42+) is encrypted. This design resolves the earlier open question of how relays compute dedup cluster keys without holding decryption keys.
- 2026-08-06: Static encryption key uses a placeholder (all 42 bytes). Needs proper key provisioning before deployment.
- 2026-08-06: Dashboard uses lucide-react for icons (emergency type indicators). Only external dependency beyond React/Vite/Tailwind stack.
- 2026-08-06: Seed script uses node-fetch@3 for POSTing test data to the ingest server.
- 2026-08-06: SECURITY AUDIT FINDING: The key-management flow for PayloadEncryptor.kt deviates from the locked spec (Noise_XX_25519_ChaChaPoly_SHA256). Currently, encryption uses AES-GCM with a hardcoded static key (a 32-byte array filled with the value `42`). A random 12-byte nonce is generated per-encryption using `SecureRandom`. On the receiving end (Tier 3), no actual decryption logic exists in the codebase yet; the dashboard's ingest server expects already-decrypted JSON, and the gateway script responsible for decryption (planned for Testing Stage 3) is entirely unwritten.
- 2026-08-06: SPEC CORRECTION — Noise_XX replaced with Noise_N: The locked spec mandated Noise_XX_25519_ChaChaPoly_SHA256. However, Noise_XX requires a multi-round interactive handshake between two parties. The Tier 1→Tier 2 BLE transport is a unidirectional broadcast (BLE advertisements) with no back-channel for the relay to send handshake responses. Noise_XX is architecturally incompatible with this transport. The fix is Noise_N (one-way pattern): the sender knows the responder's static public key and encrypts using a fresh ephemeral X25519 keypair per message. No handshake or back-channel is needed. The Tier 3 static public key is embedded in the APK at build time — since it is a public key, extracting it from a decompiled APK gives zero decryption capability (unlike a symmetric PSK which would be a single point of compromise). Tier 1 (PayloadEncryptor.kt) uses BouncyCastle (bcprov-jdk18on:1.78.1) for X25519 DH and ChaCha20-Poly1305 AEAD to support minSdk=26. Tier 3 (ingest.js) uses Node.js built-in crypto module for the matching decryption. This unblocks Testing Stage 3.
- 2026-08-06: TESTING STAGE 3: Implemented Python script (`tests/stage3_test.py`) using `bleak` to scan BLE advertisements and decode/decrypt payloads matching `ingest.js` logic. Execution failed locally because the host machine's Bluetooth radio is not powered on/available. Script is ready for execution on a machine with functional Bluetooth (implemented by: Gemini 3.1 Pro).
- 2026-08-07: TESTING STAGE 2 (Android Build): Attempted to verify the Android build environment for the reporter app. Discovered that the Android SDK, Gradle, and the Gradle Wrapper (`gradlew`) are entirely missing from this environment. This blocks any attempt to build the APK (e.g., `assembleDebug`) or run Testing Stage 2 locally. A proper Android SDK installation and project initialization (including wrapper scripts) are required to proceed.
- 2026-08-07: ARCHITECTURAL BOTTLENECK AUDIT - 2.1 (Full TDMA Scheduling): Rejected. Requires Tier 2 to receive, parse, and act on synchronization packets from Tier 3, contradicting the locked design where relays forward ciphertext blindly with zero payload-level logic. LoRa's built-in Channel Activity Detection (CAD) with randomized backoff provides collision mitigation without sync infrastructure.
- 2026-08-07: ARCHITECTURAL BOTTLENECK AUDIT - 2.2 (OS/OEM background BLE throttling): Accepted. Added FOREGROUND_SERVICE_CONNECTED_DEVICE permission, foregroundServiceType="connectedDevice", and BOOT_COMPLETED receiver to auto-resurrect the BLE service. The strict ScanFilter targeting the custom mesh service UUID was already correctly implemented.
- 2026-08-07: ARCHITECTURAL BOTTLENECK AUDIT - 2.3 (Regulatory Duty Cycle Lockout): Accepted. Relay-node LoRa config was already locked to SF7. The existing dedup logic correctly avoided re-sending full payloads but suffered a regression where it dropped them entirely, failing to update Tier 3. Fixed by transmitting a minimal 43-byte header-only packet containing the incremented corroborationCount (notesLength = 0).
- 2026-08-07: ARCHITECTURAL BOTTLENECK AUDIT - 2.4 (IMU-triggered rebroadcast on motion): Rejected. Power-tier duty cycling already re-advertises in CONSERVE mode. Motion-triggered rebroadcast adds drain and complexity for marginal gain.
- 2026-08-07: ARCHITECTURAL BOTTLENECK AUDIT - 2.5 (RSSI-based relative triangulation): Rejected. RSSI-based localization is unreliable and risks misdirecting rescuers.
- 2026-08-07: ARCHITECTURAL BOTTLENECK AUDIT - 2.5 (GPS Serialization Failure): Accepted. Added a NULL_LOC byte flag (hasLocation) to the binary struct. Incremented fixed header size from 42 to 43 bytes, shifting subsequent offsets. WIRING.md and serialization code updated.
- 2026-08-07: ARCHITECTURAL DISCONNECT FIX - Tier 2 vs Tier 3 Corroboration Merge: A bug was found where the relay deduplicates by `clusterKey` but Tier 3 merges by `reportId`. Because the relay originally transmitted the duplicate reporter's randomly generated `reportId` in the header update, Tier 3 couldn't match it to the original report, creating empty "ghost" reports on the dashboard. Option A (Tier 3 computing `clusterKey`) was rejected because it duplicates complex logic and breaks the `NULL_LOC` clustering safety assumption (which relies on the single physical relay's range acting as the geographic bound). Option B was accepted: `DedupEntry` in the relay now caches the original `reportId` (16 bytes) and injects it into outgoing duplicate packets. Tier 3's `ingest.js` was also updated to merge safely by pinning identity fields (severity, casualtyCount, hasLocation, lat, lon) to the original stored record and using `Math.max()` to protect `corroborationCount` from out-of-order LoRa packets.

## Known Issues / Open Questions
- Dedup table in relay firmware has no eviction policy. Flagged for future consideration.
- GpsCapture first call in NORMAL mode may return stale cached location.
- BLE advertisement payload size limit (~31 bytes for legacy, ~255 for extended) may require splitting payloads with notes. Current max payload is ~370 bytes with Noise_N overhead. Needs testing on real hardware (Stage 3).
- No hardware available yet for testing beyond Stage 1 (dashboard). Both Bluetooth hardware and Android SDK/Gradle are missing in the local environment, blocking Stages 2 and 3.
- Tier 3 private key loaded from TIER3_PRIVATE_KEY env var or gitignored .tier3_key file (never committed to source). For production deployment, use a proper secrets manager.
