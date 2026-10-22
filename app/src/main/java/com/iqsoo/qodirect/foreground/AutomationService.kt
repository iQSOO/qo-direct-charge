package com.iqsoo.qodirect.foreground

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.iqsoo.qodirect.QOApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class AutomationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph
        get() = (application as QOApplication).graph

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            Notifications.ID,
            Notifications.ongoing(this, false, "正在同步真实系统状态"),
            if (android.os.Build.VERSION.SDK_INT >= 34)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE,
        )
        graph.runtime.serviceStarted()
        scope.launch {
            combine(graph.runtime.state, graph.repository.preferences) { s, p ->
                    p.paused to s.reason
                }
                .collect { (paused, reason) ->
                    getSystemService(android.app.NotificationManager::class.java)
                        .notify(
                            Notifications.ID,
                            Notifications.ongoing(this@AutomationService, paused, reason),
                        )
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "pause",
            "resume",
            "stop" -> graph.runtime.command(intent.action!!)
            else -> graph.runtime.request()
        }
        scope.launch {
            if (!graph.repository.preferences.firstReady(graph.repository)) {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        graph.runtime.serviceDestroyed()
        scope.cancel()
        super.onDestroy()
    }
}

private suspend fun kotlinx.coroutines.flow.StateFlow<com.iqsoo.qodirect.automation.Preferences>
    .firstReady(repository: com.iqsoo.qodirect.repository.ChargeRepository): Boolean {
    repository.ready.first { it }
    return value.automatic
}
