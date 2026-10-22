package com.iqsoo.qodirect.foreground

import android.content.*
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.work.*
import com.iqsoo.qodirect.QOApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED))
            return
        val pending = goAsync()
        val graph = (c.applicationContext as QOApplication).graph
        graph.scope.launch {
            try {
                withTimeout(7000) {
                    graph.repository.ready.first { it }
                    val p = graph.repository.preferences.value
                    if (p.automatic && p.policy.restoreOnBoot) {
                        try {
                            ContextCompat.startForegroundService(
                                c,
                                Intent(c, AutomationService::class.java),
                            )
                            Notifications.event(c, "管理已恢复。重启后请重新启动 Shizuku；控制前会读取当前真实状态")
                        } catch (e: Exception) {
                            Notifications.event(c, "系统未允许后台启动，请打开 QO 智能直驱恢复管理")
                            graph.repository.log("开机恢复", "START_DENIED", e.message ?: "系统限制")
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class DeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val graph = (c.applicationContext as QOApplication).graph
        if (!graph.runtime.state.value.serviceRunning) return
        // Short and bounded wake lock only for an inexact system alarm; never a continuous CPU
        // wakeup.
        val lock =
            c.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QODirect:deadline")
        lock.acquire(10000)
        val pending = goAsync()
        graph.scope.launch {
            try {
                graph.runtime.monitor.sample()
                graph.runtime.request()
                delay(8000)
            } finally {
                if (lock.isHeld) lock.release()
                pending.finish()
            }
        }
    }
}

class LogMaintenance(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        (applicationContext as QOApplication).graph.repository.dao.prune()
        return Result.success()
    }

    companion object {
        fun schedule(c: Context) {
            WorkManager.getInstance(c)
                .enqueueUniquePeriodicWork(
                    "qo-local-log-maintenance",
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<LogMaintenance>(1, TimeUnit.DAYS).build(),
                )
        }
    }
}
