package com.bitchat.emergency.ble

import android.content.Context
import com.bitchat.emergency.model.BleConfig
import com.bitchat.emergency.payload.PayloadPipeline
import com.bitchat.emergency.queue.ReportQueue
import java.util.concurrent.ConcurrentLinkedQueue

class BleMeshManager(private val context: Context) {
    private val dedup = PayloadDedup()
    private val advertiser = BleAdvertiser(context)
    private val scanner = BleScanner(context) { payload ->
        if (!dedup.isDuplicate(payload)) {
            if (payload.size >= 24) {
                val ttl = payload[23].toInt()
                if (ttl > 1) {
                    val newPayload = payload.clone()
                    newPayload[23] = (ttl - 1).toByte()
                    relayQueue.add(newPayload)
                }
            }
        }
    }
    
    private val relayQueue = ConcurrentLinkedQueue<ByteArray>()
    private val reportQueue = ReportQueue(context)
    
    @Volatile private var isRunning = false
    @Volatile private var currentConfig: BleConfig? = null
    private var lastOwnPayload: ByteArray? = null
    
    fun updateConfig(config: BleConfig) {
        this.currentConfig = config
    }
    
    fun start() {
        if (isRunning) return
        isRunning = true
        Thread {
            while (isRunning) {
                val config = currentConfig
                if (config == null) {
                    Thread.sleep(1000)
                    continue
                }
                
                if (config.scanWindowMs > 0) {
                    scanner.startScan()
                    Thread.sleep(config.scanWindowMs)
                    scanner.stopScan()
                }
                
                if (config.advertiseIntervalMs > 0) {
                    var payloadToAdvertise = relayQueue.poll()
                    
                    if (payloadToAdvertise == null) {
                        val report = reportQueue.dequeue()
                        if (report != null) {
                            payloadToAdvertise = PayloadPipeline.prepareForTransmission(report)
                            lastOwnPayload = payloadToAdvertise
                            dedup.isDuplicate(payloadToAdvertise) // Prevent self relay
                        }
                    }
                    
                    if (payloadToAdvertise == null) {
                        payloadToAdvertise = lastOwnPayload
                    }
                    
                    if (payloadToAdvertise != null) {
                        advertiser.startAdvertising(payloadToAdvertise)
                        if (config.scanWindowMs > 0) {
                            Thread.sleep(config.advertiseIntervalMs)
                        } else {
                            Thread.sleep(2000) // Burst
                        }
                        advertiser.stopAdvertising()
                    }
                    
                    if (config.scanWindowMs == 0L) {
                        val sleepTime = config.advertiseIntervalMs - if (payloadToAdvertise != null) 2000L else 0L
                        if (sleepTime > 0) {
                            Thread.sleep(sleepTime)
                        }
                    }
                } else {
                    Thread.sleep(5000)
                }
            }
        }.start()
    }
    
    fun stop() {
        isRunning = false
        scanner.stopScan()
        advertiser.stopAdvertising()
    }
}
