package com.iqsoo.qodirect.monitoring

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.*
import android.provider.Settings
import android.view.Display
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.iqsoo.qodirect.automation.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DeviceMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onEvent: () -> Unit,
) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val memo = context.getSharedPreferences("runtime_clock", Context.MODE_PRIVATE)
    private val boot =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    private val mutable = MutableStateFlow(Environment(bootId = boot))
    val state = mutable.asStateFlow()
    private var started = false
    private var detailed = false
    private var displayRegistered = false
    private var usageJob: Job? = null
    private var cursor = 0L
    @Volatile private var resetUsage = true
    private val evidence = ForegroundEvidence()
    private val usageSignals = Channel<Unit>(Channel.CONFLATED)
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (!detailed && i?.action !in setOf(
                        Intent.ACTION_BATTERY_CHANGED, Intent.ACTION_POWER_CONNECTED,
                        Intent.ACTION_POWER_DISCONNECTED,
                    )) return
                if (i?.action == Intent.ACTION_BATTERY_CHANGED) battery(i)
                if (
                    i?.action in
                        setOf(Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED)
                )
                    context
                        .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                        ?.let(::battery)
                if (i?.action == Intent.ACTION_POWER_DISCONNECTED) {
                    invalidateUsage()
                    mutable.update { it.copy(plugged = 0) }
                    usageSignals.trySend(Unit)
                }
                if (detailed) {
                    screen()
                    rotation()
                }
                onEvent()
            }
        }
    private val displayListener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) {
                if (!detailed) return
                rotation()
                onEvent()
            }

            override fun onDisplayRemoved(id: Int) {
                if (!detailed) return
                rotation()
                onEvent()
            }

            override fun onDisplayChanged(id: Int) {
                if (!detailed) return
                if (id == Display.DEFAULT_DISPLAY) {
                    rotation()
                    screen()
                    onEvent()
                }
            }
        }

    fun start() {
        if (started) return
        started = true
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        invalidateUsage()
        sample()
    }

    fun configureDetails(enabled: Boolean) {
        detailed = enabled
        if (started && enabled && !displayRegistered) {
            displays.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
            displayRegistered = true
            sample()
        } else if (!enabled && displayRegistered) {
            displays.unregisterDisplayListener(displayListener)
            displayRegistered = false
            invalidateUsage()
        }
    }

    fun sample() {
        // The sticky official battery broadcast is always sampled before optional monitoring.
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::battery)
        if (!detailed) return
        val granted = usageGranted()
        if (!granted) invalidateUsage()
        mutable.update { it.copy(usageGranted = granted) }
        screen()
        rotation()
    }

    private fun invalidateUsage() {
        evidence.invalidate()
        resetUsage = true
        mutable.update { it.copy(foreground = null, foregroundKnown = false) }
    }

    private fun battery(i: Intent) {
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val changed = state.value.plugged != plugged
        if (changed) invalidateUsage()
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        mutable.update { current ->
            current.copy(
                plugged = plugged,
                battery = if (level >= 0 && scale > 0) level * 100 / scale else -1,
                temperature =
                    i.takeIf { it.hasExtra(BatteryManager.EXTRA_TEMPERATURE) }
                        ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                        ?.div(10f),
                batteryStatus =
                    i.getIntExtra(
                        BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN,
                    ),
            )
        }
        if (changed) usageSignals.trySend(Unit)
    }

    private fun screen() {
        val displayState = displays.getDisplay(Display.DEFAULT_DISPLAY)?.state
        val interactive =
            power.isInteractive &&
                displayState !in
                    setOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND)
        val locked = keyguard.isKeyguardLocked
        val changed = state.value.interactive != interactive || state.value.locked != locked
        if (changed) invalidateUsage()
        val old = state.value
        val offSince =
            if (interactive) null
            else
                old.screenOffSince
                    ?: memo.getLong("off", -1).takeIf {
                        it >= 0 &&
                            it <= SystemClock.elapsedRealtime() &&
                            memo.getInt("boot", -1) == boot
                    }
                    ?: SystemClock.elapsedRealtime()
        mutable.update {
            it.copy(interactive = interactive, locked = locked, screenOffSince = offSince)
        }
        if (memo.getInt("boot", -1) != boot || memo.getLong("off", -1) != (offSince ?: -1))
            memo.edit {
                putInt("boot", boot)
                putLong("off", offSince ?: -1)
            }
        if (!interactive || locked)
            mutable.update { it.copy(foreground = null, foregroundKnown = false) }
        if (changed) usageSignals.trySend(Unit)
    }

    @Suppress("DEPRECATION")
    private fun rotation() {
        val display = displays.getDisplay(Display.DEFAULT_DISPLAY)
        val size = Point()
        display?.getRealSize(size)
        mutable.update {
            it.copy(
                landscape = if (size.x > 0 && size.y > 0) size.x > size.y else null,
                rotation = display?.rotation,
            )
        }
    }

    fun usageGranted(): Boolean =
        context
            .getSystemService(AppOpsManager::class.java)
            .checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED

    fun configureUsage(needed: Boolean) {
        if (!needed) {
            usageJob?.cancel()
            usageJob = null
            invalidateUsage()
            mutable.update { it.copy(usageGranted = usageGranted()) }
            return
        }
        if (usageJob?.isActive == true) return
        invalidateUsage()
        usageJob =
            scope.launch {
                while (isActive) {
                    val current = state.value
                    if (current.wired && current.interactive && !current.locked)
                        withContext(Dispatchers.IO) { queryUsage() }
                    else invalidateUsage()
                    val wait =
                        if (state.value.wired && state.value.interactive && !state.value.locked)
                            1500L
                        else 10000L
                    // Physical power/screen events wake this wait immediately; there is no
                    // 10-second stale window on reconnect.
                    withTimeoutOrNull(wait) { usageSignals.receive() }
                }
            }
    }

    private fun queryUsage() {
        val granted = usageGranted()
        var token = -1L
        var result = ForegroundEvidence.Result()
        if (granted)
            try {
                val end = System.currentTimeMillis()
                val reseed = resetUsage || cursor > end || end - cursor > 180_000
                if (reseed) {
                    cursor = (end - 12 * 60 * 60 * 1000L).coerceAtLeast(0)
                    resetUsage = false
                }
                token = evidence.begin(reseed)
                val events =
                    context.getSystemService(UsageStatsManager::class.java).queryEvents(cursor, end)
                cursor = end
                if (events == null) {
                    evidence.invalidate()
                    resetUsage = true
                } else {
                    val event = UsageEvents.Event()
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        when (event.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED ->
                                evidence.accept(
                                    token,
                                    ForegroundEvidence.Event.RESUMED,
                                    event.packageName.orEmpty(),
                                    event.className.orEmpty(),
                                )
                            UsageEvents.Event.ACTIVITY_PAUSED,
                            UsageEvents.Event.ACTIVITY_STOPPED ->
                                evidence.accept(
                                    token,
                                    ForegroundEvidence.Event.PAUSED,
                                    event.packageName.orEmpty(),
                                    event.className.orEmpty(),
                                )
                            UsageEvents.Event.DEVICE_SHUTDOWN,
                            UsageEvents.Event.SCREEN_NON_INTERACTIVE ->
                                evidence.accept(token, ForegroundEvidence.Event.RESET)
                        }
                    }
                    result = evidence.result(token)
                }
            } catch (_: Exception) {
                evidence.invalidate()
                resetUsage = true
            }
        else {
            evidence.invalidate()
            resetUsage = true
        }
        val old = state.value
        mutable.update { current ->
            val valid =
                granted &&
                    evidence.isCurrent(token) &&
                    current.wired &&
                    current.interactive &&
                    !current.locked
            current.copy(
                usageGranted = granted,
                foreground = if (valid) result.packageName else null,
                foregroundKnown = valid && result.known,
            )
        }
        val latest = state.value
        if (
            old.foreground != latest.foreground ||
                old.foregroundKnown != latest.foregroundKnown ||
                old.usageGranted != latest.usageGranted
        )
            onEvent()
    }

    fun stop() {
        if (!started) return
        started = false
        usageJob?.cancel()
        usageJob = null
        invalidateUsage()
        runCatching { context.unregisterReceiver(receiver) }
        if (displayRegistered) displays.unregisterDisplayListener(displayListener)
        displayRegistered = false
        detailed = false
    }
}
