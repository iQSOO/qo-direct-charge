package com.iqsoo.qodirect

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.iqsoo.qodirect.automation.AutomationRuntime
import com.iqsoo.qodirect.charging.VivoDirectChargeController
import com.iqsoo.qodirect.database.ChargeDatabase
import com.iqsoo.qodirect.repository.ChargeRepository
import com.iqsoo.qodirect.shizuku.ShizukuGateway
import kotlinx.coroutines.*

class QOApplication : Application() {
    val graph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        graph
        com.iqsoo.qodirect.foreground.LogMaintenance.schedule(this)
    }
}

/** Explicit constructor injection keeps Android dependencies out of the pure rule engine. */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database =
        Room.databaseBuilder(context, ChargeDatabase::class.java, "qo.db").build()
    val repository = ChargeRepository(context, database.dao(), scope)
    val gateway = ShizukuGateway(context, scope)
    val controller =
        VivoDirectChargeController(
            gateway,
            { repository.preferences.value.policy.confirmSeconds },
            elapsed = android.os.SystemClock::elapsedRealtime,
        )
    val runtime = AutomationRuntime(context, scope, repository, gateway, controller)
}
