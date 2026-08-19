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
    Serial.println("[BLE] Initializing Scanner (BLE 5 extended scan)...");
    payloadCallback = cb;
    
    BLEDevice::init("");
    pBLEScan = BLEDevice::getScan();
    pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks());
    pBLEScan->setActiveScan(true); 
    pBLEScan->setInterval(100);
    pBLEScan->setWindow(99); 
    
    // BLE 5 Extended Scanning Configuration
    // On ESP32-S3/C3 with Arduino-ESP32 v3.x (ESP-IDF 5.x), the NimBLE 
    // stack supports extended scanning via BLEScan. The Arduino BLE library
    // wraps NimBLE and should handle extended advertising PDUs (ADV_EXT_IND /
    // AUX_ADV_IND) transparently in the scan callback.
    //
    // If the Arduino-ESP32 BLE library version does NOT automatically enable
    // extended scanning, the following ESP-IDF NimBLE call may be needed
    // directly (requires #include "esp_bt.h" and NimBLE headers):
    //
    //   struct ble_gap_ext_disc_params ext_params = {};
    //   ext_params.itvl = 100;
    //   ext_params.window = 99;
    //   ext_params.passive = 0;
    //   ble_gap_ext_disc(own_addr_type, 0, 0, 1, BLE_HCI_SCAN_FILT_NO_WL,
    //                    NULL, &ext_params, scanCompleteCb, NULL);
    //
    // HARDWARE VALIDATION REQUIRED: This configuration has NOT been tested
    // on real ESP32-S3 hardware. Verify that extended advertising payloads
    // (>31 bytes) are received correctly in the scan callback before
    // proceeding to Testing Stage 5.
    
    Serial.println("[BLE] Scanner Initialized (expecting BLE 5 extended advertisements).");
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
