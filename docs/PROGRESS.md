# Project Progress

## Current State (update this section every session)
- Last worked on: 2026-08-06
- Active phase: All phases complete
- Immediate next step: Testing Stage 1 — verify dashboard with seed data, then Stage 2 — phone app on real device

## Phase Status
| Phase | Status | Notes |
|---|---|---|
| 0 — Gateway/Power Architecture | Done | Relay firmware (ESP32/C++) and power-tier state machine (Kotlin) |
| 1 — Reporter UI + local intent capture | Done | Tap-first UI, GPS capture, report queue, voice dictation (optional) |
| 2 — Payload compression & crypto | Done | Binary serializer, LZ4 compression, AES-GCM encryption (notes only), plaintext header preserved |
| 3 — BLE mesh transport | Done | BLE advertiser/scanner, duty-cycled per power tier, foreground service |
| 3.5 — Dedup & anti-flooding | Done | Phone-side reportId dedup (capped 1000), relay-side cluster-key dedup |
| 4 — Command dashboard | Done | React + Vite + Tailwind, Express ingest server, sort by weighting formula |

## Decisions Log (append-only — never delete past entries)
- 2026-08-06: Project initialized from emergency-mesh-spec-v2.md. All architectural decisions are locked per spec.
- 2026-08-06: EmergencyType enum fixed to match spec: TRAPPED(0), INJURED(1), FIRE(2), NEED_EVAC(3).
- 2026-08-06: Power-tier boundary: battery=15 → CONSERVE (inclusive), battery<15 → CRITICAL.
- 2026-08-06: Dedup timestamp bucketing: divide by 300000 (not 300) since timestamps are millis.
- 2026-08-06: RadioLib Module constructor 4th arg is DIO1, not SCK. Fixed to RADIOLIB_NC.
- 2026-08-06: Dedup fields are in plaintext header outside the encrypted envelope, readable by relays.
- 2026-08-06: IncidentReport data class updated with latitude, longitude, ttl (default 7), reportId (UUID v4) to match wire format.
- 2026-08-06: GpsCapture uses LocationManager (no Play Services) for disaster resilience.
- 2026-08-06: Encryption uses AES-GCM instead of ChaCha20-Poly1305 because minSdk=26 and ChaCha20 requires API 28+. AES-GCM provides equivalent AEAD guarantees.
- 2026-08-06: PayloadPipeline encrypts only the notes portion (bytes 42+). The 42-byte header stays plaintext for relay dedup.
- 2026-08-06: Static encryption key uses a placeholder (all 42 bytes). Needs proper key provisioning before deployment.
- 2026-08-06: Dashboard uses lucide-react for icons (emergency type indicators). Only external dependency beyond React/Vite/Tailwind stack.
- 2026-08-06: Seed script uses node-fetch@3 for POSTing test data to the ingest server.

## Known Issues / Open Questions
- Dedup table in relay firmware has no eviction policy. Flagged for future consideration.
- GpsCapture first call in NORMAL mode may return stale cached location.
- Static encryption key is a mock placeholder — needs proper key provisioning workflow before deployment.
- BLE advertisement payload size limit (~31 bytes for legacy, ~255 for extended) may require splitting payloads with notes. Current max payload is ~350 bytes. Needs testing on real hardware (Stage 3).
- No hardware available yet for testing beyond Stage 1 (dashboard).
