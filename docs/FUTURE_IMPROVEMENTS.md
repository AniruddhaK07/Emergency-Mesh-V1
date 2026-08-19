# Future Improvements, Edge Cases & Real-World Hardening

This document outlines architectural critiques, operational realities, edge cases, and proposed future enhancements for the Emergency Mesh system. It serves as a roadmap for hardening the system beyond the Phase 0–4 baseline.

---

## 1. Operational Realities & Deployment Critique

### 1.1 The "Day Zero" App Distribution Problem
- **Reality:** In a severe disaster (earthquake, flood, hurricane), cell towers, fiber lines, and power grids are offline. Survivors cannot download an APK from Google Play or sideload an app after cellular connectivity is lost.
- **Impact:** Consumer phone mesh networks suffer from a severe node-density bottleneck. If only a handful of people in a 10 km² disaster zone have the app installed, isolated survivor islands cannot bridge data across the mesh.
- **Recommended Strategy:**
  - Position the system for **Organized First Responders / Search & Rescue (S&R) teams** carrying pre-configured devices.
  - Pre-deploy in disaster-prone municipalities, remote islands, and delta communities with pre-installed community kits.
  - Sideloading distribution hubs via Wi-Fi Direct / Local Captive Portal hotspots at evacuation centers.

### 1.2 RF Propagation through Disaster Rubble
- **BLE 5 Extended Advertising (2.4 GHz):** While nominal open-air BLE range is 30–100m, absorption through wet concrete, metal rebar, and dense rubble drops effective range to **3–8 meters**.
- **LoRa (915 MHz Sub-GHz):** Nominal 15km range assumes line-of-sight hilltop geometry. In dense, non-line-of-sight urban canyons with collapsed structures, realistic urban range drops to **800m – 2.5km**, requiring strategically elevated relay nodes (towers, hills, rooftops).

### 1.3 Survivor Psychology & Lack of ACKs
- **The Psychological Abyss:** The current transport is strictly unidirectional broadcast (Noise_N). When a user submits a report, the UI indicates local queuing, but the user receives zero confirmation that a relay or command center has received their signal.
- **Risk:** Survivors in life-threatening panic who do not see confirmation may repeatedly resubmit reports, draining critical battery reserves.

---

## 2. Unaddressed Edge Cases & Vulnerabilities

### 2.1 Dedup Table Memory Exhaustion (Relay Firmware)
- **Current State:** In `relay-node/src/dedup.cpp`, `std::map<std::string, DedupEntry> dedupTable` stores cluster keys in RAM indefinitely.
- **Risk:** During a multi-day disaster with thousands of reports, the ESP32-S3's limited heap (~512KB SRAM) will fragment and crash from out-of-memory (OOM).
- **Fix:** Implement a fixed-capacity LRU ring buffer (e.g., maximum 500 entries) with timestamp-based TTL eviction (purge entries older than 60 minutes).

### 2.2 Replay Attacks & Corroboration Spoofing
- **Current State:** The 43-byte fixed header is transmitted in plaintext so relays can route and cluster without holding decryption keys.
- **Vulnerability:** A malicious actor or compromised device could intercept a legitimate report header and replay it repeatedly with modified timestamps or fake coordinates to artificially manipulate the dashboard priority weighting formula ($W = \text{Severity} \times \ln(1 + \text{Corroboration})$).
- **Fix:** Include a lightweight HMAC / truncated signature of the header over a short time window, or incorporate sender-rate limiting per physical relay.

### 2.3 Clock Drift on Offline Devices
- **Current State:** Clustering relies on `timestamp / 300000` (5-minute discrete time buckets).
- **Risk:** If a survivor's phone has been powered down, drained to 0%, or has an incorrect real-time clock (RTC) without cellular/NTP synchronization, its timestamp may differ significantly, preventing proper clustering with other survivors at the same event.
- **Fix:** Relays should validate incoming timestamps against the relay's local hardware RTC / GPS time and clamp drifted timestamps to current relay observation time before clustering.

### 2.4 Aggressive OEM Battery Optimization & Doze Mode
- **Current State:** App uses `FOREGROUND_SERVICE_CONNECTED_DEVICE` and requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **Risk:** Aggressive Android OEM ROMs (Samsung OneUI, Xiaomi MIUI, Huawei EMUI) aggressively kill background scanning loops when the screen has been turned off for extended periods to preserve battery.
- **Fix:** Implement periodic Android `WorkManager` / `AlarmManager` wake locks to resurrect the scan/advertise duty cycle if killed by the OS.

---

## 3. High-Value Roadmap Enhancements

### 3.1 Downstream Acknowledgment (ACK) Return Beacon
- **Concept:** Relay nodes can periodically broadcast a compact downstream BLE advertisement containing a Bloom filter or truncated hashes of recently forwarded `reportId`s.
- **User Experience:** When the survivor's phone detects its report hash in the relay's beacon, the UI screen turns green and displays:
  > **CONFIRMED: Received by Relay #4 at 01:45 AM. Rescue dispatched.**

### 3.2 1-Tap Panic & Hardware Emergency Trigger
- **Concept:** In fast-moving catastrophes (flash floods, building collapse), form-filling is impossible.
- **Feature:** Implement a hardware trigger (e.g., pressing the device Power button 5 times rapidly) to instantly construct and queue a `CRITICAL | TRAPPED` payload with immediate GPS capture and high-priority BLE burst without unlocking the phone.

### 3.3 Dynamic Multi-Hop LoRa Mesh (Tier 2 ➔ Tier 2)
- **Concept:** Currently, all ESP32 relays transmit directly to the Command Node. In large geography (>15km), relays should support simple hop-decrement repeating between relays to reach distant command nodes.

### 3.4 Dedicated Wearable / Beacon Bridge
- **Concept:** Pair the phone app with cheap ($10–15) wearable BLE / LoRa beacon tags that survivors or emergency workers wear, extending broadcast resilience even if the primary smartphone battery expires.
