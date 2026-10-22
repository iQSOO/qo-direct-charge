@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.iqsoo.qodirect.charging

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ControllerTest {
    class Fake : ShellTransport {
        var setting = false
        var bypass = false
        var support = true
        var uid = 2000
        var code = 0
        var partialWrite = false
        var sync = true
        var revert = false
        var throwTransport = false
        var timeout = false
        var timeoutAfterWrite = false
        var writes = mutableListOf<Boolean>()

        override suspend fun execute(op: Int): ShellReply {
            if (timeout) throw ShellTransportTimeoutException()
            if (throwTransport) throw SecurityException("授权已撤销")
            if (op == 0)
                return ShellReply(
                    mapOf(
                        "uid" to uid,
                        "exit" to 0,
                        "settingsExit" to 0,
                        "bypassExit" to 0,
                        "allSupportExit" to 0,
                        "gameSupportExit" to 0,
                    ),
                    mapOf(
                        "settings" to if (setting) "1" else "0",
                        "bypass" to if (bypass) "1" else "0",
                        "allSupport" to if (support) "1" else "0",
                        "gameSupport" to if (support) "1" else "0",
                    ),
                )
            writes += (op == 1)
            if (code == 0 || partialWrite) {
                if (!revert) setting = op == 1
                if (sync) bypass = setting
            }
            if (timeoutAfterWrite) {
                timeoutAfterWrite = false
                throw ShellTransportTimeoutException()
            }
            return ShellReply(
                mapOf("exit" to code),
                mapOf("output" to if (code != 0) "permission denied" else ""),
            )
        }
    }

    @Test
    fun enableVerifiesReadback() = runTest {
        val f = Fake()
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        val r = c.enable()
        assertTrue(r.success)
        assertEquals(true, r.settings)
        assertEquals(true, r.bypass)
        assertTrue(c.hasLease)
    }

    @Test
    fun disableVerifiesBothFlagsFromEnabledState() = runTest {
        val f =
            Fake().apply {
                setting = true
                bypass = true
            }
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        assertTrue(c.disable().success)
        assertFalse(f.setting)
        assertFalse(f.bypass)
        assertTrue(c.restorePreviousState().success)
        assertTrue(f.setting)
    }

    @Test
    fun failedExitIsNotSuccess() = runTest {
        val f = Fake().apply { code = 1 }
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        val r = c.enable()
        assertFalse(r.success)
        assertEquals(1, r.exitCode)
        assertFalse(c.hasLease)
    }

    @Test
    fun bypassMismatchTimesOut() = runTest {
        val f = Fake().apply { sync = false }
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        val r = c.enable()
        assertFalse(r.success)
        assertEquals("BYPASS_TIMEOUT", r.code)
        assertEquals(true, r.settings)
        assertEquals(false, r.bypass)
    }

    @Test
    fun oemSettingsRollbackIsNotSuccess() = runTest {
        val f = Fake().apply { revert = true }
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        assertEquals("SETTINGS_MISMATCH", c.enable().code)
    }

    @Test
    fun duplicateRequestsAreCoalesced() = runTest {
        val f = Fake()
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        coroutineScope { repeat(10) { launch { c.enable() } } }
        assertEquals(1, f.writes.size)
    }

    @Test
    fun restoresOriginalFalse() = runTest {
        val f = Fake()
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        c.enable()
        assertTrue(c.restorePreviousState().success)
        assertFalse(f.setting)
        assertFalse(c.hasLease)
    }

    @Test
    fun preservesNativeManualChanges() = runTest {
        val f = Fake()
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        c.enable()
        f.setting = false
        f.bypass = false
        c.refresh()
        assertTrue(c.manualDetected)
        assertFalse(c.hasLease)
        c.restorePreviousState()
        assertEquals(1, f.writes.size)
    }

    @Test
    fun restoreChecksForRace() = runTest {
        val f = Fake()
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        c.enable()
        f.setting = false
        val r = c.restorePreviousState()
        assertEquals("RESTORE_CONFLICT", r.code)
        assertEquals(1, f.writes.size)
    }

    @Test
    fun processRestartNeverReplaysPreviousValue() = runTest {
        val f = Fake()
        VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
            .enable()
        val restarted =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        assertEquals("NO_LEASE", restarted.restorePreviousState().code)
        assertEquals(1, f.writes.size)
    }

    @Test
    fun unsupportedDeviceCannotEnable() = runTest {
        val f = Fake().apply { support = false }
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        assertEquals("UNSUPPORTED", c.enable().code)
        assertTrue(f.writes.isEmpty())
    }

    @Test
    fun shellUidIsRequired() = runTest {
        val f = Fake().apply { uid = 10001 }
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        assertEquals("UID_INVALID", c.enable().code)
        assertTrue(f.writes.isEmpty())
    }

    @Test
    fun revokedPermissionReportsTransportFailure() = runTest {
        val f = Fake().apply { throwTransport = true }
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        assertEquals("TRANSPORT_ERROR", c.enable().code)
    }

    @Test
    fun noOwnershipOfAlreadyEnabledNativeState() = runTest {
        val f =
            Fake().apply {
                setting = true
                bypass = true
            }
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        val unchanged = c.enable()
        assertTrue(unchanged.success)
        assertNull(unchanged.exitCode)
        assertFalse(c.hasLease)
        c.restorePreviousState()
        assertTrue(f.writes.isEmpty())
    }

    @Test
    fun unsafeRestoreNeverWritesEnable() = runTest {
        val f =
            Fake().apply {
                setting = true
                bypass = true
            }
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        c.disable()
        val r = c.restore(false)
        assertEquals("RESTORE_ENABLE_SKIPPED", r.code)
        assertEquals(listOf(false), f.writes)
    }

    @Test
    fun disconnectDropsLeaseWithoutBlindWrite() = runTest {
        val f = Fake()
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        c.enable()
        c.invalidate()
        assertEquals("NO_LEASE", c.restorePreviousState().code)
        assertEquals(1, f.writes.size)
    }

    @Test
    fun wallClockRollbackCannotExtendConfirmationTimeout() = runTest {
        val f = Fake().apply { sync = false }
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { 1_000_000L - testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        assertEquals("BYPASS_TIMEOUT", c.enable().code)
        assertTrue(testScheduler.currentTime in 5000L..6000L)
    }

    @Test
    fun transportTimeoutReportsFailureAndCanRetry() = runTest {
        val f = Fake().apply { timeout = true }
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        assertEquals("TRANSPORT_TIMEOUT", c.enable().code)
        f.timeout = false
        assertTrue(c.enable().success)
    }

    @Test
    fun failedRefreshDoesNotPresentOldFlagsAsCurrent() = runTest {
        val f = Fake()
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        c.enable()
        f.throwTransport = true
        val snapshot = c.refresh()
        assertNull(snapshot.settings)
        assertNull(snapshot.bypass)
        assertNotNull(snapshot.error)
    }

    @Test
    fun failedCommandStillReadsActualPostCommandState() = runTest {
        val f =
            Fake().apply {
                code = 1
                partialWrite = true
            }
        val c =
            VivoDirectChargeController(
                f,
                { 5 },
                { testScheduler.currentTime },
                { testScheduler.currentTime },
            )
        val result = c.enable()
        assertFalse(result.success)
        assertEquals(1, result.exitCode)
        assertEquals(true, result.settings)
        assertEquals(true, result.bypass)
    }

    @Test
    fun timedOutWriteThatActuallySucceededIsReadBeforeRetryAndCanRestore() = runTest {
        val f = Fake().apply { timeoutAfterWrite = true }
        val c = VivoDirectChargeController(f, { 2 }, { testScheduler.currentTime }, { testScheduler.currentTime })
        assertEquals("TRANSPORT_TIMEOUT", c.enable().code)
        assertTrue(c.refresh().settings == true)
        assertFalse(c.manualDetected)
        assertEquals("ALREADY_SET", c.enable().code)
        assertEquals(listOf(true), f.writes)
        assertTrue(c.restorePreviousState().success)
        assertFalse(f.setting)
        assertEquals(listOf(true, false), f.writes)
    }

    @Test
    fun timedOutWriteThatDidNotStickCanRetryWithoutManualConflict() = runTest {
        val f = Fake().apply { timeoutAfterWrite = true; revert = true }
        val c = VivoDirectChargeController(f, { 2 }, { testScheduler.currentTime }, { testScheduler.currentTime })
        assertEquals("TRANSPORT_TIMEOUT", c.enable().code)
        c.refresh()
        assertFalse(c.hasLease)
        assertFalse(c.manualDetected)
        f.revert = false
        assertTrue(c.enable().success)
        assertTrue(c.restorePreviousState().success)
        assertFalse(f.setting)
    }

    @Test
    fun partialWriteWithNonzeroExitStillRetainsTheOriginalBaseline() = runTest {
        val f = Fake().apply { code = 1; partialWrite = true }
        val c = VivoDirectChargeController(f, { 2 }, { testScheduler.currentTime }, { testScheduler.currentTime })
        assertEquals("COMMAND_FAILED", c.enable().code)
        assertTrue(c.hasLease)
        f.code = 0
        assertTrue(c.restorePreviousState().success)
        assertEquals(listOf(true, false), f.writes)
    }

    @Test
    fun delayedBypassConfirmationDoesNotNeedAnotherEnableWrite() = runTest {
        val f = Fake().apply { sync = false }
        val c = VivoDirectChargeController(f, { 2 }, { testScheduler.currentTime }, { testScheduler.currentTime })
        assertEquals("BYPASS_TIMEOUT", c.enable().code)
        f.bypass = true
        val fresh = c.refresh()
        assertTrue(fresh.settings == true && fresh.bypass == true)
        assertEquals("ALREADY_SET", c.enable().code)
        assertEquals(listOf(true), f.writes)
    }

    @Test
    fun duplicateRetryRequestsAreSerializedAndMergedAfterRecovery() = runTest {
        val f = Fake().apply { sync = false }
        val c = VivoDirectChargeController(f, { 2 }, { testScheduler.currentTime }, { testScheduler.currentTime })
        assertEquals("BYPASS_TIMEOUT", c.enable().code)
        f.sync = true
        coroutineScope { repeat(10) { launch { assertTrue(c.enable().success) } } }
        assertEquals(listOf(true, true), f.writes)
    }

    @Test
    fun restoringAfterAFullRollbackDoesNotWriteOrClaimManualChange() = runTest {
        val f = Fake().apply { revert = true }
        val c = VivoDirectChargeController(f, { 2 }, { testScheduler.currentTime }, { testScheduler.currentTime })
        assertEquals("SETTINGS_MISMATCH", c.enable().code)
        assertFalse(c.hasLease)
        assertEquals("NO_LEASE", c.restorePreviousState().code)
        assertFalse(c.manualDetected)
        assertEquals(listOf(true), f.writes)
    }

    @Test
    fun stoppingAfterUncertainWriteRestoresOnlyAfterReadingReality() = runTest {
        val f = Fake().apply { timeoutAfterWrite = true }
        val c = VivoDirectChargeController(f, { 2 }, { testScheduler.currentTime }, { testScheduler.currentTime })
        assertEquals("TRANSPORT_TIMEOUT", c.enable().code)
        assertTrue(c.restorePreviousState().success)
        assertEquals(listOf(true, false), f.writes)
        assertFalse(f.setting)
    }
}
