@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.iqsoo.qodirect.automation

import com.iqsoo.qodirect.charging.ControlResult
import com.iqsoo.qodirect.charging.ControllerTest.Fake
import com.iqsoo.qodirect.charging.VivoDirectChargeController
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AutomaticEnableRetryTest {
    private val preferences = Preferences(automatic = true)
    private val landscape = Environment(plugged = 1, battery = 60, temperature = 32f, landscape = true)
    private val decision = Decision(Action.ENABLE, "充电时横屏", "builtin-landscape")
    private val key = AutomaticEnableRetry.key(decision)
    private val timeout = ControlResult(false, "BYPASS_TIMEOUT", "厂商标志未同步")

    @Test fun repeatedFailuresBackOffAndContinueAtThirtySeconds() {
        val retry = AutomaticEnableRetry()
        var now = 100L
        listOf(5, 10, 20, 30, 30, 30, 30, 30, 30).forEachIndexed { i, seconds ->
            val pending = retry.failed(key, timeout, now)!!
            assertEquals(i + 1L, pending.attempt)
            assertEquals(now + seconds * 1000L, pending.dueAt)
            now = pending.dueAt
        }
    }

    @Test fun successClearsTheRetryAndRestartsBackoffForTheNextFailure() {
        val retry = AutomaticEnableRetry()
        retry.failed(key, timeout, 0)
        assertNull(retry.failed(key, ControlResult(true, "FEEDBACK_CONFIRMED", "已确认"), 6000))
        assertNull(retry.pending)
        assertEquals(12000L, retry.failed(key, timeout, 7000)!!.dueAt)
    }

    @Test fun protectionPermissionAndManualConflictsDoNotRetry() {
        val retry = AutomaticEnableRetry()
        listOf("UNSUPPORTED", "UID_INVALID", "PROTECTION", "MANUAL_CHANGED", "RESTORE_CONFLICT", "UNKNOWN_ERROR").forEach {
            retry.failed(key, timeout, 0)
            assertNull(retry.failed(key, ControlResult(false, it, it), 1000))
            assertNull(retry.pending)
        }
    }

    @Test fun transientReadTransportAndCommandFailuresAreRecoverable() {
        listOf("READ_FAILED", "TRANSPORT_TIMEOUT", "TRANSPORT_ERROR", "COMMAND_FAILED", "SETTINGS_MISMATCH", "UNKNOWN_SETTINGS").forEach {
            assertNotNull(AutomaticEnableRetry().failed(key, ControlResult(false, it, it), 0))
        }
    }

    @Test fun repeatedEvaluationDoesNotMoveTheDeadline() {
        val retry = AutomaticEnableRetry()
        val first = retry.failed(key, timeout, 1000)
        repeat(100) { retry.retain(key) }
        assertEquals(first, retry.pending)
    }

    @Test fun changingTheWinningRuleCancelsThePreviousAttempt() {
        val retry = AutomaticEnableRetry()
        retry.failed(key, timeout, 0)
        retry.retain("another-rule")
        assertNull(retry.pending)
        val fresh = retry.failed("another-rule", timeout, 10000)!!
        assertEquals(1L, fresh.attempt)
        assertEquals(15000L, fresh.dueAt)
    }

    @Test fun absentEligibilityCancelsAndDoesNotReplayAnOldTimer() {
        val retry = AutomaticEnableRetry()
        retry.failed(key, timeout, 0)
        retry.retain(null)
        assertNull(retry.pending)
        assertEquals(1L, retry.failed(key, timeout, 60000)!!.attempt)
    }

    @Test fun onlyConfirmedWiredLandscapeWithSafeDataCanRetry() {
        assertTrue(AutomaticEnableRetry.eligible(landscape, preferences, decision, true))
        listOf(
            landscape.copy(plugged = 0), landscape.copy(plugged = 4),
            landscape.copy(plugged = 8), landscape.copy(plugged = -1),
            landscape.copy(landscape = false), landscape.copy(landscape = null),
            landscape.copy(interactive = false), landscape.copy(locked = true),
            landscape.copy(manual = true), landscape.copy(temperature = null),
            landscape.copy(temperature = 42f), landscape.copy(temperature = 46f),
            landscape.copy(battery = 19), landscape.copy(battery = -1),
        ).forEach {
            assertFalse(it.toString(), AutomaticEnableRetry.eligible(it, preferences, decision, true))
        }
        assertFalse(AutomaticEnableRetry.eligible(landscape, preferences.copy(automatic = false), decision, true))
        assertFalse(AutomaticEnableRetry.eligible(landscape, preferences.copy(paused = true), decision, true))
        assertFalse(AutomaticEnableRetry.eligible(landscape, preferences, decision, false))
    }

    @Test fun restoreDisableAndPendingDecisionsNeverAuthorizeEnableRetries() {
        listOf(Action.RESTORE, Action.DISABLE, null).forEach {
            assertFalse(AutomaticEnableRetry.eligible(landscape, preferences, decision.copy(action = it), true))
        }
    }

    @Test fun portraitCancelsRetryEvenWhileExitDelayIsStillPending() {
        val engine = RuleEngine()
        engine.evaluate(landscape, preferences, emptyList(), 0, 0)
        val active = engine.evaluate(landscape, preferences, emptyList(), 2000, 0)
        val retry = AutomaticEnableRetry()
        retry.failed(AutomaticEnableRetry.key(active), timeout, 2000)
        val exit = engine.evaluate(landscape.copy(landscape = false), preferences, emptyList(), 3000, 0)
        assertNull(exit.action)
        assertEquals(13000L, exit.dueAt)
        retry.retain(AutomaticEnableRetry.key(exit).takeIf {
            AutomaticEnableRetry.eligible(landscape.copy(landscape = false), preferences, exit, true)
        })
        assertNull(retry.pending)
    }

    @Test fun highTemperatureCooldownCannotReenableBeforeHysteresisRecovery() {
        val engine = RuleEngine()
        engine.evaluate(landscape.copy(temperature = 44f), preferences, emptyList(), 0, 0)
        val warm = landscape.copy(temperature = 40f)
        val d = engine.evaluate(warm, preferences, emptyList(), 10000, 0)
        assertEquals(Action.DISABLE, d.action)
        assertFalse(AutomaticEnableRetry.eligible(warm, preferences, d, true))
    }

    @Test fun higherPriorityDisableBlocksALandscapeRetry() {
        val engine = RuleEngine()
        val d = engine.evaluate(landscape, preferences,
            listOf(Rule(action = Action.DISABLE, delaySeconds = 0)), 0, 0)
        assertEquals(Action.DISABLE, d.action)
        assertFalse(AutomaticEnableRetry.eligible(landscape, preferences, d, true))
    }

    @Test fun missingForegroundEvidenceDoesNotRetryUsingAGuess() {
        val p = preferences.copy(apps = listOf(AppRule("example.game", "游戏")))
        val d = RuleEngine().evaluate(landscape, p, emptyList(), 0, 0)
        assertNull(d.action)
        assertFalse(AutomaticEnableRetry.eligible(landscape, p, d, true))
    }

    @Test fun bypassTimeoutRetriesThenSucceedsAndStillRestoresTheOriginalState() = runTest {
        val fake = Fake().apply { sync = false }
        val controller = VivoDirectChargeController(fake, { 2 },
            { testScheduler.currentTime }, { testScheduler.currentTime })
        val retry = AutomaticEnableRetry()
        val result = controller.enable()
        assertEquals("BYPASS_TIMEOUT", result.code)
        val next = retry.failed(key, result, testScheduler.currentTime)!!
        delay(next.dueAt - testScheduler.currentTime)
        controller.refresh()
        assertFalse(controller.manualDetected)
        fake.sync = true
        val recovered = controller.enable()
        assertTrue(recovered.success)
        retry.failed(key, recovered, testScheduler.currentTime)
        assertNull(retry.pending)
        assertEquals(listOf(true, true), fake.writes)
        assertTrue(controller.restorePreviousState().success)
        assertFalse(fake.setting)
        assertFalse(fake.bypass)
    }

    @Test fun settingsRollbackCanBeRetriedWithoutFalseManualOverride() = runTest {
        val fake = Fake().apply { revert = true }
        val controller = VivoDirectChargeController(fake, { 2 },
            { testScheduler.currentTime }, { testScheduler.currentTime })
        val retry = AutomaticEnableRetry()
        val failed = controller.enable()
        assertEquals("SETTINGS_MISMATCH", failed.code)
        val next = retry.failed(key, failed, testScheduler.currentTime)!!
        delay(next.dueAt - testScheduler.currentTime)
        controller.refresh()
        assertFalse(controller.manualDetected)
        fake.revert = false
        assertTrue(controller.enable().success)
        assertEquals(listOf(true, true), fake.writes)
    }

    @Test fun manualChangeDuringRetryDelayTakesPriority() = runTest {
        val fake = Fake().apply { sync = false }
        val controller = VivoDirectChargeController(fake, { 2 },
            { testScheduler.currentTime }, { testScheduler.currentTime })
        val retry = AutomaticEnableRetry()
        retry.failed(key, controller.enable(), testScheduler.currentTime)
        fake.setting = false
        fake.bypass = false
        controller.refresh()
        assertTrue(controller.manualDetected)
        val env = landscape.copy(manual = true)
        val d = RuleEngine().evaluate(env, preferences, emptyList(), testScheduler.currentTime, 0)
        retry.retain(AutomaticEnableRetry.key(d).takeIf {
            AutomaticEnableRetry.eligible(env, preferences, d, true)
        })
        assertNull(retry.pending)
        assertEquals(listOf(true), fake.writes)
    }
}
