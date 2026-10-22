package com.iqsoo.qodirect.automation

import kotlinx.serialization.Serializable

@Serializable
enum class Phase {
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    DISCONNECTED,
    IDLE,
    ELIGIBLE,
    ENABLE_PENDING,
    ENABLED,
    DISABLE_PENDING,
    RESTORING,
    MANUAL_OVERRIDE,
    ERROR,
}

@Serializable
enum class Action {
    ENABLE,
    DISABLE,
    RESTORE,
    CANCEL,
    PAUSE,
    RESUME,
    LOG,
    NOTIFY,
}

@Serializable
enum class ConditionType {
    ALL,
    ANY,
    NOT,
    POWER_CONNECTED,
    POWER_DISCONNECTED,
    LANDSCAPE,
    PORTRAIT,
    APP_FOREGROUND,
    APP_EXITED,
    SCREEN_ON,
    SCREEN_OFF,
    BATTERY_RANGE,
    TEMPERATURE_RANGE,
    TIME_RANGE,
    MANUAL_STATE,
}

@Serializable
data class Condition(
    val type: ConditionType = ConditionType.LANDSCAPE,
    val value: String = "",
    val children: List<Condition> = emptyList(),
)

@Serializable
data class Rule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "新规则",
    val enabled: Boolean = true,
    val priority: Int = 100,
    val action: Action = Action.ENABLE,
    val delaySeconds: Int = 2,
    val exitDelaySeconds: Int = 10,
    val condition: Condition = Condition(),
    val window: String = "00:00-00:00",
    val allowScreenOff: Boolean = false,
)

@Serializable
data class Policy(
    val landscapeEnabled: Boolean = true,
    val landscapeOnlySelected: Boolean = false,
    val enterSeconds: Int = 2,
    val exitSeconds: Int = 10,
    val screenOffSeconds: Int = 60,
    val minBattery: Int = 20,
    val maxBattery: Int = 100,
    val maxTemperature: Float = 42f,
    val resumeTemperature: Float = 39f,
    val confirmSeconds: Int = 5,
    val cooldownSeconds: Int = 5,
    val restoreOnBoot: Boolean = false,
    val dynamicColor: Boolean = true,
    val darkMode: Int = 0,
)

@Serializable
data class AppRule(
    val packageName: String,
    val name: String,
    val enabled: Boolean = true,
    val landscapeOnly: Boolean = true,
    val enterSeconds: Int = 2,
    val exitSeconds: Int = 10,
    val blacklisted: Boolean = false,
)

@Serializable
data class Preferences(
    val automatic: Boolean = false,
    val paused: Boolean = false,
    val policy: Policy = Policy(),
    val apps: List<AppRule> = emptyList(),
)

@Serializable
data class RuleExport(
    val version: Int = 1,
    val policy: Policy = Policy(),
    val apps: List<AppRule> = emptyList(),
    val rules: List<Rule> = emptyList(),
)

data class Environment(
    val plugged: Int = -1,
    val battery: Int = -1,
    val temperature: Float? = null,
    val batteryStatus: Int = 1,
    val interactive: Boolean = true,
    val locked: Boolean = false,
    val landscape: Boolean? = null,
    val rotation: Int? = null,
    val foreground: String? = null,
    val foregroundKnown: Boolean = false,
    val usageGranted: Boolean = false,
    val screenOffSince: Long? = null,
    val bootId: Int = 0,
    val manual: Boolean = false,
) {
    val connected: Boolean
        get() = plugged > 0

    // Only AC and USB are validated candidates. Wireless and dock require independent device
    // validation.
    val wired: Boolean
        get() = plugged > 0 && plugged and 3 != 0 && plugged and 12 == 0
}

data class RuleEffect(val id: String, val name: String, val action: Action)

data class Decision(
    val action: Action?,
    val reason: String,
    val ruleId: String? = null,
    val dueAt: Long? = null,
    val urgent: Boolean = false,
    val effects: List<RuleEffect> = emptyList(),
)

enum class Truth {
    YES,
    NO,
    UNKNOWN,
}

/** Pure, three-valued predicates: NOT unknown remains unknown, including missing Usage Access. */
object Predicates {
    fun timeMatches(range: String, minute: Int): Boolean {
        val halves = range.split('-')
        if (halves.size != 2) return false
        fun parse(v: String): Int? {
            val p = v.split(':')
            if (p.size != 2) return null
            val h = p[0].toIntOrNull() ?: return null
            val m = p[1].toIntOrNull() ?: return null
            return if (h in 0..23 && m in 0..59) h * 60 + m else null
        }
        val start = parse(halves[0]) ?: return false
        val end = parse(halves[1]) ?: return false
        return if (start == end) true
        else if (start < end) minute in start until end else minute >= start || minute < end
    }

    fun evaluate(c: Condition, e: Environment, minute: Int): Truth {
        fun truth(v: Boolean) = if (v) Truth.YES else Truth.NO
        fun range(v: Float?): Truth {
            if (v == null) return Truth.UNKNOWN
            val p = c.value.split(',').mapNotNull { it.toFloatOrNull() }
            return if (p.size == 2) truth(v >= p[0] && v <= p[1]) else Truth.NO
        }
        return when (c.type) {
            ConditionType.ALL -> {
                val r = c.children.map { evaluate(it, e, minute) }
                if (r.isEmpty()) Truth.NO
                else if (Truth.NO in r) Truth.NO
                else if (Truth.UNKNOWN in r) Truth.UNKNOWN else Truth.YES
            }
            ConditionType.ANY -> {
                val r = c.children.map { evaluate(it, e, minute) }
                if (Truth.YES in r) Truth.YES
                else if (Truth.UNKNOWN in r) Truth.UNKNOWN else Truth.NO
            }
            ConditionType.NOT ->
                if (c.children.size != 1) Truth.NO
                else
                    when (evaluate(c.children[0], e, minute)) {
                        Truth.YES -> Truth.NO
                        Truth.NO -> Truth.YES
                        Truth.UNKNOWN -> Truth.UNKNOWN
                    }
            ConditionType.POWER_CONNECTED ->
                if (e.plugged < 0) Truth.UNKNOWN else truth(e.connected)
            ConditionType.POWER_DISCONNECTED ->
                if (e.plugged < 0) Truth.UNKNOWN else truth(!e.connected)
            ConditionType.LANDSCAPE -> e.landscape?.let(::truth) ?: Truth.UNKNOWN
            ConditionType.PORTRAIT -> e.landscape?.let { truth(!it) } ?: Truth.UNKNOWN
            ConditionType.APP_FOREGROUND ->
                if (!e.foregroundKnown || !e.usageGranted) Truth.UNKNOWN
                else truth(e.foreground == c.value)
            ConditionType.APP_EXITED ->
                if (!e.foregroundKnown || !e.usageGranted) Truth.UNKNOWN
                else truth(e.foreground != c.value)
            ConditionType.SCREEN_ON -> truth(e.interactive)
            ConditionType.SCREEN_OFF -> truth(!e.interactive)
            ConditionType.BATTERY_RANGE -> range(e.battery.takeIf { it >= 0 }?.toFloat())
            ConditionType.TEMPERATURE_RANGE -> range(e.temperature)
            ConditionType.TIME_RANGE -> truth(timeMatches(c.value, minute))
            ConditionType.MANUAL_STATE -> truth(e.manual == (c.value != "false"))
        }
    }

    fun usesApp(c: Condition): Boolean =
        c.type in setOf(ConditionType.APP_FOREGROUND, ConditionType.APP_EXITED) ||
            c.children.any(::usesApp)
}

/**
 * A single deterministic arbiter; callers execute only its final decision on one serialized
 * coroutine.
 */
class RuleEngine {
    private var candidate: String? = null
    private var candidateSince = 0L
    private var active: Rule? = null
    private var exitSince: Long? = null
    private var hot = false
    private var rulePaused = false
    private val effectSince = mutableMapOf<String, Long>()
    private val latched = mutableSetOf<String>()

    fun reset() {
        candidate = null
        active = null
        exitSince = null
        candidateSince = 0
    }

    fun reconfigure() {
        reset()
        rulePaused = false
        effectSince.clear()
        latched.clear()
    }

    fun evaluate(
        e: Environment,
        p: Preferences,
        custom: List<Rule>,
        now: Long,
        minute: Int,
    ): Decision {
        // Unplugged/unknown/unvalidated power never evaluates or emits secondary rule effects.
        if (!e.connected) {
            reconfigure()
            return Decision(Action.RESTORE, "外部电源已断开，退出接管", urgent = true)
        }
        if (!e.wired) {
            reconfigure()
            return Decision(Action.RESTORE, "无线或底座供电尚未验证，禁止自动开启", urgent = true)
        }
        val policy = p.policy
        val effects = mutableListOf<RuleEffect>()
        var effectDue: Long? = null
        var cancel = false
        val matchedEffects =
            custom
                .filter {
                    it.enabled &&
                        it.action !in setOf(Action.ENABLE, Action.DISABLE, Action.RESTORE) &&
                        Predicates.timeMatches(it.window, minute) &&
                        Predicates.evaluate(it.condition, e, minute) == Truth.YES
                }
                .sortedWith(compareByDescending<Rule> { it.priority }.thenBy { it.id })
        val ids = matchedEffects.map { it.id }.toSet()
        effectSince.keys.retainAll(ids)
        latched.retainAll(ids)
        if (p.automatic) {
            for (r in matchedEffects) {
                val since = effectSince.getOrPut(r.id) { now }
                val due = since + r.delaySeconds * 1000L
                if (now < due) {
                    effectDue = effectDue?.coerceAtMost(due) ?: due
                    continue
                }
                if (r.action == Action.CANCEL) cancel = true
                if (latched.add(r.id)) effects += RuleEffect(r.id, r.name, r.action)
            }
            matchedEffects
                .firstOrNull { it.action in setOf(Action.PAUSE, Action.RESUME) && it.id in latched }
                ?.let { rulePaused = it.action == Action.PAUSE }
        }
        fun result(d: Decision): Decision =
            d.copy(effects = effects, dueAt = listOfNotNull(d.dueAt, effectDue).minOrNull())
        fun decision(
            action: Action?,
            reason: String,
            ruleId: String? = null,
            dueAt: Long? = null,
            urgent: Boolean = false,
        ) = result(Decision(action, reason, ruleId, dueAt, urgent))
        if (e.temperature != null) {
            if (e.temperature >= policy.maxTemperature) hot = true
            else if (e.temperature <= policy.resumeTemperature) hot = false
        }
        if (hot) {
            reset()
            return decision(Action.DISABLE, "温度保护：暂停自动请求", urgent = true)
        }
        if (e.battery < 0 || e.temperature == null) {
            reset()
            return decision(Action.RESTORE, "电池信息未知，等待系统反馈", urgent = true)
        }
        if (e.battery !in policy.minBattery..policy.maxBattery) {
            reset()
            return decision(Action.RESTORE, "电量不在允许范围", urgent = true)
        }
        if (!p.automatic || p.paused || e.manual) {
            reset()
            return decision(null, if (e.manual) "手动操作优先，等待用户恢复自动管理" else "自动管理已暂停")
        }
        if (rulePaused) {
            reset()
            return decision(Action.RESTORE, "自定义规则已暂停自动管理")
        }
        val off = !e.interactive
        if (e.interactive && e.locked) {
            candidate = null
            candidateSince = now
            exitSince = null
            return decision(null, "设备已锁定，等待解锁后重新评估")
        }
        val screenDeadline =
            e.screenOffSince?.plus(policy.screenOffSeconds.coerceAtLeast(0) * 1000L)
        val builtins = mutableListOf<Rule>()
        if (policy.landscapeEnabled && !policy.landscapeOnlySelected)
            builtins +=
                Rule(
                    id = "builtin-landscape",
                    name = "充电时横屏",
                    priority = 10,
                    delaySeconds = policy.enterSeconds,
                    exitDelaySeconds = policy.exitSeconds,
                    condition = Condition(ConditionType.LANDSCAPE),
                )
        p.apps
            .filter { it.enabled && !it.blacklisted }
            .forEach { a ->
                builtins +=
                    Rule(
                        id = "app:${a.packageName}",
                        name = a.name,
                        priority = 50,
                        delaySeconds = a.enterSeconds,
                        exitDelaySeconds = a.exitSeconds,
                        condition =
                            Condition(
                                ConditionType.ALL,
                                children =
                                    buildList {
                                        add(Condition(ConditionType.APP_FOREGROUND, a.packageName))
                                        if (a.landscapeOnly) add(Condition(ConditionType.LANDSCAPE))
                                    },
                            ),
                    )
            }
        val all =
            (custom + builtins)
                .filter {
                    it.enabled && it.action in setOf(Action.ENABLE, Action.DISABLE, Action.RESTORE)
                }
                .sortedWith(compareByDescending<Rule> { it.priority }.thenBy { it.id })
        val matches =
            all.map {
                it to
                    if (Predicates.timeMatches(it.window, minute))
                        Predicates.evaluate(it.condition, e, minute)
                    else Truth.NO
            }
        val winner = matches.firstOrNull { it.second == Truth.YES }?.first
        val hold = winner?.allowScreenOff == true
        if (
            off &&
                policy.screenOffSeconds >= 0 &&
                screenDeadline != null &&
                now >= screenDeadline &&
                !hold
        ) {
            reset()
            return decision(Action.RESTORE, "息屏延迟已到期", urgent = true)
        }
        if (off && !hold)
            return decision(
                null,
                if (policy.screenOffSeconds < 0) "息屏保持原状态" else "息屏后退出",
                dueAt = screenDeadline.takeIf { policy.screenOffSeconds >= 0 },
            )
        if (p.apps.any { it.blacklisted } && (!e.foregroundKnown || !e.usageGranted)) {
            candidate = null
            return decision(null, "无法确认前台应用，暂停判断黑名单")
        }
        if (p.apps.any { it.blacklisted && it.packageName == e.foreground }) {
            reset()
            return decision(Action.RESTORE, "当前应用在黑名单", urgent = true)
        }
        val ambiguous =
            matches.any { (r, t) ->
                t == Truth.UNKNOWN && (winner == null || r.priority >= winner.priority)
            }
        if (ambiguous) {
            candidate = null
            candidateSince = now
            exitSince = null
            return decision(null, "前台应用或显示状态不确定，暂停切换")
        }
        if (cancel) {
            candidate = null
            exitSince = null
            return decision(null, "取消规则命中，待执行切换已取消")
        }
        if (winner == null) {
            candidate = null
            val old = active
            if (old != null) {
                if (exitSince == null) exitSince = now
                val due = exitSince!! + old.exitDelaySeconds * 1000L
                if (now < due) return decision(null, "等待退出：${old.name}", old.id, due)
            }
            active = null
            exitSince = null
            return decision(Action.RESTORE, "无规则命中，恢复接管前状态")
        }
        exitSince = null
        if (active?.id == winner.id) return decision(winner.action, winner.name, winner.id)
        if (active?.action == Action.ENABLE && winner.action == Action.ENABLE) {
            active = winner
            candidate = null
            return decision(Action.ENABLE, winner.name, winner.id)
        }
        if (candidate != winner.id) {
            candidate = winner.id
            candidateSince = now
        }
        val due = candidateSince + winner.delaySeconds * 1000L
        if (now < due) return decision(null, "等待触发：${winner.name}", winner.id, due)
        active = winner
        candidate = null
        return decision(winner.action, winner.name, winner.id)
    }
}
