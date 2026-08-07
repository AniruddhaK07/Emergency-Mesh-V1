#include "dedup.h"
#include <map>
#include <cmath>
#include <cstdio>

struct DedupEntry {
    uint16_t corroborationCount;
    uint8_t originalReportId[16];
};

static std::map<std::string, DedupEntry> dedupTable;

bool processDedup(uint8_t* payload, size_t length, uint16_t& outCorroborationCount) {
    if (length < 25) {
        return false; // Payload too small to contain our header
    }

    uint8_t emergencyType = payload[1];
    
    int64_t timestamp;
    memcpy(&timestamp, &payload[5], sizeof(int64_t)); // LE
    
    float lat;
    memcpy(&lat, &payload[14], sizeof(float)); // LE
    
    float lon;
    memcpy(&lon, &payload[18], sizeof(float)); // LE
    
    uint16_t currentCorroboration;
    memcpy(&currentCorroboration, &payload[22], sizeof(uint16_t)); // LE

    // Generate cluster key
    uint8_t hasLocation = payload[13];
    int64_t timeKey = timestamp / 300000;
    char keyBuf[128];

    if (hasLocation == 0) {
        // For NULL_LOC, the relay's own BLE range bounds the physical location,
        // so we cluster on emergencyType + time bucket alone.
        snprintf(keyBuf, sizeof(keyBuf), "NULL_LOC_%u_%lld", emergencyType, timeKey);
    } else {
        float rLat = roundf(lat * 1000.0f) / 1000.0f;
        float rLon = roundf(lon * 1000.0f) / 1000.0f;
        snprintf(keyBuf, sizeof(keyBuf), "%.3f_%.3f_%u_%lld", rLat, rLon, emergencyType, timeKey);
    }
    std::string key(keyBuf);

    auto it = dedupTable.find(key);
    if (it != dedupTable.end()) {
        // Exists, increment corroboration
        it->second.corroborationCount++;
        outCorroborationCount = it->second.corroborationCount;
        
        // Overwrite the outgoing duplicate packet's reportId with the original one
        memcpy(&payload[25], it->second.originalReportId, 16);
        
        return false; // Not a new event, don't TX again
    } else {
        // New event
        DedupEntry entry;
        // The packet might already have >1 corroboration count from the reporter, 
        // but we track our local observations. We will store 1.
        entry.corroborationCount = currentCorroboration > 0 ? currentCorroboration : 1;
        memcpy(entry.originalReportId, &payload[25], 16);
        dedupTable[key] = entry;
        outCorroborationCount = entry.corroborationCount;
        return true; // Transmit this over LoRa
    }
}

void updateCorroborationCount(uint8_t* payload, uint16_t newCount) {
    memcpy(&payload[22], &newCount, sizeof(uint16_t));
}

uint8_t decrementTTL(uint8_t* payload) {
    uint8_t ttl = payload[24];
    if (ttl > 0) {
        ttl--;
        payload[24] = ttl;
    }
    return ttl;
}
