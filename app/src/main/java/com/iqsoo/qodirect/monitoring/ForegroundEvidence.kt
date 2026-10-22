package com.iqsoo.qodirect.monitoring

/**
 * In-memory lifecycle evidence. A power/screen/permission transition invalidates every old query.
 */
class ForegroundEvidence {
    enum class Event {
        RESUMED,
        PAUSED,
        RESET,
    }

    data class Result(val packageName: String? = null) {
        val known: Boolean
            get() = packageName != null
    }

    private var generation = 0L
    private val resumed = mutableSetOf<Pair<String, String>>()

    @Synchronized
    fun invalidate() {
        generation++
        resumed.clear()
    }

    @Synchronized
    fun begin(reseed: Boolean): Long {
        if (reseed) resumed.clear()
        return generation
    }

    @Synchronized fun isCurrent(token: Long): Boolean = token == generation

    @Synchronized
    fun accept(token: Long, event: Event, packageName: String = "", className: String = "") {
        if (token != generation) return
        if (event == Event.RESET) {
            resumed.clear()
            return
        }
        if (packageName.isBlank()) return
        val component = packageName to className
        when (event) {
            Event.RESUMED -> resumed.add(component)
            Event.PAUSED -> resumed.remove(component)
            Event.RESET -> Unit
        }
    }

    @Synchronized
    fun result(token: Long): Result {
        if (token != generation) return Result()
        val packages = resumed.map { it.first }.distinct()
        return Result(packages.singleOrNull())
    }
}
