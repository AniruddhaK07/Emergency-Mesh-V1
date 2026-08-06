#pragma once

typedef void (*BlePayloadCallback)(const uint8_t* payload, size_t length);

void setupBLEScanner(BlePayloadCallback cb);
void loopBLEScanner();
