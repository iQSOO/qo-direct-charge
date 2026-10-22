package com.iqsoo.qodirect.automation

import com.iqsoo.qodirect.charging.ControlResult

/** Retry only an unconfirmed automatic enable, while the same winning rule remains eligible. */
class AutomaticEnableRetry {
    data class Pending(val key: String, val attempt: Long, val dueAt: Long, val errorCode: String)

    var pending: Pending? = null
        private set

    fun reset() {
        pending = null
    }

    fun retain(key: String?) {
        if (pending?.key != key) reset()
    }

    fun failed(key: String, result: ControlResult, elapsed: Long): Pending? {
        if (result.success || result.code !in RETRYABLE) {
            reset()
            return null
        }
        val attempt = if (pending?.key == key) pending!!.attempt.coerceAtMost(Long.MAX_VALUE - 1) + 1 else 1
        val seconds = when (attempt) {
            1L -> 5L
            2L -> 10L
            3L -> 20L
            else -> 30L
        }
        return Pending(key, attempt, elapsed + seconds * 1000, result.code).also { pending = it }
    }

    companion object {
        private val RETRYABLE = setOf(
            "BYPASS_TIMEOUT", "SETTINGS_MISMATCH", "COMMAND_FAILED", "TRANSPORT_TIMEOUT",
            "TRANSPORT_ERROR", "READ_FAILED", "UNKNOWN_SETTINGS",
        )

        fun key(d: Decision) = "${d.action}:${d.ruleId}:${d.reason}"

        fun eligible(e: Environment, p: Preferences, d: Decision, serviceRunning: Boolean): Boolean =
            serviceRunning && p.automatic && !p.paused && !e.manual && e.wired &&
                e.interactive && !e.locked && e.landscape == true &&
                e.battery in p.policy.minBattery..p.policy.maxBattery &&
                e.temperature != null && e.temperature < p.policy.maxTemperature &&
                d.action == Action.ENABLE
    }
}
