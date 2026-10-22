package com.iqsoo.qodirect.automation

import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {
    private val ready =
        Environment(
            plugged = 1,
            battery = 70,
            temperature = 32f,
            landscape = true,
            foreground = "game.one",
            foregroundKnown = true,
            usageGranted = true,
        )
    private val prefs = Preferences(automatic = true)

    @Test
    fun landscapeRequiresContinuousTwoSeconds() {
        val engine = RuleEngine()
        assertNull(engine.evaluate(ready, prefs, emptyList(), 0, 720).action)
        assertEquals(Action.ENABLE, engine.evaluate(ready, prefs, emptyList(), 2000, 720).action)
    }

    @Test
    fun rapidRotationCancelsPending() {
        val e = RuleEngine()
        e.evaluate(ready, prefs, emptyList(), 0, 0)
        e.evaluate(ready.copy(landscape = false), prefs, emptyList(), 1000, 0)
        assertNull(e.evaluate(ready, prefs, emptyList(), 1500, 0).action)
        assertNull(e.evaluate(ready, prefs, emptyList(), 3000, 0).action)
        assertEquals(Action.ENABLE, e.evaluate(ready, prefs, emptyList(), 3500, 0).action)
    }

    @Test
    fun portraitExitsAfterTenSeconds() {
        val e = RuleEngine()
        e.evaluate(ready, prefs, emptyList(), 0, 0)
        e.evaluate(ready, prefs, emptyList(), 2000, 0)
        val portrait = ready.copy(landscape = false)
        assertNull(e.evaluate(portrait, prefs, emptyList(), 3000, 0).action)
        assertNull(e.evaluate(portrait, prefs, emptyList(), 12999, 0).action)
        assertEquals(Action.RESTORE, e.evaluate(portrait, prefs, emptyList(), 13000, 0).action)
    }

    @Test
    fun unplugIsUrgent() {
        val d = RuleEngine().evaluate(ready.copy(plugged = 0), prefs, emptyList(), 1, 0)
        assertEquals(Action.RESTORE, d.action)
        assertTrue(d.urgent)
    }

    @Test
    fun wirelessCannotEnable() {
        assertEquals(
            Action.RESTORE,
            RuleEngine().evaluate(ready.copy(plugged = 4), prefs, emptyList(), 9000, 0).action,
        )
    }

    @Test
    fun batteryStatusIsNotPowerConnection() {
        assertTrue(ready.copy(batteryStatus = 4).connected)
        assertFalse(ready.copy(plugged = 0, batteryStatus = 2).connected)
    }

    @Test
    fun appRulesSwitchWithoutDisableGap() {
        val p =
            prefs.copy(
                policy = prefs.policy.copy(landscapeEnabled = false),
                apps =
                    listOf(
                        AppRule("game.one", "一", enterSeconds = 0),
                        AppRule("game.two", "二", enterSeconds = 5),
                    ),
            )
        val e = RuleEngine()
        assertEquals(Action.ENABLE, e.evaluate(ready, p, emptyList(), 0, 0).action)
        assertEquals(
            Action.ENABLE,
            e.evaluate(ready.copy(foreground = "game.two"), p, emptyList(), 100, 0).action,
        )
    }

    @Test
    fun unknownAppCannotBeInvertedToTrue() {
        val c =
            Condition(
                ConditionType.NOT,
                children = listOf(Condition(ConditionType.APP_FOREGROUND, "game.one")),
            )
        assertEquals(Truth.UNKNOWN, Predicates.evaluate(c, ready.copy(foregroundKnown = false), 0))
    }

    @Test
    fun unknownAppDoesNotTriggerDelayedExit() {
        val p = prefs.copy(apps = listOf(AppRule("game.one", "一", enterSeconds = 0)))
        val e = RuleEngine()
        e.evaluate(ready, p, emptyList(), 0, 0)
        val d = e.evaluate(ready.copy(foregroundKnown = false), p, emptyList(), 30000, 0)
        assertNull(d.action)
    }

    @Test
    fun screenOffRestoresAtSixtySeconds() {
        val e = RuleEngine()
        val off = ready.copy(interactive = false, screenOffSince = 1000)
        assertNull(e.evaluate(off, prefs, emptyList(), 60000, 0).action)
        assertEquals(Action.RESTORE, e.evaluate(off, prefs, emptyList(), 61000, 0).action)
    }

    @Test
    fun wakingCancelsScreenDeadline() {
        val e = RuleEngine()
        e.evaluate(ready.copy(interactive = false, screenOffSince = 0), prefs, emptyList(), 100, 0)
        assertNotEquals(Action.RESTORE, e.evaluate(ready, prefs, emptyList(), 61000, 0).action)
    }

    @Test
    fun blacklistDoesNotBlockScreenTimeout() {
        val p = prefs.copy(apps = listOf(AppRule("bad.app", "黑名单", blacklisted = true)))
        assertEquals(
            Action.RESTORE,
            RuleEngine()
                .evaluate(
                    ready.copy(interactive = false, screenOffSince = 0, foregroundKnown = false),
                    p,
                    emptyList(),
                    60000,
                    0,
                )
                .action,
        )
    }

    @Test
    fun screenOffKeepPolicyHasNoDeadline() {
        val d =
            RuleEngine()
                .evaluate(
                    ready.copy(interactive = false, screenOffSince = 0),
                    prefs.copy(policy = prefs.policy.copy(screenOffSeconds = -1)),
                    emptyList(),
                    900000,
                    0,
                )
        assertNull(d.action)
        assertNull(d.dueAt)
    }

    @Test
    fun screenHoldRuleIsExplicit() {
        val r =
            Rule(
                id = "hold",
                delaySeconds = 0,
                allowScreenOff = true,
                condition = Condition(ConditionType.SCREEN_OFF),
            )
        assertEquals(
            Action.ENABLE,
            RuleEngine()
                .evaluate(
                    ready.copy(interactive = false, screenOffSince = 0),
                    prefs,
                    listOf(r),
                    60000,
                    0,
                )
                .action,
        )
    }

    @Test
    fun highTemperatureBypassesDebounce() {
        val d = RuleEngine().evaluate(ready.copy(temperature = 43f), prefs, emptyList(), 0, 0)
        assertEquals(Action.DISABLE, d.action)
        assertTrue(d.urgent)
    }

    @Test
    fun temperatureUsesHysteresis() {
        val e = RuleEngine()
        e.evaluate(ready.copy(temperature = 43f), prefs, emptyList(), 0, 0)
        assertEquals(
            Action.DISABLE,
            e.evaluate(ready.copy(temperature = 40f), prefs, emptyList(), 1000, 0).action,
        )
        assertNull(e.evaluate(ready.copy(temperature = 38f), prefs, emptyList(), 2000, 0).action)
    }

    @Test
    fun lowBatteryCannotEnable() {
        assertEquals(
            Action.RESTORE,
            RuleEngine().evaluate(ready.copy(battery = 10), prefs, emptyList(), 9000, 0).action,
        )
    }

    @Test
    fun manualPauseHasPriority() {
        assertNull(
            RuleEngine()
                .evaluate(ready, prefs.copy(paused = true), listOf(Rule(delaySeconds = 0)), 9000, 0)
                .action
        )
    }

    @Test
    fun nativeManualControlHasPriority() {
        assertNull(
            RuleEngine()
                .evaluate(ready.copy(manual = true), prefs, listOf(Rule(delaySeconds = 0)), 9000, 0)
                .action
        )
    }

    @Test
    fun highestPriorityWins() {
        val a = Rule(id = "a", action = Action.ENABLE, priority = 100, delaySeconds = 0)
        val b = a.copy(id = "b", action = Action.DISABLE, priority = 200)
        assertEquals(Action.DISABLE, RuleEngine().evaluate(ready, prefs, listOf(a, b), 0, 0).action)
    }

    @Test
    fun logicalAndOrAndNot() {
        val c =
            Condition(
                ConditionType.ALL,
                children =
                    listOf(
                        Condition(ConditionType.POWER_CONNECTED),
                        Condition(
                            ConditionType.ANY,
                            children =
                                listOf(
                                    Condition(ConditionType.PORTRAIT),
                                    Condition(
                                        ConditionType.NOT,
                                        children = listOf(Condition(ConditionType.SCREEN_OFF)),
                                    ),
                                ),
                        ),
                    ),
            )
        assertEquals(Truth.YES, Predicates.evaluate(c, ready, 0))
    }

    @Test
    fun timeRangeCrossesMidnight() {
        assertTrue(Predicates.timeMatches("22:00-06:00", 1380))
        assertTrue(Predicates.timeMatches("22:00-06:00", 120))
        assertFalse(Predicates.timeMatches("22:00-06:00", 720))
    }

    @Test
    fun foregroundBlacklistRestores() {
        val p = prefs.copy(apps = listOf(AppRule("game.one", "黑名单", blacklisted = true)))
        assertEquals(Action.RESTORE, RuleEngine().evaluate(ready, p, emptyList(), 0, 0).action)
    }

    @Test
    fun disconnectStopsSecondaryRulesAndOnlyRequestsSafeExit() {
        val r =
            Rule(
                action = Action.LOG,
                delaySeconds = 0,
                condition = Condition(ConditionType.POWER_DISCONNECTED),
            )
        val d = RuleEngine().evaluate(ready.copy(plugged = 0), prefs, listOf(r), 0, 0)
        assertEquals(Action.RESTORE, d.action)
        assertTrue(d.effects.isEmpty())
        assertNull(d.dueAt)
        assertTrue(d.urgent)
    }

    @Test
    fun effectsRunOncePerConditionEntry() {
        val r = Rule(action = Action.NOTIFY, delaySeconds = 0)
        val e = RuleEngine()
        assertEquals(1, e.evaluate(ready, prefs, listOf(r), 0, 0).effects.size)
        assertTrue(e.evaluate(ready, prefs, listOf(r), 1, 0).effects.isEmpty())
        e.evaluate(ready.copy(landscape = false), prefs, listOf(r), 2, 0)
        assertEquals(1, e.evaluate(ready, prefs, listOf(r), 3, 0).effects.size)
    }

    @Test
    fun rulePauseCanBeResumedByAnotherRule() {
        val pause = Rule(id = "pause", action = Action.PAUSE, delaySeconds = 0)
        val resume =
            pause.copy(
                id = "resume",
                action = Action.RESUME,
                condition = Condition(ConditionType.PORTRAIT),
            )
        val e = RuleEngine()
        assertEquals(Action.RESTORE, e.evaluate(ready, prefs, listOf(pause, resume), 0, 0).action)
        assertNotEquals(
            "自定义规则已暂停自动管理",
            e.evaluate(ready.copy(landscape = false), prefs, listOf(pause, resume), 1, 0).reason,
        )
    }

    @Test
    fun cancelSuppressesPendingEnable() {
        val c = Rule(action = Action.CANCEL, delaySeconds = 0)
        val d = RuleEngine().evaluate(ready, prefs, listOf(c), 10000, 0)
        assertNull(d.action)
        assertNull(d.dueAt)
    }

    @Test
    fun restartReevaluatesWithoutRememberedEnable() {
        val e = RuleEngine()
        val d = e.evaluate(ready, prefs, emptyList(), 50000, 0)
        assertNull(d.action)
        assertEquals(52000L, d.dueAt)
    }

    @Test
    fun unknownBatteryNeverEnables() {
        assertEquals(
            Action.RESTORE,
            RuleEngine().evaluate(ready.copy(temperature = null), prefs, emptyList(), 0, 0).action,
        )
    }

    @Test
    fun invalidImportsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Validation.export(RuleExport(policy = Policy(maxTemperature = 90f)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Validation.rule(Rule(condition = Condition(ConditionType.NOT)))
        }
    }

    @Test
    fun allDayTimeWindow() {
        assertTrue(Predicates.timeMatches("00:00-00:00", 0))
        assertTrue(Predicates.timeMatches("00:00-00:00", 1439))
    }

    @Test
    fun disablingPauseRuleClearsRulePause() {
        val engine = RuleEngine()
        engine.evaluate(ready, prefs, listOf(Rule(action = Action.PAUSE, delaySeconds = 0)), 0, 0)
        engine.reconfigure()
        assertNotEquals("自定义规则已暂停自动管理", engine.evaluate(ready, prefs, emptyList(), 1, 0).reason)
    }

    @Test
    fun brightLockScreenCancelsPendingScreenOffExit() {
        val e = RuleEngine()
        e.evaluate(
            ready.copy(interactive = false, locked = true, screenOffSince = 0),
            prefs,
            emptyList(),
            59000,
            0,
        )
        val awake =
            e.evaluate(
                ready.copy(interactive = true, locked = true, screenOffSince = null),
                prefs,
                emptyList(),
                61000,
                0,
            )
        assertNull(awake.action)
        assertNull(awake.dueAt)
        assertEquals(
            Truth.YES,
            Predicates.evaluate(Condition(ConditionType.SCREEN_ON), ready.copy(locked = true), 0),
        )
    }

    @Test
    fun aodAndLockedScreenStillExpireWhenNotInteractive() {
        val d =
            RuleEngine()
                .evaluate(
                    ready.copy(interactive = false, locked = true, screenOffSince = 0),
                    prefs,
                    emptyList(),
                    60000,
                    0,
                )
        assertEquals(Action.RESTORE, d.action)
    }

    @Test
    fun revokedUsageAccessMakesCachedForegroundUnknown() {
        val c = Condition(ConditionType.APP_FOREGROUND, "game.one")
        assertEquals(Truth.UNKNOWN, Predicates.evaluate(c, ready.copy(usageGranted = false), 0))
    }
}
