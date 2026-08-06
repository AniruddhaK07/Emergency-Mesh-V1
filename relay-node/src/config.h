#pragma once

// LoRa SPI Pins for ESP32 DevKit v1
#define LORA_NSS 18
#define LORA_DIO0 26
#define LORA_RESET 14
#define LORA_SCK 5
#define LORA_MISO 19
#define LORA_MOSI 27

// LoRa Parameters
#define LORA_FREQ 915.0
#define LORA_BW 125.0
#define LORA_SF 7
#define LORA_CR 5
#define LORA_SYNC_WORD 0x12
#define LORA_POWER 10

// BLE Parameters
#define BLE_SCAN_WINDOW_SEC 5
#define BLE_SCAN_INTERVAL_SEC 10
#define BLE_SERVICE_UUID "0000b17c-0000-1000-8000-00805f9b34fb" // Example UUID for bitchat
