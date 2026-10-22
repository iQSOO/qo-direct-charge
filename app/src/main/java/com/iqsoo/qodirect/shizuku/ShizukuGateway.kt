package com.iqsoo.qodirect.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import com.iqsoo.qodirect.charging.ShellReply
import com.iqsoo.qodirect.charging.ShellTransport
import com.iqsoo.qodirect.charging.ShellTransportTimeoutException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class ShizukuGateway(context: Context, private val scope: CoroutineScope) : ShellTransport {
    data class State(
        val running: Boolean = false,
        val authorized: Boolean = false,
        val connected: Boolean = false,
        val message: String = "Shizuku 未运行",
    )

    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    @Volatile private var remote: IChargeService? = null
    private var binding = false
    private var nextBindAt = 0L
    private var bindError: String? = null
    private val args =
        Shizuku.UserServiceArgs(ComponentName(context, ChargeUserService::class.java))
            .daemon(false)
            .processNameSuffix("qo_shell")
            .debuggable(false)
            .version(1)
            .tag("qo-direct-shell-v1")
    private val death =
        IBinder.DeathRecipient {
            scope.launch(Dispatchers.Main.immediate) {
                remote = null
                binding = false
                refresh()
            }
        }
    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binding = false
                if (service?.pingBinder() == true) {
                    nextBindAt = 0L
                    bindError = null
                    remote = IChargeService.Stub.asInterface(service)
                    runCatching { service.linkToDeath(death, 0) }
                    refresh()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                remote = null
                binding = false
                refresh()
            }
        }
    private val received =
        Shizuku.OnBinderReceivedListener { scope.launch(Dispatchers.Main.immediate) { retry() } }
    private val dead =
        Shizuku.OnBinderDeadListener {
            remote = null
            binding = false
            mutable.value = State(message = "Shizuku 已断线，控制已暂停")
        }
    private val permission =
        Shizuku.OnRequestPermissionResultListener { _, _ ->
            scope.launch(Dispatchers.Main.immediate) { retry() }
        }

    init {
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
    }

    fun refresh() {
        if (remote?.asBinder()?.pingBinder() == false) {
            remote = null
            binding = false
            nextBindAt = SystemClock.elapsedRealtime() + 1000
            bindError = "UserService 已断开，等待重新连接"
        }
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted =
            running &&
                runCatching {
                        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED &&
                            !Shizuku.isPreV11()
                    }
                    .getOrDefault(false)
        if (!granted) {
            if (remote != null) runCatching { Shizuku.unbindUserService(args, connection, true) }
            remote = null
            binding = false
        }
        mutable.value =
            State(
                running,
                granted,
                remote?.asBinder()?.pingBinder() == true,
                when {
                    !running -> "Shizuku 未运行"
                    !granted -> "等待 Shizuku 授权"
                    remote == null -> bindError ?: "正在连接 shell UserService"
                    else -> "Shizuku 已授权 / UserService 已连接"
                },
            )
        if (granted && remote == null && !binding && SystemClock.elapsedRealtime() >= nextBindAt) {
            binding = true
            try {
                Shizuku.bindUserService(args, connection)
            } catch (e: Exception) {
                binding = false
                nextBindAt = SystemClock.elapsedRealtime() + 10000
                bindError = "绑定失败：${e.message}；10 秒后可重试"
                mutable.value = mutable.value.copy(message = bindError!!)
            }
            scope.launch {
                delay(6000)
                if (remote == null && binding) {
                    binding = false
                    nextBindAt = SystemClock.elapsedRealtime() + 10000
                    bindError = "UserService 连接超时，可刷新重试"
                    mutable.value = mutable.value.copy(message = bindError!!)
                }
            }
        }
    }

    fun retry() {
        nextBindAt = 0L
        bindError = null
        refresh()
    }

    fun requestPermission(): String? {
        retry()
        if (!state.value.running) return "请先安装并通过无线调试启动 Shizuku"
        return try {
            if (!state.value.authorized) Shizuku.requestPermission(410)
            null
        } catch (e: Exception) {
            "授权失败：${e.message}"
        }
    }

    override suspend fun execute(op: Int): ShellReply =
        withContext(Dispatchers.IO) {
            if (
                !runCatching {
                        Shizuku.pingBinder() &&
                            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    }
                    .getOrDefault(false)
            )
                throw SecurityException("Shizuku 已断线或授权失效")
            val service = remote ?: throw IllegalStateException("UserService 尚未连接")
            // Binder calls cannot be forcibly interrupted. The remote fixed commands each have a
            // hard process timeout.
            try {
                withTimeout(8500) {
                    val b = runInterruptible { service.execute(op) }
                    ShellReply(
                        listOf(
                                "uid",
                                "exit",
                                "settingsExit",
                                "bypassExit",
                                "allSupportExit",
                                "gameSupportExit",
                            )
                            .filter { b.containsKey(it) }
                            .associateWith { b.getInt(it) },
                        listOf("error", "output", "settings", "bypass", "allSupport", "gameSupport")
                            .mapNotNull { k -> b.getString(k)?.let { k to it } }
                            .toMap(),
                    )
                }
            } catch (e: TimeoutCancellationException) {
                throw ShellTransportTimeoutException("UserService 调用超过 8.5 秒，控制未确认；可刷新后重试")
            }
        }

    fun close() {
        runCatching { remote?.asBinder()?.unlinkToDeath(death, 0) }
        runCatching { Shizuku.unbindUserService(args, connection, true) }
        remote = null
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeBinderDeadListener(dead)
        Shizuku.removeRequestPermissionResultListener(permission)
    }
}
