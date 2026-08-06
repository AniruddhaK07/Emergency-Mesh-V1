package com.bitchat.emergency.power

import com.bitchat.emergency.model.GpsMode
import com.bitchat.emergency.model.PowerTier
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PowerTierStateMachineTest {

    private lateinit var stateMachine: PowerTierStateMachine

    @Before
    fun setup() {
        stateMachine = PowerTierStateMachine()
    }

    // --- Threshold boundary tests ---

    @Test
    fun testBattery100_returnsNormal() {
        assertEquals(PowerTier.NORMAL, stateMachine.updateBatteryLevel(100))
    }

    @Test
    fun testBattery41_returnsNormal() {
        assertEquals(PowerTier.NORMAL, stateMachine.updateBatteryLevel(41))
    }

    @Test
    fun testBattery40_returnsConserve() {
        // Spec: battery 15-40% → CONSERVE (40 is inclusive)
        assertEquals(PowerTier.CONSERVE, stateMachine.updateBatteryLevel(40))
    }

    @Test
    fun testBattery16_returnsConserve() {
        assertEquals(PowerTier.CONSERVE, stateMachine.updateBatteryLevel(16))
    }

    @Test
    fun testBattery15_returnsConserve() {
        // Spec: CONSERVE is 15-40% (inclusive), CRITICAL is < 15%
        assertEquals(PowerTier.CONSERVE, stateMachine.updateBatteryLevel(15))
    }

    @Test
    fun testBattery14_returnsCritical() {
        assertEquals(PowerTier.CRITICAL, stateMachine.updateBatteryLevel(14))
    }

    @Test
    fun testBattery1_returnsCritical() {
        assertEquals(PowerTier.CRITICAL, stateMachine.updateBatteryLevel(1))
    }

    // --- Transition tests ---

    @Test
    fun testTransition_NormalToConserve() {
        stateMachine.updateBatteryLevel(50)
        assertEquals(PowerTier.NORMAL, stateMachine.currentTier)
        stateMachine.updateBatteryLevel(35)
        assertEquals(PowerTier.CONSERVE, stateMachine.currentTier)
    }

    @Test
    fun testTransition_ConserveToCritical() {
        stateMachine.updateBatteryLevel(20)
        assertEquals(PowerTier.CONSERVE, stateMachine.currentTier)
        stateMachine.updateBatteryLevel(10)
        assertEquals(PowerTier.CRITICAL, stateMachine.currentTier)
    }

    @Test
    fun testTransition_CriticalToConserve() {
        stateMachine.updateBatteryLevel(10)
        assertEquals(PowerTier.CRITICAL, stateMachine.currentTier)
        stateMachine.updateBatteryLevel(20)
        assertEquals(PowerTier.CONSERVE, stateMachine.currentTier)
    }

    @Test
    fun testTransition_ConserveToNormal() {
        stateMachine.updateBatteryLevel(30)
        assertEquals(PowerTier.CONSERVE, stateMachine.currentTier)
        stateMachine.updateBatteryLevel(45)
        assertEquals(PowerTier.NORMAL, stateMachine.currentTier)
    }

    // --- BLE config per tier ---

    @Test
    fun testNormalConfig_scanAndAdvertise() {
        stateMachine.updateBatteryLevel(80)
        val config = stateMachine.getCurrentBleConfig()
        assertEquals(10_000L, config.scanIntervalMs)
        assertEquals(3_000L, config.scanWindowMs)
        assertEquals(7_000L, config.advertiseIntervalMs)
        assertEquals(GpsMode.ON_DEMAND, config.gpsMode)
    }

    @Test
    fun testConserveConfig_advertiseOnly() {
        stateMachine.updateBatteryLevel(30)
        val config = stateMachine.getCurrentBleConfig()
        assertEquals(0L, config.scanIntervalMs)
        assertEquals(0L, config.scanWindowMs)
        assertEquals(30_000L, config.advertiseIntervalMs)
        assertEquals(GpsMode.LAST_KNOWN_CACHED, config.gpsMode)
    }

    @Test
    fun testCriticalConfig_burstOnly() {
        stateMachine.updateBatteryLevel(5)
        val config = stateMachine.getCurrentBleConfig()
        assertEquals(0L, config.scanIntervalMs)
        assertEquals(0L, config.scanWindowMs)
        assertEquals(300_000L, config.advertiseIntervalMs) // 5 minutes
        assertEquals(GpsMode.DISABLED, config.gpsMode)
    }
}
