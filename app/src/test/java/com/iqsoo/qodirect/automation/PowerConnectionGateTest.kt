package com.iqsoo.qodirect.automation

import com.iqsoo.qodirect.automation.PowerConnectionGate.Result.*
import org.junit.Assert.*
import org.junit.Test

class PowerConnectionGateTest {
    private val unplugged = Environment(plugged = 0, battery = 70, temperature = 30f, landscape = true)

    @Test fun unpluggedLandscapeNeverStartsAnAutomationSession() {
        val gate = PowerConnectionGate()
        repeat(100) { assertEquals(WAIT, gate.evaluate(unplugged, false)) }
    }

    @Test fun onlyValidatedAcOrUsbAllowsSecondaryFeatures() {
        val gate = PowerConnectionGate()
        listOf(-1, 0, 4, 8, 5, 9).forEach {
            assertEquals("plugged=$it", WAIT, gate.evaluate(unplugged.copy(plugged = it), false))
        }
        listOf(1, 2, 3).forEach {
            assertEquals(READY, gate.evaluate(unplugged.copy(plugged = it), false))
        }
    }

    @Test fun failedUnplugCleanupIsAttemptedOnlyOnce() {
        val gate = PowerConnectionGate()
        gate.evaluate(unplugged.copy(plugged = 1), true)
        assertEquals(RESTORE_ONCE, gate.evaluate(unplugged, true))
        // A failed cleanup may leave a lease. Later battery and rotation events must not loop.
        repeat(100) { assertEquals(WAIT, gate.evaluate(unplugged, true)) }
    }

    @Test fun noLeaseNeverCausesAnUnplugRestore() {
        val gate = PowerConnectionGate()
        gate.evaluate(unplugged.copy(plugged = 1), false)
        repeat(100) { assertEquals(WAIT, gate.evaluate(unplugged, false)) }
    }

    @Test fun aNewPhysicalPowerSessionAllowsOneNewCleanup() {
        val gate = PowerConnectionGate()
        repeat(5) {
            assertEquals(READY, gate.evaluate(unplugged.copy(plugged = 2), true))
            assertEquals(RESTORE_ONCE, gate.evaluate(unplugged, true))
            assertEquals(WAIT, gate.evaluate(unplugged, true))
        }
    }

    @Test fun switchingToUnvalidatedPowerStopsTheSession() {
        val gate = PowerConnectionGate()
        gate.evaluate(unplugged.copy(plugged = 2), true)
        assertEquals(RESTORE_ONCE, gate.evaluate(unplugged.copy(plugged = 4), true))
        assertEquals(WAIT, gate.evaluate(unplugged.copy(plugged = 4), true))
    }

    @Test fun unpluggedEventsCannotEmitSecondaryRuleEffectsOrDeadlines() {
        val engine = RuleEngine()
        val p = Preferences(automatic = true)
        val rules = listOf(Action.LOG, Action.NOTIFY, Action.CANCEL, Action.PAUSE, Action.RESUME).map {
            Rule(action = it, delaySeconds = 0,
                condition = Condition(ConditionType.POWER_DISCONNECTED))
        }
        repeat(100) {
            val result = engine.evaluate(unplugged, p, rules, it * 1000L, 0)
            assertEquals(Action.RESTORE, result.action)
            assertTrue(result.effects.isEmpty())
            assertNull(result.dueAt)
        }
    }

    @Test fun reconnectObservesLandscapeDelayFromScratch() {
        val engine = RuleEngine()
        val p = Preferences(automatic = true)
        val wired = unplugged.copy(plugged = 1)
        engine.evaluate(wired, p, emptyList(), 0, 0)
        assertEquals(Action.ENABLE, engine.evaluate(wired, p, emptyList(), 2000, 0).action)
        engine.evaluate(unplugged, p, emptyList(), 3000, 0)
        val reconnect = engine.evaluate(wired, p, emptyList(), 20000, 0)
        assertNull(reconnect.action)
        assertEquals(22000L, reconnect.dueAt)
        assertEquals(Action.ENABLE, engine.evaluate(wired, p, emptyList(), 22000, 0).action)
    }

    @Test fun reconnectStartsANewRuleEffectSessionWithoutRepeatedIdleEffects() {
        val engine = RuleEngine()
        val p = Preferences(automatic = true)
        val rules = listOf(Rule(action = Action.LOG, delaySeconds = 0))
        val wired = unplugged.copy(plugged = 1)
        assertEquals(1, engine.evaluate(wired, p, rules, 0, 0).effects.size)
        assertTrue(engine.evaluate(unplugged, p, rules, 1000, 0).effects.isEmpty())
        assertTrue(engine.evaluate(unplugged, p, rules, 2000, 0).effects.isEmpty())
        assertEquals(1, engine.evaluate(wired, p, rules, 3000, 0).effects.size)
    }
}
