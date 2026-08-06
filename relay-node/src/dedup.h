#pragma once
#include <Arduino.h>
#include <string>

// Returns true if this is a new unique event (should be transmitted over LoRa),
// or false if it's a duplicate (already seen, corroboration incremented).
bool processDedup(const uint8_t* payload, size_t length, uint16_t& outCorroborationCount);

// Updates the corroboration count in the byte buffer directly
void updateCorroborationCount(uint8_t* payload, uint16_t newCount);

// Decrements TTL in the payload. Returns new TTL.
uint8_t decrementTTL(uint8_t* payload);
