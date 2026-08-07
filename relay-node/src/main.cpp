#include <Arduino.h>
#include "config.h"
#include "ble_scanner.h"
#include "lora_sender.h"
#include "dedup.h"

// Callback for when we receive a valid BLE payload
void onBlePayloadReceived(const uint8_t* payload, size_t length) {
    if (length < 24) return;
    
    // We copy the payload because we need to modify TTL and corroboration count
    uint8_t* buffer = new uint8_t[length];
    memcpy(buffer, payload, length);
    
    // 1. Decrement TTL
    uint8_t newTtl = decrementTTL(buffer);
    if (newTtl == 0) {
        Serial.println("[Relay] TTL reached 0, dropping packet.");
        delete[] buffer;
        return;
    }
    
    // 2. Dedup and cluster key checking
    uint16_t newCorroborationCount = 0;
    bool shouldTransmit = processDedup(buffer, length, newCorroborationCount);
    
    if (shouldTransmit) {
        Serial.println("[Relay] New unique event detected. Forwarding via LoRa.");
        // Ensure the updated corroboration count is in the payload before transmission
        updateCorroborationCount(buffer, newCorroborationCount);
        sendLoRaPacket(buffer, length);
    } else {
        Serial.print("[Relay] Duplicate event detected. Corroboration count updated to ");
        Serial.println(newCorroborationCount);
        
        // Transmit minimal packet (header only) to update Tier 3's corroboration count
        updateCorroborationCount(buffer, newCorroborationCount);
        buffer[41] = 0; // notesLength (LSB)
        buffer[42] = 0; // notesLength (MSB)
        Serial.println("[Relay] Forwarding corroboration update via LoRa (header only).");
        sendLoRaPacket(buffer, 43);
    }
    
    delete[] buffer;
}

void setup() {
    Serial.begin(115200);
    while (!Serial);
    
    Serial.println("[Relay] Booting ESP32 Relay Node...");
    
    setupLoRa();
    setupBLEScanner(onBlePayloadReceived);
    
    Serial.println("[Relay] Setup complete. Entering main loop.");
}

void loop() {
    loopBLEScanner();
    // Other background tasks like pinging watchdog could go here
    delay(10);
}
