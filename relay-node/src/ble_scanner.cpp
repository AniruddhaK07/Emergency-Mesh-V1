#include "ble_scanner.h"
#include "config.h"
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>

static BLEScan* pBLEScan;
static BlePayloadCallback payloadCallback = nullptr;
static uint32_t lastScanTime = 0;
static bool isScanning = false;

class MyAdvertisedDeviceCallbacks : public BLEAdvertisedDeviceCallbacks {
    void onResult(BLEAdvertisedDevice advertisedDevice) override {
        // We look for our service data or manufacturer data.
        // Assuming payload is broadcast as Service Data for our specific UUID
        if (advertisedDevice.haveServiceData()) {
            BLEUUID uuid = advertisedDevice.getServiceDataUUID();
            if (uuid.toString() == BLE_SERVICE_UUID) {
                std::string strServiceData = advertisedDevice.getServiceData(uuid);
                size_t length = strServiceData.length();
                if (length >= 24 && payloadCallback != nullptr) {
                    payloadCallback((const uint8_t*)strServiceData.data(), length);
                }
            }
        }
    }
};

void setupBLEScanner(BlePayloadCallback cb) {
    Serial.println("[BLE] Initializing Scanner...");
    payloadCallback = cb;
    
    BLEDevice::init("");
    pBLEScan = BLEDevice::getScan(); // create new scan
    pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks());
    pBLEScan->setActiveScan(true); 
    pBLEScan->setInterval(100);
    pBLEScan->setWindow(99); 
    
    Serial.println("[BLE] Scanner Initialized.");
}

void loopBLEScanner() {
    uint32_t now = millis();
    // Simple interval logic
    if (!isScanning && (now - lastScanTime >= (BLE_SCAN_INTERVAL_SEC * 1000))) {
        isScanning = true;
        Serial.println("[BLE] Starting scan window...");
        // Non-blocking scan start
        pBLEScan->start(BLE_SCAN_WINDOW_SEC, [](BLEScanResults results) {
            isScanning = false;
            pBLEScan->clearResults();
            Serial.println("[BLE] Scan window ended.");
        }, false);
        
        lastScanTime = now;
    }
}
