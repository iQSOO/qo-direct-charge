package com.iqsoo.qodirect.automation

/** Physical power is the first gate, before shell queries, rules, deadlines or retries. */
class PowerConnectionGate {
    enum class Result { READY, WAIT, RESTORE_ONCE }

    private var cleanupAttempted = false

    fun evaluate(e: Environment, hasLease: Boolean): Result {
        if (e.wired) {
            cleanupAttempted = false
            return Result.READY
        }
        if (hasLease && !cleanupAttempted) {
            cleanupAttempted = true
            return Result.RESTORE_ONCE
        }
        return Result.WAIT
    }
}
