# Testing Plan

## Stage 1 — Dashboard (no hardware needed)
- [ ] `npm run dev`, feed hand-written JSON matching the decrypted payload shape.
- Validates: sort weighting, UI, ingest server — independent of everything else.

## Stage 2 — Phone app, solo (needs 1 phone)
- [ ] `adb install` onto a real device — Android emulators do not support real BLE radio between instances, so this cannot be emulator-only past this stage.
- Validates: tap-first UI, GPS capture, local queueing, power-tier state machine (mock battery levels for CONSERVE/CRITICAL transitions).

## Stage 3 — Phone-to-laptop BLE (needs 1 phone + laptop's built-in BLE)
- [ ] Python script on laptop using `bleak` (cross-platform BLE lib) scans for and decrypts the phone's advertised payload — laptop temporarily holds the Tier 3 key material for this test only.
- Validates: wire format, LZ4 round-trip, Noise handshake + encryption — the whole Phase 2 pipeline — without waiting on any relay hardware.

## Stage 4 — Phone-to-phone BLE mesh (needs 2 phones)
- [ ] Validates: actual store-and-forward, TTL decrement, dedup logic from Phase 3.5, all in the same room, no relay node involved yet.

## Stage 5 — ESP32 LoRa bench test (needs 2× ESP32 + RFM95)
- [ ] Modules a foot apart. Confirms bytes sent from one arrive intact at the other.
- Validates: LoRa hop correctness — NOT range. Range is a separate, later, outdoor test and is not a blocker for anything else in this plan.

## Stage 6 — Full chain
- [ ] Phone → ESP32 relay (BLE in, LoRa out) → second ESP32 (LoRa in, USB-serial out) → laptop command node (decrypt, dashboard).
- Only attempt once Stages 1–5 each pass independently — this stage should mostly confirm wiring, not surface new logic bugs, because the logic was already proven per-hop above.
