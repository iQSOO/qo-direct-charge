package com.iqsoo.qodirect.monitoring

import org.junit.Assert.*
import org.junit.Test

class ForegroundEvidenceTest {
    @Test
    fun requiresActualResumeEvidence() {
        val e = ForegroundEvidence()
        val t = e.begin(true)
        assertFalse(e.result(t).known)
        e.accept(t, ForegroundEvidence.Event.RESUMED, "game.one", "Game")
        assertEquals("game.one", e.result(t).packageName)
    }

    @Test
    fun pauseRemovesForeground() {
        val e = ForegroundEvidence()
        val t = e.begin(true)
        e.accept(t, ForegroundEvidence.Event.RESUMED, "game.one", "Game")
        e.accept(t, ForegroundEvidence.Event.PAUSED, "game.one", "Game")
        assertFalse(e.result(t).known)
    }

    @Test
    fun multiWindowDoesNotGuessWhichAppIsTop() {
        val e = ForegroundEvidence()
        val t = e.begin(true)
        e.accept(t, ForegroundEvidence.Event.RESUMED, "game.one", "A")
        e.accept(t, ForegroundEvidence.Event.RESUMED, "game.two", "B")
        assertFalse(e.result(t).known)
        e.accept(t, ForegroundEvidence.Event.PAUSED, "game.one", "A")
        assertEquals("game.two", e.result(t).packageName)
    }

    @Test
    fun samePackageMultipleActivitiesRemainKnown() {
        val e = ForegroundEvidence()
        val t = e.begin(true)
        e.accept(t, ForegroundEvidence.Event.RESUMED, "game.one", "A")
        e.accept(t, ForegroundEvidence.Event.RESUMED, "game.one", "B")
        assertEquals("game.one", e.result(t).packageName)
    }

    @Test
    fun powerOrPermissionTransitionRejectsOldQueryResults() {
        val e = ForegroundEvidence()
        val old = e.begin(true)
        e.accept(old, ForegroundEvidence.Event.RESUMED, "old.game", "A")
        e.invalidate()
        e.accept(old, ForegroundEvidence.Event.RESUMED, "old.game", "A")
        assertFalse(e.result(old).known)
        val fresh = e.begin(true)
        assertFalse(e.result(fresh).known)
        e.accept(fresh, ForegroundEvidence.Event.RESUMED, "new.game", "B")
        assertEquals("new.game", e.result(fresh).packageName)
    }

    @Test
    fun screenOffOrShutdownClearsEvidence() {
        val e = ForegroundEvidence()
        val t = e.begin(true)
        e.accept(t, ForegroundEvidence.Event.RESUMED, "game.one", "A")
        e.accept(t, ForegroundEvidence.Event.RESET)
        assertFalse(e.result(t).known)
    }

    @Test
    fun reseedingNeverReusesPreviousForeground() {
        val e = ForegroundEvidence()
        val t = e.begin(true)
        e.accept(t, ForegroundEvidence.Event.RESUMED, "game.one", "A")
        val fresh = e.begin(true)
        assertFalse(e.result(fresh).known)
    }
}
