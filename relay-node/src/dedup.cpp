#include "dedup.h"
#include <map>
#include <cmath>
#include <cstdio>

struct DedupEntry {
    uint16_t corroborationCount;
};

static std::map<std::string, DedupEntry> dedupTable;

bool processDedup(const uint8_t* payload, size_t length, uint16_t& outCorroborationCount) {
    if (length < 24) {
        return false; // Payload too small to contain our header
    }

    uint8_t emergencyType = payload[1];
    
    int64_t timestamp;
    memcpy(&timestamp, &payload[5], sizeof(int64_t)); // LE
    
    float lat;
    memcpy(&lat, &payload[13], sizeof(float)); // LE
    
    float lon;
    memcpy(&lon, &payload[17], sizeof(float)); // LE
    
    uint16_t currentCorroboration;
    memcpy(&currentCorroboration, &payload[21], sizeof(uint16_t)); // LE

    // Generate cluster key
    // round(lat, 3) + round(lon, 3) + emergencyType + floor(timestamp / 300)
    float rLat = roundf(lat * 1000.0f) / 1000.0f;
    float rLon = roundf(lon * 1000.0f) / 1000.0f;
    // Timestamp is in millis. Spec says floor(timestamp / 300) meaning 5-min
    // buckets in seconds, so divide by 300,000 (300s * 1000ms/s).
    int64_t timeKey = timestamp / 300000;

    char keyBuf[128];
    snprintf(keyBuf, sizeof(keyBuf), "%.3f_%.3f_%u_%lld", rLat, rLon, emergencyType, timeKey);
    std::string key(keyBuf);

    auto it = dedupTable.find(key);
    if (it != dedupTable.end()) {
        // Exists, increment corroboration
        it->second.corroborationCount++;
        outCorroborationCount = it->second.corroborationCount;
        return false; // Not a new event, don't TX again
    } else {
        // New event
        DedupEntry entry;
        // The packet might already have >1 corroboration count from the reporter, 
        // but we track our local observations. We will store 1.
        entry.corroborationCount = currentCorroboration > 0 ? currentCorroboration : 1;
        dedupTable[key] = entry;
        outCorroborationCount = entry.corroborationCount;
        return true; // Transmit this over LoRa
    }
}

void updateCorroborationCount(uint8_t* payload, uint16_t newCount) {
    memcpy(&payload[21], &newCount, sizeof(uint16_t));
}

uint8_t decrementTTL(uint8_t* payload) {
    uint8_t ttl = payload[23];
    if (ttl > 0) {
        ttl--;
        payload[23] = ttl;
    }
    return ttl;
}
