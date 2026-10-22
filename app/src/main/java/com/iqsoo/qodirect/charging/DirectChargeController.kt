package com.iqsoo.qodirect.charging

import com.iqsoo.qodirect.shizuku.ChargeUserService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface DirectChargeController {
    suspend fun getCapabilities(): ChargeCapabilities

    suspend fun getSettingsState(): Boolean?

    suspend fun getBypassFlag(): Boolean?

    suspend fun enable(): ControlResult

    suspend fun disable(): ControlResult

    suspend fun restorePreviousState(): ControlResult
}

data class ChargeCapabilities(
    val shellUid: Int? = null,
    val allSupport: Boolean? = null,
    val gameSupport: Boolean? = null,
    val settingsPresent: Boolean = false,
) {
    val supported: Boolean
        get() = shellUid == 2000 && settingsPresent && (allSupport == true || gameSupport == true)
}

data class NativeSnapshot(
    val settings: Boolean? = null,
    val bypass: Boolean? = null,
    val capabilities: ChargeCapabilities = ChargeCapabilities(),
    val at: Long = 0,
    val error: String? = null,
    val settingsRaw: String = "",
    val bypassRaw: String = "",
)

data class ControlResult(
    val success: Boolean,
    val code: String,
    val message: String,
    val exitCode: Int? = null,
    val settings: Boolean? = null,
    val bypass: Boolean? = null,
)

/**
 * Owns a reversible lease only for this process. A restart always reads reality and never replays a
 * saved value.
 */
class VivoDirectChargeController(
    private val gateway: ShellTransport,
    private val confirmationSeconds: () -> Int,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = { System.nanoTime() / 1_000_000L },
) : DirectChargeController {
    private val lock = Mutex()
    private val mutable = MutableStateFlow(NativeSnapshot())
    val snapshot = mutable.asStateFlow()
    private var previous: Boolean? = null
    private var lastWritten: Boolean? = null
    private var writeUncertain = false
    var hasLease = false
        private set

    var manualDetected = false
        private set

    var lastSwitchElapsed = 0L
        private set

    var lastSwitchAt = 0L
        private set

    private fun parse(v: String?): Boolean? =
        when (v?.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }

    private suspend fun readUnlocked(): NativeSnapshot {
        val b = gateway.execute(ChargeUserService.SNAPSHOT)
        val s = if (b.getInt("settingsExit", -1) == 0) parse(b.getString("settings")) else null
        val bypass = if (b.getInt("bypassExit", -1) == 0) parse(b.getString("bypass")) else null
        val snap =
            NativeSnapshot(
                s,
                bypass,
                ChargeCapabilities(
                    b.getInt("uid", -1).takeIf { it >= 0 },
                    if (b.getInt("allSupportExit", -1) == 0) parse(b.getString("allSupport"))
                    else null,
                    if (b.getInt("gameSupportExit", -1) == 0) parse(b.getString("gameSupport"))
                    else null,
                    s != null,
                ),
                now(),
                if (b.getInt("exit", -1) == 0) null
                else b.getString("error") ?: "状态查询失败：${b.getString("settings")}",
                b.getString("settings").orEmpty(),
                b.getString("bypass").orEmpty(),
            )
        mutable.value = snap
        return snap
    }

    suspend fun refresh(): NativeSnapshot =
        lock.withLock {
            try {
                val snap = readUnlocked()
                if (writeUncertain) reconcileFailedWrite(snap)
                else if (
                    hasLease &&
                        snap.settings != null &&
                        lastWritten != null &&
                        snap.settings != lastWritten
                ) {
                    manualDetected = true
                    release()
                }
                snap
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value =
                    NativeSnapshot(at = now(), error = "${e.javaClass.simpleName}: ${e.message}")
                mutable.value
            }
        }

    override suspend fun getCapabilities() = refresh().capabilities

    override suspend fun getSettingsState() = refresh().settings

    override suspend fun getBypassFlag() = refresh().bypass

    fun acknowledgeManual() {
        manualDetected = false
    }

    fun release() {
        hasLease = false
        previous = null
        lastWritten = null
        writeUncertain = false
    }

    fun invalidate() {
        release()
        mutable.value = NativeSnapshot(error = "连接已变化，等待重新读取真实状态")
    }

    // A failed/in-flight command is not a confirmed native manual change. Keep the actual
    // post-command value for conflict detection; never rebase a later, confirmed external change.
    private fun reconcileFailedWrite(s: NativeSnapshot?) {
        if (!hasLease || s?.error != null || s?.settings == null) return
        lastWritten = s.settings
        writeUncertain = false
        if (s.settings == previous && s.bypass == previous) release()
    }

    private suspend fun writeUnlocked(target: Boolean, capture: Boolean): ControlResult {
        val initial = readUnlocked()
        if (writeUncertain) reconcileFailedWrite(initial)
        if (initial.error != null) return ControlResult(false, "READ_FAILED", initial.error)
        if (initial.capabilities.shellUid != 2000)
            return ControlResult(false, "UID_INVALID", "UserService 没有 shell UID")
        if (target && !initial.capabilities.supported)
            return ControlResult(false, "UNSUPPORTED", "未确认原厂直驱支持，拒绝写入")
        if (initial.settings == null)
            return ControlResult(false, "UNKNOWN_SETTINGS", "原生 Settings 状态未知，拒绝接管")
        if (hasLease && lastWritten != null && initial.settings != lastWritten) {
            manualDetected = true
            release()
            return ControlResult(
                false,
                "MANUAL_CHANGED",
                "检测到原生开关被用户或系统更改，已退出接管",
                settings = initial.settings,
                bypass = initial.bypass,
            )
        }
        // A matching state without a lease belongs to the user; do not claim it as our own.
        if (initial.settings == target && initial.bypass == target)
            return ControlResult(
                true,
                "ALREADY_SET",
                "配置及厂商标志已为目标值；未执行写入，硬件状态未验证",
                null,
                initial.settings,
                initial.bypass,
            )
        if (capture && !hasLease) {
            previous = initial.settings
            hasLease = true
        }
        writeUncertain = true
        val b = gateway.execute(if (target) ChargeUserService.ENABLE else ChargeUserService.DISABLE)
        val exit = b.getInt("exit", -1)
        if (exit != 0) {
            val observed =
                try {
                    readUnlocked()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    mutable.value = NativeSnapshot(at = now(), error = "命令失败且回读失败：${e.message}")
                    null
                }
            reconcileFailedWrite(observed)
            return ControlResult(
                false,
                "COMMAND_FAILED",
                "命令失败（exit=$exit）：${b.getString("error")?.takeIf { it.isNotBlank() } ?: b.getString("output")?.takeIf { it.isNotBlank() } ?: "系统未返回错误文本"}",
                exit,
                observed?.settings,
                observed?.bypass,
            )
        }
        // Track even an unconfirmed request; later rollback still needs a read-before-write check.
        lastWritten = target
        lastSwitchAt = now()
        lastSwitchElapsed = elapsed()
        val deadline = elapsed() + confirmationSeconds().coerceIn(2, 15) * 1000L
        var s: NativeSnapshot
        do {
            s = readUnlocked()
            if (s.settings == target && s.bypass == target) {
                writeUncertain = false
                return ControlResult(
                    true,
                    "FEEDBACK_CONFIRMED",
                    "Settings 已${if(target) "开启" else "关闭"}，厂商 Bypass 标志已同步；硬件状态未验证",
                    exit,
                    s.settings,
                    s.bypass,
                )
            }
            delay(350)
        } while (elapsed() < deadline)
        reconcileFailedWrite(s)
        return ControlResult(
            false,
            if (s.settings != target) "SETTINGS_MISMATCH" else "BYPASS_TIMEOUT",
            if (s.settings != target) "写入后 Settings 未保持目标值，可能被系统拒绝或回退"
            else "Settings 已写入，厂商 Bypass 标志在超时内未同步；未确认生效",
            exit,
            s.settings,
            s.bypass,
        )
    }

    private suspend fun write(target: Boolean): ControlResult =
        lock.withLock {
            try {
                writeUnlocked(target, true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = NativeSnapshot(at = now(), error = "控制未确认：${e.message}")
                ControlResult(
                    false,
                    if (e is ShellTransportTimeoutException) "TRANSPORT_TIMEOUT"
                    else "TRANSPORT_ERROR",
                    "${e.javaClass.simpleName}: ${e.message}",
                )
            }
        }

    override suspend fun enable() = write(true)

    override suspend fun disable() = write(false)

    override suspend fun restorePreviousState(): ControlResult = restore(allowEnable = true)

    suspend fun restore(allowEnable: Boolean): ControlResult =
        lock.withLock {
            if (!hasLease)
                return@withLock ControlResult(
                    true,
                    "NO_LEASE",
                    "没有软件接管状态，无需写入",
                    settings = mutable.value.settings,
                    bypass = mutable.value.bypass,
                )
            val target = previous ?: return@withLock ControlResult(false, "NO_BASELINE", "缺少接管前状态")
            if (target && !allowEnable) {
                release()
                return@withLock ControlResult(
                    true,
                    "RESTORE_ENABLE_SKIPPED",
                    "当前条件不允许恢复开启，已退出接管，不写入开启值",
                )
            }
            try {
                val s = readUnlocked()
                if (writeUncertain) reconcileFailedWrite(s)
                if (!hasLease)
                    return@withLock ControlResult(
                        true, "NO_LEASE", "未确认的请求已回到原状态，无需恢复写入",
                        settings = s.settings, bypass = s.bypass,
                    )
                if (s.settings != lastWritten) {
                    release()
                    manualDetected = true
                    return@withLock ControlResult(
                        false,
                        "RESTORE_CONFLICT",
                        "原生状态已变化，保留用户或系统的最新设置",
                        settings = s.settings,
                        bypass = s.bypass,
                    )
                }
                val r = writeUnlocked(target, false)
                if (r.success) release()
                r
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = NativeSnapshot(at = now(), error = "控制未确认：${e.message}")
                ControlResult(false, "RESTORE_FAILED", e.message ?: "恢复失败")
            }
        }
}
