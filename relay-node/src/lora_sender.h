#pragma once
#include <Arduino.h>

void setupLoRa();
bool sendLoRaPacket(const uint8_t* buffer, size_t length);
