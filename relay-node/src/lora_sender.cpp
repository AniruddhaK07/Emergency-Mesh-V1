#include "lora_sender.h"
#include "config.h"
#include <RadioLib.h>
#include <SPI.h>

// Initialize SX1276 — Module(NSS, DIO0, RESET, DIO1)
// DIO1 not wired in this hardware config, so RADIOLIB_NC
SX1276 radio = new Module(LORA_NSS, LORA_DIO0, LORA_RESET, RADIOLIB_NC);

void setupLoRa() {
    Serial.println("[LoRa] Initializing...");
    
    SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, LORA_NSS);
    
    int state = radio.begin(LORA_FREQ, LORA_BW, LORA_SF, LORA_CR, LORA_SYNC_WORD, LORA_POWER);
    if (state == RADIOLIB_ERR_NONE) {
        Serial.println("[LoRa] Initialized successfully!");
    } else {
        Serial.print("[LoRa] Initialization failed, code ");
        Serial.println(state);
        while (true); // Halt on failure
    }
}

bool sendLoRaPacket(const uint8_t* buffer, size_t length) {
    Serial.print("[LoRa] Transmitting packet of size ");
    Serial.println(length);
    
    int state = radio.transmit((uint8_t*)buffer, length);
    
    if (state == RADIOLIB_ERR_NONE) {
        Serial.println("[LoRa] Transmit success!");
        return true;
    } else {
        Serial.print("[LoRa] Transmit failed, code ");
        Serial.println(state);
        return false;
    }
}
