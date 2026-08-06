# Emergency Mesh V1

An off-grid, disaster-resilient communication mesh designed for extreme emergency situations where cellular and internet infrastructure have completely failed. 

This system prioritizes absolute utilitarian reliability, high-contrast usability in stressful environments, and aggressive power conservation over everything else.

## 🏗️ System Architecture

The mesh operates across three distinct hardware tiers, ensuring that data can physically traverse a disaster zone via short-range and long-range RF without relying on the internet until the absolute last mile.

*   **Tier 1: Reporter Phones (Android)**
    *   **Role:** Data entry, local GPS capture, and short-range mesh transport.
    *   **Tech:** Native Kotlin, Android Foreground Services.
    *   **Transport:** BLE (Bluetooth Low Energy) Advertisements.
    *   **Behavior:** Users tap a high-contrast UI to report emergencies. Phones use a store-and-forward BLE mesh to pass encrypted payloads from phone to phone, migrating data physically toward a relay.

*   **Tier 2: Relay Nodes (ESP32 + RFM95)**
    *   **Role:** Bridge the short-range phone mesh to long-range backhaul.
    *   **Tech:** C++, PlatformIO, RadioLib.
    *   **Transport:** BLE Scanner (In) → SX1276 LoRa (Out).
    *   **Behavior:** Placed on rooftops or high ground. They continuously scan for BLE advertisements, perform deduplication (to prevent network flooding), and blindly forward the encrypted payloads over kilometers using LoRa.

*   **Tier 3: Command Node (Dashboard + Gateway)**
    *   **Role:** The single internet-connected choke point (e.g., Starlink) for operational command.
    *   **Tech:** React, Vite, TailwindCSS, Express.js.
    *   **Behavior:** Decrypts incoming LoRa payloads, unpacks the binary structs, and displays them on a dark, high-contrast operational dashboard. Reports are mathematically weighted and sorted based on severity and network corroboration.

## ✨ Key Technical Features

### 1. Power-Tier State Machine
Disaster scenarios require phones to last days. The Android app implements a pure, testable state machine that dynamically alters radio duty cycles:
*   **NORMAL (>40% battery):** Actively scans and advertises (store-and-forward mesh is active).
*   **CONSERVE (15-40% battery):** Stops scanning to save power; only advertises outgoing reports every 30 seconds.
*   **CRITICAL (<15% battery):** Radio shuts off entirely. Only fires a single BLE advertisement burst when the user hits "Send".

### 2. Custom Payload Pipeline (Compression & Crypto)
To fit within strict BLE advertisement and LoRa packet limits (~250 bytes), the system uses a custom binary pipeline:
*   **Binary Struct Serializer:** Packs data into a strictly defined, little-endian binary layout.
*   **Plaintext Header:** The first 42 bytes (containing GPS, severity, TTL, and Deduplication keys) remain in plaintext so Tier 2 relays can route and deduplicate without needing decryption keys.
*   **Encrypted Body:** The free-text notes are compressed via `LZ4` and encrypted via `AES-GCM` using unique nonces. Only the Tier 3 Command Node can read the free text.

### 3. Aggressive Deduplication (Anti-Flooding)
*   **Phone-Side:** Maintains a capped LRU cache of UUIDs to prevent the BLE mesh from endlessly echoing the same report.
*   **Relay-Side:** Computes a "Cluster Key" `(Lat + Lon + Type + Time Bucket)` to deduplicate visually similar reports from *different* phones witnessing the same event, incrementing a `corroborationCount` instead of spamming the command center.

## 📂 Project Structure

```text
├── docs/                # Architecture diagrams, testing plans, and progress logs
├── relay-node/          # Tier 2: ESP32 PlatformIO project (C++)
├── reporter-app/        # Tier 1: Android App (Kotlin)
└── command-dashboard/   # Tier 3: React Dashboard & Express Ingest Server
```

## 🚀 Getting Started

### 1. Command Dashboard (Tier 3)
Requires Node.js.
```bash
cd command-dashboard
npm install
npm run server  # Starts the Express ingest API on port 3001
npm run dev     # Starts the React dashboard on port 5173
```
*To test the dashboard without hardware, run `npm run seed` in a separate terminal to fire mock payloads at the server.*

### 2. Reporter App (Tier 1)
Requires Android Studio and a **physical Android device** (emulators do not support real BLE advertising).
1. Open `reporter-app/` in Android Studio.
2. Connect a physical Android device via USB debugging.
3. Build and Run. Ensure Location and Bluetooth permissions are granted.

### 3. Relay Node (Tier 2)
Requires VS Code with the PlatformIO extension and an ESP32 wired to an SX1276/RFM95 LoRa module.
1. Open `relay-node/` in VS Code / PlatformIO.
2. Verify pinouts in `src/config.h`.
3. Build and Upload to the ESP32.

## 🧪 Current Status
*   **Phase 0-4:** ✅ Complete (App UI, Firmware, Crypto Pipeline, Mesh Transport, Dashboard).
*   **Testing:** 🔄 Currently executing the multi-stage hardware testing plan (see `docs/TESTING.md`).
