package com.iqsoo.qodirect.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.iqsoo.qodirect.charging.*
import com.iqsoo.qodirect.foreground.DeadlineReceiver
import com.iqsoo.qodirect.monitoring.DeviceMonitor
import com.iqsoo.qodirect.repository.ChargeRepository
import com.iqsoo.qodirect.shizuku.ShizukuGateway
import java.time.LocalTime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class RuntimeState(
    val phase: Phase = Phase.DISCONNECTED,
    val serviceRunning: Boolean = false,
    val reason: String = "请启动 Shizuku 并授权",
    val decision: Decision? = null,
    val manualOverride: Boolean = false,
    val lastResult: ControlResult? = null,
    val lastRequestAt: Long? = null,
    val lastSuccessAt: Long? = null,
)

class AutomationRuntime(
    private val context: Context,
    private val scope: CoroutineScope,
    val repository: ChargeRepository,
    val gateway: ShizukuGateway,
    val controller: VivoDirectChargeController,
) {
    val monitor = DeviceMonitor(context, scope) { request() }
    private val mutable = MutableStateFlow(RuntimeState())
    val state = mutable.asStateFlow()
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val engine = RuleEngine()
    private val enableRetry = AutomaticEnableRetry()
    private val powerGate = PowerConnectionGate()
    private var powerExitError: String? = null
    private val tickQueued = java.util.concurrent.atomic.AtomicBoolean(false)
    private val pendingCommands = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var timer: Job? = null
    private var heartbeat: Job? = null
    private var lastRead = 0L
    private var lastObserved: Boolean? = null
    private var failureKey: String? = null
    private var stopping = false
    private var uiVisible = false
    private val alarm = context.getSystemService(AlarmManager::class.java)
    private val alarmIntent =
        PendingIntent.getBroadcast(
            context,
            710,
            Intent(context, DeadlineReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    init {
        scope.launch {
            for (command in queue) {
                if (command == "tick") tickQueued.set(false)
                try {
                    when (command) {
                        "tick" -> evaluate()
                        "sync" -> {
                            monitor.sample()
                            evaluate()
                        }
                        "refresh" -> {
                            failureKey = null
                            cancelPending()
                            gateway.retry()
                            monitor.sample()
                            refreshNative(true)
                            evaluate()
                        }
                        "enable" -> manual(true)
                        "disable" -> manual(false)
                        "restore" -> {
                            cancelPending()
                            mutable.value = state.value.copy(manualOverride = true)
                            restore("用户恢复原生状态", true)
                            engine.reset()
                        }
                        "resume" -> {
                            cancelPending()
                            if (monitor.state.value.wired) refreshNative(true)
                            controller.acknowledgeManual()
                            failureKey = null
                            engine.reset()
                            mutable.value = state.value.copy(manualOverride = false)
                            repository.update { it.copy(paused = false) }
                            evaluate()
                        }
                        "service-restore" -> restore("服务结束，恢复接管前状态", false)
                        "pause" -> {
                            cancelPending()
                            repository.update { it.copy(paused = true) }
                            restore("用户暂停自动管理", false)
                            evaluate()
                        }
                        "stop" -> {
                            cancelPending()
                            stopping = true
                            val restored = restore("停止自动管理", false)
                            repository.update { it.copy(automatic = false, paused = false) }
                            mutable.value =
                                state.value.copy(
                                    serviceRunning = false,
                                    phase = if (restored.success) Phase.IDLE else Phase.ERROR,
                                    reason =
                                        if (restored.success) "自动管理已停止"
                                        else "管理已停止，恢复未确认：${restored.message}",
                                )
                            context.stopService(
                                Intent(
                                    context,
                                    com.iqsoo.qodirect.foreground.AutomationService::class.java,
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    cancelPending()
                    mutable.value =
                        state.value.copy(
                            phase = Phase.ERROR,
                            reason = e.message ?: e.javaClass.simpleName,
                        )
                    runCatching { repository.log(command, "ERROR", e.message ?: "运行错误", "RUNTIME") }
                } finally {
                    pendingCommands.remove(command)
                }
            }
        }
        scope.launch {
            repository.preferences.collect {
                enableRetry.reset()
                configureMonitoring()
                request()
            }
        }
        scope.launch {
            repository.rules.collect {
                configureMonitoring()
                failureKey = null
                enableRetry.reset()
                engine.reconfigure()
                request()
            }
        }
        scope.launch {
            monitor.state.map { it.wired }.distinctUntilChanged().collect {
                configureMonitoring()
                request()
            }
        }
        scope.launch {
            var wasConnected = false
            var lastConnectionMessage = ""
            gateway.state.collect { s ->
                if (lastConnectionMessage != s.message) {
                    lastConnectionMessage = s.message
                    repository.log("Shizuku 连接", "CONNECTION", s.message)
                }
                if (wasConnected && !s.connected) {
                    cancelPending()
                    controller.invalidate()
                    lastObserved = null
                    engine.reset()
                    mutable.value =
                        state.value.copy(
                            phase =
                                if (s.running) Phase.PERMISSION_REQUIRED else Phase.DISCONNECTED,
                            reason = s.message,
                        )
                }
                if (!wasConnected && s.connected) {
                    enableRetry.reset()
                    lastRead = 0
                    lastObserved = null
                    engine.reset()
                    failureKey = null
                }
                wasConnected = s.connected
                request()
            }
        }
    }

    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        configureMonitoring()
        if (visible) request()
    }

    private fun configureMonitoring() {
        if (uiVisible || state.value.serviceRunning) {
            monitor.start()
            monitor.configureDetails(uiVisible || monitor.state.value.wired)
            monitor.configureUsage(
                state.value.serviceRunning && repository.preferences.value.automatic &&
                    !repository.preferences.value.paused && monitor.state.value.wired &&
                    (repository.preferences.value.apps.any { it.enabled || it.blacklisted } ||
                        repository.rules.value.any { it.enabled && Predicates.usesApp(it.condition) })
            )
        } else monitor.stop()
        configureHeartbeat()
    }

    private fun configureHeartbeat() {
        if (!state.value.serviceRunning || !repository.preferences.value.automatic || !monitor.state.value.wired) {
            heartbeat?.cancel()
            heartbeat = null
        } else if (heartbeat?.isActive != true) {
            heartbeat = scope.launch {
                while (isActive) {
                    request()
                    delay(10000)
                }
            }
        }
    }

    fun request() {
        if (tickQueued.compareAndSet(false, true)) queue.trySend("tick")
    }

    fun command(name: String) {
        if (pendingCommands.add(name)) queue.trySend(name)
    }

    fun serviceStarted() {
        enableRetry.reset()
        stopping = false
        mutable.value = state.value.copy(serviceRunning = true)
        configureMonitoring()
        engine.reset()
        lastRead = 0
        request()
    }

    fun serviceDestroyed() {
        heartbeat?.cancel()
        cancelPending()
        mutable.value =
            state.value.copy(
                serviceRunning = false,
                reason = if (stopping) state.value.reason else "服务已停止，正在尝试恢复接管前状态",
            )
        configureMonitoring()
        if (!stopping && controller.hasLease) scope.launch { command("service-restore") }
    }

    private suspend fun refreshNative(force: Boolean = false) {
        if (!gateway.state.value.connected) return
        val elapsed = SystemClock.elapsedRealtime()
        if (!force && elapsed - lastRead < 15000 && lastRead != 0L) return
        val s = controller.refresh()
        lastRead = elapsed
        if (s.error == null && s.settings != null) {
            if (
                controller.manualDetected && !state.value.manualOverride ||
                    (lastObserved != null && lastObserved != s.settings && !controller.hasLease)
            ) {
                mutable.value =
                    state.value.copy(
                        manualOverride = true,
                        phase = Phase.MANUAL_OVERRIDE,
                        reason = "检测到用户或系统更改，已退出自动接管",
                    )
                repository.log(
                    "原生状态变化",
                    "MANUAL_OVERRIDE",
                    "保留当前原生开关，等待用户恢复自动管理",
                    settings = s.settings,
                    bypass = s.bypass,
                )
            }
            lastObserved = s.settings
        }
    }

    private fun plan(d: Decision) {
        timer?.cancel()
        alarm.cancel(alarmIntent)
        val due = d.dueAt ?: return
        val wait = (due - SystemClock.elapsedRealtime()).coerceAtLeast(50)
        timer =
            scope.launch {
                delay(wait)
                request()
            }
        // Inexact wakeup: Android may defer this in Doze. No exact-alarm permission or permanent
        // wake lock.
        alarm.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            due.coerceAtLeast(SystemClock.elapsedRealtime() + 50),
            alarmIntent,
        )
    }

    private fun cancelPending() {
        enableRetry.reset()
        timer?.cancel()
        alarm.cancel(alarmIntent)
        mutable.value = state.value.copy(decision = null)
    }

    private fun waitForRetry(d: Decision) {
        val pending = enableRetry.pending ?: return
        val waiting = d.copy(
            action = null,
            reason = "开启未确认（${pending.errorCode}），等待第 ${pending.attempt} 次自动重试",
            dueAt = listOfNotNull(d.dueAt, pending.dueAt).minOrNull(),
        )
        mutable.value = state.value.copy(
            phase = Phase.ENABLE_PENDING, decision = waiting, reason = waiting.reason,
        )
        plan(waiting)
    }

    private suspend fun evaluate() {
        if (!repository.ready.value) return
        val power = monitor.state.value
        val powerResult = powerGate.evaluate(power, controller.hasLease)
        if (powerResult != PowerConnectionGate.Result.READY) {
            cancelPending()
            engine.reconfigure()
            failureKey = null
            lastRead = 0
            if (powerResult == PowerConnectionGate.Result.RESTORE_ONCE) {
                gateway.refresh()
                val result = if (gateway.state.value.connected) {
                    controller.restore(allowEnable = false).also {
                        record("外部供电已断开或未经验证，退出软件接管", Action.RESTORE, it)
                    }
                } else {
                    ControlResult(false, "DISCONNECTED", "供电退出时 Shizuku 不可用，无法确认恢复")
                        .also { record("供电退出", Action.RESTORE, it) }
                }
                powerExitError = result.takeIf { !it.success }?.message
            }
            val waiting = when {
                power.plugged < 0 -> "供电信息尚未确认，自动触发已停止"
                power.connected -> "无线或底座供电未经验证，自动触发已停止"
                else -> "未连接充电器，等待有线供电；自动触发已停止"
            }
            mutable.value = state.value.copy(
                phase = if (powerExitError == null) Phase.IDLE else Phase.ERROR,
                reason = powerExitError?.let { "$waiting；退出未确认：$it" } ?: waiting,
                decision = null,
            )
            return
        }
        powerExitError = null
        gateway.refresh()
        val g = gateway.state.value
        if (!g.connected) {
            cancelPending()
            mutable.value =
                state.value.copy(
                    phase =
                        when {
                            !g.running -> Phase.DISCONNECTED
                            !g.authorized -> Phase.PERMISSION_REQUIRED
                            else -> Phase.UNAVAILABLE
                        },
                    reason = g.message,
                    decision = null,
                )
            return
        }
        // Retry deadlines force a fresh read, even inside the normal 15-second read interval.
        val retryDue = enableRetry.pending?.dueAt?.let { SystemClock.elapsedRealtime() >= it } == true
        refreshNative(retryDue)
        if (!monitor.state.value.wired) {
            cancelPending()
            request()
            return
        }
        val n = controller.snapshot.value
        val p = repository.preferences.value
        if (!state.value.serviceRunning || !p.automatic) {
            cancelPending()
            mutable.value =
                state.value.copy(
                    phase =
                        if (state.value.lastResult?.success == false) Phase.ERROR
                        else if (state.value.manualOverride) Phase.MANUAL_OVERRIDE
                        else if (n.settings == true) Phase.ENABLED else Phase.IDLE,
                    reason =
                        state.value.lastResult?.takeIf { !it.success }?.message
                            ?: if (state.value.manualOverride) "手动控制优先" else "仅手动控制 / 未启动自动管理",
                )
            return
        }
        val e = monitor.state.value.copy(manual = state.value.manualOverride)
        val time = LocalTime.now()
        val now = SystemClock.elapsedRealtime()
        val rules = repository.rules.value
        val d = engine.evaluate(e, p, rules, now, time.hour * 60 + time.minute)
        mutable.value = state.value.copy(decision = d, reason = d.reason)
        for (effect in d.effects) {
            if (effect.action == Action.NOTIFY)
                com.iqsoo.qodirect.foreground.Notifications.event(context, effect.name)
            repository.log(effect.name, effect.action.name, "规则动作已执行")
        }
        val retryEligible = AutomaticEnableRetry.eligible(e, p, d, state.value.serviceRunning)
        val key = AutomaticEnableRetry.key(d)
        enableRetry.retain(key.takeIf { retryEligible })
        if (n.error != null) {
            if (retryEligible) {
                if (enableRetry.pending == null || retryDue) {
                    val result = ControlResult(false, "READ_FAILED", "读取原生反馈失败，未写入开启命令：${n.error}")
                    record(d.reason, Action.ENABLE, result)
                    enableRetry.failed(key, result, SystemClock.elapsedRealtime())
                }
                waitForRetry(d)
            } else {
                plan(d.copy(action = null))
                mutable.value = state.value.copy(phase = Phase.ERROR, reason = n.error)
            }
            return
        }
        if (!n.capabilities.supported) {
            cancelPending()
            mutable.value = state.value.copy(
                phase = Phase.UNAVAILABLE, reason = "未确认兼容的原厂直驱接口，请查看诊断",
            )
            return
        }
        plan(d)
        if (d.action == null) {
            mutable.value =
                state.value.copy(
                    phase =
                        when {
                            state.value.manualOverride -> Phase.MANUAL_OVERRIDE
                            d.dueAt != null && (d.ruleId != null || d.reason == "息屏后退出") ->
                                if (controller.hasLease || d.reason == "息屏后退出")
                                    Phase.DISABLE_PENDING
                                else Phase.ENABLE_PENDING
                            n.settings == true -> Phase.ENABLED
                            else -> Phase.ELIGIBLE
                        }
                )
            return
        }
        if (
            d.action == Action.RESTORE && !controller.hasLease ||
                d.action == Action.DISABLE && d.urgent && !controller.hasLease
        ) {
            mutable.value =
                state.value.copy(phase = if (n.settings == true) Phase.ENABLED else Phase.IDLE)
            return
        }
        if (
            (d.action == Action.ENABLE && n.settings == true && n.bypass == true) ||
                (d.action == Action.DISABLE && n.settings == false && n.bypass == false)
        ) {
            // A late Bypass acknowledgement completes recovery without another write.
            if (enableRetry.pending != null) {
                enableRetry.reset()
                failureKey = null
                record(d.reason, d.action, ControlResult(
                    true, "READBACK_CONFIRMED", "延迟回读确认 Settings 和 Bypass 均为开启；硬件状态未验证",
                    settings = n.settings, bypass = n.bypass,
                ))
            }
            mutable.value =
                state.value.copy(phase = if (n.settings == true) Phase.ENABLED else Phase.IDLE)
            return
        }
        if (enableRetry.pending?.let { now < it.dueAt } == true) {
            waitForRetry(d)
            return
        }
        if (failureKey == key) {
            mutable.value =
                state.value.copy(phase = Phase.ERROR, reason = "上次操作未确认，已暂停重复写入；刷新或手动重试")
            return
        }
        if (
            !d.urgent &&
                controller.hasLease &&
                SystemClock.elapsedRealtime() - controller.lastSwitchElapsed <
                    p.policy.cooldownSeconds * 1000L
        ) {
            val due =
                now +
                    (p.policy.cooldownSeconds * 1000L -
                            (SystemClock.elapsedRealtime() - controller.lastSwitchElapsed))
                        .coerceAtLeast(50)
            val waiting = d.copy(action = null, dueAt = due)
            mutable.value = state.value.copy(decision = waiting)
            plan(waiting)
            return
        }
        val retryAttempt = enableRetry.pending?.attempt
        if (retryAttempt != null) {
            repository.log(d.reason, "ENABLE_RETRY", "第 $retryAttempt 次自动重试，已重新检查规则和保护条件")
        }
        monitor.sample()
        if (
            monitor.state.value.copy(manual = state.value.manualOverride) != e ||
                repository.preferences.value != p || repository.rules.value != rules ||
                !gateway.state.value.connected || !state.value.serviceRunning ||
                pendingCommands.any { it in setOf("pause", "stop", "enable", "disable", "restore") }
        ) {
            request()
            return
        }
        val r =
            when (d.action) {
                Action.ENABLE -> {
                    mutable.value = state.value.copy(phase = Phase.ENABLE_PENDING)
                    controller.enable()
                }
                Action.DISABLE -> {
                    mutable.value = state.value.copy(phase = Phase.DISABLE_PENDING)
                    controller.disable()
                }
                Action.RESTORE -> {
                    mutable.value = state.value.copy(phase = Phase.RESTORING)
                    controller.restore(
                        allowEnable =
                            e.wired &&
                                e.battery in p.policy.minBattery..p.policy.maxBattery &&
                                (e.temperature ?: 100f) < p.policy.maxTemperature
                    )
                }
                else -> return
            }
        record(d.reason, d.action, r)
        val next = if (retryEligible) enableRetry.failed(key, r, SystemClock.elapsedRealtime()) else null
        failureKey = if (!r.success && next == null) key else null
        if (next != null) {
            waitForRetry(d)
            // Events received during shell confirmation must cancel or supersede this retry now.
            request()
        }
    }

    private suspend fun manual(target: Boolean) {
        cancelPending()
        monitor.sample()
        gateway.refresh()
        val e = monitor.state.value
        val p = repository.preferences.value.policy
        if (
            target &&
                (!e.wired ||
                    e.temperature == null ||
                    e.temperature >= p.maxTemperature ||
                    e.battery !in p.minBattery..p.maxBattery)
        ) {
            record(
                "用户开启测试",
                Action.ENABLE,
                ControlResult(false, "PROTECTION", "需连接有线电源，且电量、温度在保护范围内"),
            )
            return
        }
        controller.refresh()
        controller.acknowledgeManual()
        mutable.value =
            state.value.copy(
                manualOverride = true,
                decision = null,
                reason = if (target) "正在请求开启并回读原生反馈" else "正在请求关闭并回读原生反馈",
                phase = if (target) Phase.ENABLE_PENDING else Phase.DISABLE_PENDING,
            )
        engine.reset()
        timer?.cancel()
        alarm.cancel(alarmIntent)
        failureKey = null
        val r = if (target) controller.enable() else controller.disable()
        record(
            "用户${if(target) "开启" else "关闭"} / 诊断测试",
            if (target) Action.ENABLE else Action.DISABLE,
            r,
        )
    }

    private suspend fun restore(reason: String, releaseManual: Boolean): ControlResult {
        mutable.value = state.value.copy(phase = Phase.RESTORING)
        val e = monitor.state.value
        val p = repository.preferences.value.policy
        val r =
            controller.restore(
                allowEnable =
                    e.wired &&
                        e.temperature != null &&
                        e.temperature < p.maxTemperature &&
                        e.battery in p.minBattery..p.maxBattery
            )
        record(reason, Action.RESTORE, r)
        if (r.success && !e.wired) powerExitError = null
        if (releaseManual) controller.acknowledgeManual()
        return r
    }

    private suspend fun record(reason: String, action: Action, r: ControlResult) {
        lastObserved = controller.snapshot.value.settings
        mutable.value =
            state.value.copy(
                lastResult = r,
                lastRequestAt = System.currentTimeMillis(),
                lastSuccessAt =
                    if (r.success && r.code in setOf("FEEDBACK_CONFIRMED", "READBACK_CONFIRMED")) System.currentTimeMillis()
                    else state.value.lastSuccessAt,
                phase =
                    if (!r.success) Phase.ERROR
                    else if (state.value.manualOverride) Phase.MANUAL_OVERRIDE
                    else if (r.settings == true || controller.snapshot.value.settings == true)
                        Phase.ENABLED
                    else Phase.IDLE,
                reason = r.message,
            )
        if (controller.manualDetected) mutable.value = state.value.copy(manualOverride = true)
        // Matching repeated enable decisions are coalesced without log flooding or additional
        // writes.
        if (r.code != "ALREADY_SET" && r.code != "NO_LEASE")
            repository.log(reason, action.name, r.message, r.code, r.exitCode, r.settings, r.bypass)
    }
}
