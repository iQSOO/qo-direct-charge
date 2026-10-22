@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.iqsoo.qodirect.ui

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iqsoo.qodirect.BuildConfig
import com.iqsoo.qodirect.automation.*
import com.iqsoo.qodirect.charging.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun QOApp(
    vm: MainViewModel,
    toggleAutomatic: (Boolean) -> Unit,
    exportRules: () -> Unit,
    importRules: () -> Unit,
    exportLogs: () -> Unit,
) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    val dark =
        when (p.policy.darkMode) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }
    val c = LocalContext.current
    val scheme =
        if (p.policy.dynamicColor && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c)
        } else if (dark)
            darkColorScheme(
                primary = Color(0xFF9DC6FF),
                secondary = Color(0xFF7EDBD4),
                background = Color(0xFF0D1421),
                surface = Color(0xFF131D2D),
            )
        else
            lightColorScheme(
                primary = Color(0xFF2563EB),
                secondary = Color(0xFF008577),
                background = Color(0xFFF5F8FF),
                surface = Color.White,
            )
    val view = LocalView.current
    SideEffect {
        (c as? android.app.Activity)?.let { activity ->
            androidx.core.view.WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme) {
        var page by rememberSaveable { mutableStateOf("home") }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
        val pages = listOf("home" to "首页", "rules" to "规则", "apps" to "应用", "tools" to "工具")
        BackHandler(enabled = page != "home") {
            page = if (page in pages.map { it.first }) "home" else "tools"
        }
        val titles =
            mapOf(
                "home" to "QO 智能直驱",
                "rules" to "智能规则",
                "apps" to "应用管理",
                "tools" to "工具与设置",
                "battery" to "电池保护",
                "diagnostics" to "诊断与验证",
                "logs" to "执行日志",
                "settings" to "运行设置",
            )
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(titles[page] ?: "QO", fontWeight = FontWeight.Bold)
                            Text(
                                "QO DIRECT CHARGE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    navigationIcon = {
                        if (page !in pages.map { it.first })
                            IconButton(onClick = { page = "tools" }) {
                                Icon(QOIcons.ArrowBack, "返回")
                            }
                    },
                    actions = {
                        IconButton(onClick = vm::refresh) { Icon(QOIcons.Refresh, "刷新真实状态") }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    pages.forEachIndexed { i, (id, label) ->
                        NavigationBarItem(
                            selected =
                                page == id ||
                                    id == "tools" &&
                                        page in setOf("battery", "diagnostics", "logs", "settings"),
                            onClick = { page = id },
                            icon = {
                                Icon(
                                    listOf(
                                        QOIcons.Dashboard,
                                        QOIcons.Tune,
                                        QOIcons.Apps,
                                        QOIcons.Widgets,
                                    )[i],
                                    label,
                                )
                            },
                            label = { Text(label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (page) {
                    "home" -> Home(vm, toggleAutomatic, { page = "diagnostics" })
                    "rules" -> RulesScreen(vm, exportRules, importRules)
                    "apps" -> AppsScreen(vm)
                    "battery" -> ProtectionScreen(vm)
                    "diagnostics" -> DiagnosticsScreen(vm)
                    "logs" -> LogsScreen(vm, exportLogs)
                    "settings" -> SettingsScreen(vm)
                    else -> Tools(vm) { page = it }
                }
            }
        }
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f)
            ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            content()
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            value,
            Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        Switch(checked, onChange)
    }
}

private fun nativeState(v: Boolean?) =
    when (v) {
        true -> "1 · 已开启"
        false -> "0 · 已关闭"
        null -> "未知"
    }

private fun supportState(v: Boolean?) =
    when (v) {
        true -> "1 · 支持"
        false -> "0 · 未声明支持"
        null -> "未知"
    }

private fun bypassState(v: Boolean?) =
    when (v) {
        true -> "1 · 厂商旁路标志已置位"
        false -> "0 · 厂商标志未置位"
        null -> "未知 / 无法读取"
    }

private fun phase(s: Phase) =
    when (s) {
        Phase.UNAVAILABLE -> "接口不可用"
        Phase.PERMISSION_REQUIRED -> "未授权"
        Phase.DISCONNECTED -> "Shizuku 未连接"
        Phase.IDLE -> "未开启 / 待命"
        Phase.ELIGIBLE -> "等待规则"
        Phase.ENABLE_PENDING -> "请求开启中"
        Phase.ENABLED -> "配置已开启"
        Phase.DISABLE_PENDING -> "请求关闭中"
        Phase.RESTORING -> "恢复原生状态中"
        Phase.MANUAL_OVERRIDE -> "手动控制优先"
        Phase.ERROR -> "控制失败 / 未确认"
    }

private fun power(e: Environment) =
    when {
        e.plugged < 0 -> "未知"
        !e.connected -> "外部电源已断开"
        e.plugged and 4 != 0 -> "无线供电（未验证）"
        e.plugged and 8 != 0 -> "底座供电（未验证）"
        e.plugged and 2 != 0 -> "USB 外部供电"
        e.plugged and 1 != 0 -> "有线电源 / AC"
        else -> "其他外部供电"
    }

@Composable
private fun Home(vm: MainViewModel, toggle: (Boolean) -> Unit, diagnostics: () -> Unit) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    val r by vm.runtime.collectAsStateWithLifecycle()
    val e by vm.environment.collectAsStateWithLifecycle()
    val n by vm.native.collectAsStateWithLifecycle()
    val s by vm.shizuku.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Box(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer,
                            )
                        ),
                        RoundedCornerShape(30.dp),
                    )
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            QOIcons.Bolt,
                            null,
                            Modifier.size(36.dp),
                            MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "让供电，顺应使用",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        when {
                            r.decision?.dueAt != null &&
                                r.decision?.action == null &&
                                r.phase == Phase.ENABLE_PENDING -> "等待开启"
                            r.decision?.dueAt != null &&
                                r.decision?.action == null &&
                                r.phase == Phase.DISABLE_PENDING -> "等待退出"
                            else -> phase(r.phase)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(r.reason, style = MaterialTheme.typography.bodyMedium)
                    SwitchRow(
                        "智能管理",
                        p.automatic && r.serviceRunning,
                        toggle,
                        if (p.automatic && !r.serviceRunning) "服务未运行，请重新启动" else "由统一规则引擎接管原厂直驱入口",
                    )
                }
            }
        }
        item {
            Panel(title = "原生状态反馈") {
                Detail("Settings 配置", nativeState(n.settings))
                Detail("vivo Bypass", bypassState(n.bypass))
                if (!e.wired) Text(
                    "未连接已验证的有线电源时暂停自动回读；以上为上次读取值，可在诊断中手动刷新。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "硬件状态未验证。配置和厂商标志只能作为系统反馈，不能证明电源芯片的实际工作模式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.command("enable") }) { Text("开启直驱") }
                    OutlinedButton(onClick = { vm.command("disable") }) { Text("关闭直驱") }
                    TextButton(onClick = { vm.command("restore") }) { Text("恢复原生状态") }
                }
            }
        }
        item {
            Panel(title = "正在感知") {
                Detail("外部供电", power(e))
                Detail(
                    "电量 / 温度",
                    "${e.battery.takeIf { it>=0 } ?: "—"}%  ·  ${e.temperature ?: "—"} °C",
                )
                Detail(
                    "屏幕",
                    when {
                        !e.interactive -> "息屏 / AOD"
                        e.locked -> "亮屏 / 已锁定"
                        else -> "亮屏 / 已解锁"
                    },
                )
                Detail(
                    "显示方向",
                    when (e.landscape) {
                        true -> "实际显示横屏"
                        false -> "实际显示竖屏"
                        null -> "未知"
                    },
                )
                Detail("前台应用", if (e.foregroundKnown) e.foreground ?: "未知" else "未知 / 未启用应用检测")
                Detail("当前规则", r.decision?.reason ?: "无")
                Detail("上次成功请求", r.lastSuccessAt?.let(::date) ?: "无")
                Detail(
                    "上次软件切换",
                    vm.graph.controller.lastSwitchAt.takeIf { it > 0 }?.let(::date) ?: "无",
                )
                Detail(
                    "下一次操作",
                    r.decision?.dueAt?.let {
                        "约 ${((it-SystemClock.elapsedRealtime()).coerceAtLeast(0)+999)/1000} 秒后重新评估"
                    } ?: "等待系统事件",
                )
            }
        }
        item {
            Panel(title = "管理状态") {
                Detail("Shizuku", s.message)
                if (p.automatic) {
                    SwitchRow("暂停自动管理", p.paused, { vm.command(if (it) "pause" else "resume") })
                    if (r.manualOverride)
                        Button(onClick = { vm.command("resume") }) { Text("结束手动优先，恢复自动管理") }
                }
                Button(onClick = vm::permission, modifier = Modifier.fillMaxWidth()) {
                    Text("申请 / 检查 Shizuku 授权")
                }
                TextButton(onClick = diagnostics) { Text("打开诊断与开启 / 关闭验证") }
            }
        }
        item {
            Text(
                "无需 Root · 保留原厂温控和电池保护",
                Modifier.padding(8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tools(vm: MainViewModel, navigate: (String) -> Unit) {
    val c = LocalContext.current
    val tools =
        listOf(
            Triple("battery", "电池保护", QOIcons.BatteryChargingFull),
            Triple("diagnostics", "诊断与验证", QOIcons.Science),
            Triple("logs", "执行日志", QOIcons.ReceiptLong),
            Triple("settings", "运行设置", QOIcons.Settings),
        )
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(tools) { (id, title, icon) ->
            Panel(modifier = Modifier.clickable { navigate(id) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(QOIcons.ChevronRight, null)
                }
            }
        }
        item {
            Panel(title = "关于 QO 智能直驱") {
                Text(
                    "为 iQOO Neo11 / OriginOS Android 16 开发。使用已经实测的 secure Settings 入口，通过授权后的 Shizuku shell UserService 控制。所有规则、日志与设置均在本机保存。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("版本 ${BuildConfig.VERSION_NAME} · 离线运行 · 不含广告", style = MaterialTheme.typography.labelMedium)
                Detail("开发者", "新好友")
                Text("更多实用教程与工具，欢迎访问 iQSOO。", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    open(c, Intent(Intent.ACTION_VIEW, "https://www.iqsoo.com".toUri()), vm)
                }) {
                    Text("访问官网：www.iqsoo.com")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(vm: MainViewModel) {
    val s by vm.shizuku.collectAsStateWithLifecycle()
    val n by vm.native.collectAsStateWithLifecycle()
    val e by vm.environment.collectAsStateWithLifecycle()
    val r by vm.runtime.collectAsStateWithLifecycle()
    val c = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Panel(title = "连接与权限") {
                Detail("Shizuku 服务", if (s.running) "运行中" else "未运行")
                Detail("Shizuku 授权", if (s.authorized) "已授予" else "未授权")
                Detail("UserService", if (s.connected) "已连接" else "未连接")
                Detail("Shell UID", n.capabilities.shellUid?.toString() ?: "等待验证")
                Detail("使用情况访问", if (e.usageGranted) "已授予" else "未授予 / 未启用")
                Button(onClick = vm::permission) { Text("申请 Shizuku 授权") }
                OutlinedButton(
                    onClick = {
                        open(
                            c,
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://shizuku.rikka.app/download/".toUri(),
                            ),
                            vm,
                        )
                    }
                ) {
                    Text("查看 Shizuku 官方下载与说明")
                }
                Text(
                    "免 Root 模式下，每次手机重启后通常需要重新通过无线调试启动 Shizuku。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Panel(title = "设备与真实反馈") {
                Detail("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
                Detail("Android", "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                Detail("目标 SDK", "36")
                Detail("Settings", nativeState(n.settings))
                Detail("Bypass Flag", bypassState(n.bypass))
                Detail("all_bypass 支持", supportState(n.capabilities.allSupport))
                Detail("game_bypass 支持", supportState(n.capabilities.gameSupport))
                Detail("最后读取", if (n.at == 0L) "尚未读取" else date(n.at))
                if (n.error != null) Text(n.error!!, color = MaterialTheme.colorScheme.error)
                Text(
                    "Settings 原文：${n.settingsRaw.ifEmpty { "（空）" }}\nBypass 原文：${n.bypassRaw.ifEmpty { "（空）" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Panel(title = "开启与关闭测试") {
                Text(
                    "连接有线电源后执行。每次测试都会校验命令退出码、Settings 回读，并等待厂商标志同步。关闭接口仍需在真实手机的已开启状态下验收。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.command("enable") }) { Text("开启测试") }
                    OutlinedButton(onClick = { vm.command("disable") }) { Text("关闭测试") }
                    TextButton(onClick = vm::refresh) { Text("刷新状态") }
                }
                r.lastResult?.let { result ->
                    Detail("验证结果", if (result.success) "配置反馈确认 / 无需写入" else "失败或未确认")
                    Detail("错误码", result.code)
                    Detail("退出码", result.exitCode?.toString() ?: "无命令执行")
                    Text(
                        result.message,
                        color =
                            if (result.success) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error,
                    )
                    Detail("Settings / Bypass", "${result.settings} / ${result.bypass}")
                }
                Text(
                    "硬件直驱未实测验证。不会修改 persist 属性、温控、电流或电压参数。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun open(c: android.content.Context, intent: Intent, vm: MainViewModel) {
    try {
        c.startActivity(intent)
    } catch (e: Exception) {
        vm.message("无法打开页面：${e.message}")
    }
}

private fun date(at: Long) =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date(at))

@Composable
private fun LogsScreen(vm: MainViewModel, export: () -> Unit) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    var clear by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = export) { Text("导出全部日志") }
                OutlinedButton(onClick = { clear = true }) { Text("清空日志") }
            }
            Text("页面显示最近 500 条，本机最多保留 2000 条。", style = MaterialTheme.typography.bodySmall)
        }
        if (logs.isEmpty()) item { Panel { Text("暂无执行日志。真实控制、权限变化和错误发生时会记录。") } }
        items(logs, key = { it.id }) { l ->
            Panel {
                Row {
                    Text(
                        date(l.at),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        l.action,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(l.trigger, fontWeight = FontWeight.SemiBold)
                Text(l.result)
                Text(
                    "${l.code} · exit=${l.exitCode ?: "—"} · Settings=${l.settings ?: "—"} · Bypass=${l.bypass ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (clear)
        AlertDialog(
            onDismissRequest = { clear = false },
            title = { Text("清空本机日志") },
            text = { Text("清空后无法恢复，已导出的文件不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearLogs()
                        clear = false
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = { TextButton(onClick = { clear = false }) { Text("取消") } },
        )
}

@Composable
private fun IntField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text,
        {
            text = it
            it.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
    )
}

@Composable
private fun FloatField(label: String, value: Float, onChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text,
        {
            text = it
            it.toFloatOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            ),
    )
}

@Composable
private fun ProtectionScreen(vm: MainViewModel) {
    val runtime by vm.runtime.collectAsStateWithLifecycle()
    val p by vm.preferences.collectAsStateWithLifecycle()
    val e by vm.environment.collectAsStateWithLifecycle()
    val n by vm.native.collectAsStateWithLifecycle()
    var edit by remember(p.policy) { mutableStateOf(p.policy) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Panel(title = "当前电池") {
                Detail("电量", "${e.battery}%")
                Detail("温度", "${e.temperature ?: "未知"} °C")
                Detail("外部供电", power(e))
                Detail(
                    "系统电池状态",
                    when (e.batteryStatus) {
                        2 -> "系统报告正在充电"
                        3 -> "系统报告放电"
                        4 -> "系统报告未充电"
                        5 -> "系统报告已充满"
                        else -> "未知"
                    },
                )
                Detail("原生直驱配置", nativeState(n.settings))
                Detail(
                    "软件保护",
                    when {
                        !e.wired -> "非已验证有线供电，禁止自动开启"
                        e.temperature == null || e.battery < 0 -> "数据未知，暂停请求"
                        runtime.decision?.reason?.startsWith("温度保护") == true ->
                            "高温暂停，等待降至 ${p.policy.resumeTemperature} °C"
                        e.temperature!! >= p.policy.maxTemperature -> "达到温度暂停阈值"
                        e.battery !in p.policy.minBattery..p.policy.maxBattery -> "电量不在允许范围"
                        else -> "当前读数在允许范围内"
                    },
                )
                Text(
                    "是否插电依据 EXTRA_PLUGGED，独立于系统报告的充电状态。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Panel(title = "额外软件保护") {
                IntField("允许开启的最低电量（%）", edit.minBattery) { edit = edit.copy(minBattery = it) }
                IntField("允许开启的最高电量（%）", edit.maxBattery) { edit = edit.copy(maxBattery = it) }
                FloatField("暂停温度（30–50 °C）", edit.maxTemperature) {
                    edit = edit.copy(maxTemperature = it)
                }
                FloatField("恢复温度（°C，低于暂停温度）", edit.resumeTemperature) {
                    edit = edit.copy(resumeTemperature = it)
                }
                Button(onClick = { vm.updatePolicy(edit) }) { Text("保存保护策略") }
                Text(
                    "自动管理期间达到暂停温度时，软件会退出自己发起的直驱请求；达到恢复温度后重新评估。原厂 BMS、温控及充电保护始终保留。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    val p by vm.preferences.collectAsStateWithLifecycle()
    var edit by remember(p.policy) { mutableStateOf(p.policy) }
    val c = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Panel(title = "横屏与防抖") {
                SwitchRow(
                    "启用横屏触发",
                    edit.landscapeEnabled,
                    { edit = edit.copy(landscapeEnabled = it) },
                )
                SwitchRow(
                    "横屏仅限所选应用",
                    edit.landscapeOnlySelected,
                    { edit = edit.copy(landscapeOnlySelected = it) },
                    "关闭后，任意应用的实际显示横屏均可触发",
                )
                IntField("横屏进入延迟（秒）", edit.enterSeconds) { edit = edit.copy(enterSeconds = it) }
                IntField("竖屏退出延迟（秒）", edit.exitSeconds) { edit = edit.copy(exitSeconds = it) }
                IntField("普通切换冷却（秒）", edit.cooldownSeconds) {
                    edit = edit.copy(cooldownSeconds = it)
                }
                IntField("状态确认超时（秒，2–15）", edit.confirmSeconds) {
                    edit = edit.copy(confirmSeconds = it)
                }
                Text(
                    "横屏开启未确认时，自动按 5、10、20 秒退避，之后每隔 30 秒重试。" +
                        "每次先检查当前规则、供电和保护条件；确认成功后停止。" +
                        "竖屏、息屏、手动优先、暂停或断线会取消重试。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Panel(title = "息屏管理") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 15, 30, 60, 300, -1).forEach { sec ->
                        FilterChip(
                            edit.screenOffSeconds == sec,
                            { edit = edit.copy(screenOffSeconds = sec) },
                            label = { Text(if (sec < 0) "保持原状态" else "${sec}秒") },
                        )
                    }
                }
                IntField("自定义延迟（秒，-1 表示保持）", edit.screenOffSeconds) {
                    edit = edit.copy(screenOffSeconds = it)
                }
                Text(
                    "AOD 按息屏处理。亮屏取消退出计时；亮屏仍锁定时暂停新请求，解锁后重新评估。Android Doze 与厂商后台限制可能延后执行，应用不承诺精确定时。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Panel(title = "后台与外观") {
                SwitchRow(
                    "开机尝试恢复管理",
                    edit.restoreOnBoot,
                    { edit = edit.copy(restoreOnBoot = it) },
                    "仅恢复已启用的管理服务；先读取真实状态，Shizuku 仍需重新启动",
                )
                SwitchRow("动态颜色", edit.dynamicColor, { edit = edit.copy(dynamicColor = it) })
                Choice("外观", edit.darkMode, listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色")) {
                    edit = edit.copy(darkMode = it)
                }
                Button(onClick = { vm.updatePolicy(edit) }, modifier = Modifier.fillMaxWidth()) {
                    Text("保存全部运行设置")
                }
                OutlinedButton(
                    onClick = {
                        open(
                            c,
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                "package:${c.packageName}".toUri(),
                            ),
                            vm,
                        )
                    }
                ) {
                    Text("打开系统应用设置")
                }
                Text(
                    "如 OriginOS 限制后台，请在系统应用设置中允许本应用持续运行。无 Root 不保证永久常驻；强行停止后需手动打开。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun <T> Choice(
    label: String,
    value: T,
    options: List<Pair<T, String>>,
    onChange: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                "$label：${options.firstOrNull { it.first==value }?.second ?: value}",
                Modifier.weight(1f),
            )
            Icon(QOIcons.ExpandMore, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (v, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onChange(v)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AppsScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val p by vm.preferences.collectAsStateWithLifecycle()
    val loading by vm.loadingApps.collectAsStateWithLifecycle()
    val e by vm.environment.collectAsStateWithLifecycle()
    val c = LocalContext.current
    var search by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<AppRule?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var packageInput by remember { mutableStateOf("") }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Panel(title = "应用识别") {
                Text(
                    "仅在配置应用规则后读取使用情况事件。多窗口或事件不足时显示未知并暂停判断。可选择所有具有启动入口的可见应用，也可手动填写其他适用应用的包名。",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!e.usageGranted)
                    Button(
                        onClick = { open(c, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), vm) }
                    ) {
                        Text("授予使用情况访问")
                    }
                OutlinedTextField(
                    search,
                    { search = it },
                    label = { Text("搜索应用名称或包名") },
                    leadingIcon = { Icon(QOIcons.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::loadApps) { Text(if (loading) "读取中…" else "刷新应用") }
                    TextButton(onClick = { selected = emptySet() }) { Text("清除选择") }
                }
                if (selected.isNotEmpty())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                vm.batchApps(selected, true)
                                selected = emptySet()
                            }
                        ) {
                            Text("批量：横屏开启")
                        }
                        OutlinedButton(
                            onClick = {
                                vm.batchApps(selected, false)
                                selected = emptySet()
                            }
                        ) {
                            Text("批量：前台开启")
                        }
                    }
                OutlinedTextField(
                    packageInput,
                    { packageInput = it },
                    label = { Text("其他应用的准确包名（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        if (packageInput.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))
                            editing = AppRule(packageInput, packageInput)
                        else vm.message("请输入准确的应用包名")
                    }
                ) {
                    Text("按包名添加规则")
                }
            }
        }
        val configured = p.apps.filter { a -> apps.none { it.packageName == a.packageName } }
        items(configured, key = { "custom:${it.packageName}" }) { a ->
            Panel {
                Text(a.name, fontWeight = FontWeight.Bold)
                Text(a.packageName, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { editing = a }) { Text("编辑已配置应用") }
            }
        }
        items(
            apps.filter {
                it.label.contains(search, true) || it.packageName.contains(search, true)
            },
            key = { it.packageName },
        ) { a ->
            val configuredRule = p.apps.firstOrNull { it.packageName == a.packageName }
            Panel {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(a.icon.asImageBitmap(), null, Modifier.size(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.label, fontWeight = FontWeight.SemiBold)
                        Text(a.packageName, style = MaterialTheme.typography.bodySmall)
                        if (configuredRule != null)
                            Text(
                                if (configuredRule.blacklisted) "黑名单"
                                else if (configuredRule.enabled)
                                    "已设置：${if(configuredRule.landscapeOnly) "前台且横屏" else "前台"}"
                                else "规则已停用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                    }
                    Checkbox(
                        a.packageName in selected,
                        { checked ->
                            selected =
                                if (checked) selected + a.packageName else selected - a.packageName
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { editing = configuredRule ?: AppRule(a.packageName, a.label) }
                    ) {
                        Text("独立规则")
                    }
                    if (configuredRule != null)
                        TextButton(onClick = { vm.removeApp(a.packageName) }) { Text("移除") }
                }
            }
        }
    }
    editing?.let { initial ->
        var a by remember(initial.packageName) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(a.name) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(a.packageName, style = MaterialTheme.typography.bodySmall)
                    SwitchRow("启用应用规则", a.enabled, { a = a.copy(enabled = it) })
                    SwitchRow(
                        "加入黑名单",
                        a.blacklisted,
                        { a = a.copy(blacklisted = it) },
                        "黑名单优先于自动开启规则",
                    )
                    SwitchRow("仅在前台且横屏时开启", a.landscapeOnly, { a = a.copy(landscapeOnly = it) })
                    Text("所有开启请求均要求有线外部供电，并通过保护检查。", style = MaterialTheme.typography.bodySmall)
                    IntField("进入延迟（秒）", a.enterSeconds) { a = a.copy(enterSeconds = it) }
                    IntField("退出延迟（秒）", a.exitSeconds) { a = a.copy(exitSeconds = it) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (a.enterSeconds in 0..3600 && a.exitSeconds in 0..3600) {
                            vm.saveApp(a)
                            editing = null
                        } else vm.message("延迟应在 0–3600 秒")
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
    }
}

private val actionNames =
    mapOf(
        Action.ENABLE to "请求开启直驱",
        Action.DISABLE to "请求关闭直驱",
        Action.RESTORE to "恢复接管前状态",
        Action.CANCEL to "取消待执行任务",
        Action.PAUSE to "暂停自动规则",
        Action.RESUME to "恢复自动规则",
        Action.LOG to "记录日志",
        Action.NOTIFY to "记录并通知",
    )
private val conditionNames =
    mapOf(
        ConditionType.ALL to "AND · 全部满足",
        ConditionType.ANY to "OR · 任意满足",
        ConditionType.NOT to "NOT · 条件取反",
        ConditionType.POWER_CONNECTED to "外部电源连接",
        ConditionType.POWER_DISCONNECTED to "外部电源断开",
        ConditionType.LANDSCAPE to "实际显示横屏",
        ConditionType.PORTRAIT to "实际显示竖屏",
        ConditionType.APP_FOREGROUND to "指定应用前台",
        ConditionType.APP_EXITED to "指定应用退出",
        ConditionType.SCREEN_ON to "亮屏",
        ConditionType.SCREEN_OFF to "息屏",
        ConditionType.BATTERY_RANGE to "电量范围",
        ConditionType.TEMPERATURE_RANGE to "温度范围",
        ConditionType.TIME_RANGE to "当前时间段",
        ConditionType.MANUAL_STATE to "用户手动控制状态",
    )

@Composable
private fun RulesScreen(vm: MainViewModel, export: () -> Unit, importAction: () -> Unit) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val p by vm.preferences.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Rule?>(null) }
    var deleting by remember { mutableStateOf<Rule?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Panel(title = "统一规则引擎") {
                Text(
                    "保护与用户手动暂停优先，随后按优先级选择规则。相同优先级按规则 ID 稳定排序。无规则命中时恢复接管前状态；未知证据会暂停切换。所有动作由同一控制队列执行。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Detail(
                    "内置横屏规则",
                    if (p.policy.landscapeEnabled && !p.policy.landscapeOnlySelected) "启用 · 优先级 10"
                    else "关闭 / 仅所选应用",
                )
                Detail("应用规则", "${p.apps.count { it.enabled && !it.blacklisted }} 条 · 优先级 50")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { editing = Rule() }) {
                        Icon(QOIcons.Add, null)
                        Text("创建规则")
                    }
                    TextButton(onClick = export) { Text("导出") }
                    TextButton(onClick = importAction) { Text("导入") }
                }
                Text("导入将替换规则和策略，请先停止自动管理；导出文件可用于备份。", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (rules.isEmpty()) item { Panel { Text("自定义规则还没有创建。内置横屏与应用规则仍可独立工作。") } }
        items(rules.sortedByDescending { it.priority }, key = { it.id }) { r ->
            Panel {
                SwitchRow(r.name, r.enabled, { vm.saveRule(r.copy(enabled = it)) })
                Text(
                    "${actionNames[r.action]} · 优先级 ${r.priority} · 延迟 ${r.delaySeconds} 秒",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(describe(r.condition), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { editing = r }) { Text("编辑") }
                    TextButton(
                        onClick = {
                            vm.saveRule(
                                r.copy(id = UUID.randomUUID().toString(), name = r.name + " 副本")
                            )
                        }
                    ) {
                        Text("复制")
                    }
                    TextButton(onClick = { deleting = r }) { Text("删除") }
                }
            }
        }
    }
    editing?.let { r ->
        RuleEditor(r, { editing = null }) { edited ->
            try {
                Validation.rule(edited)
                vm.saveRule(edited)
                editing = null
            } catch (e: Exception) {
                vm.message(e.message ?: "规则无效")
            }
        }
    }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除规则") },
            text = { Text(r.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeRule(r.id)
                        deleting = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

private fun describe(c: Condition): String =
    when (c.type) {
        ConditionType.ALL,
        ConditionType.ANY,
        ConditionType.NOT ->
            "${conditionNames[c.type]}（${c.children.joinToString("；",transform=::describe)}）"
        else -> "${conditionNames[c.type]}${if(c.value.isBlank()) "" else "：${c.value}"}"
    }

@Composable
private fun RuleEditor(initial: Rule, dismiss: () -> Unit, save: (Rule) -> Unit) {
    var r by remember(initial.id) { mutableStateOf(initial) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = dismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.fillMaxWidth(.96f).fillMaxHeight(.94f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "编辑智能规则",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        r.name,
                        { r = r.copy(name = it) },
                        label = { Text("规则名称") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SwitchRow("启用规则", r.enabled, { r = r.copy(enabled = it) })
                    IntField("优先级（-1000 至 1000）", r.priority) { r = r.copy(priority = it) }
                    Choice("动作", r.action, Action.entries.map { it to actionNames.getValue(it) }) {
                        r = r.copy(action = it)
                    }
                    IntField("条件持续 / 执行延迟（秒）", r.delaySeconds) { r = r.copy(delaySeconds = it) }
                    IntField("不再满足时退出延迟（秒）", r.exitDelaySeconds) {
                        r = r.copy(exitDelaySeconds = it)
                    }
                    OutlinedTextField(
                        r.window,
                        { r = r.copy(window = it) },
                        label = { Text("生效时段 HH:mm-HH:mm") },
                        supportingText = { Text("00:00-00:00 表示全天，支持跨午夜") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SwitchRow(
                        "允许覆盖息屏退出策略",
                        r.allowScreenOff,
                        { r = r.copy(allowScreenOff = it) },
                        "仅在本条规则仍明确命中时保持，保护条件仍优先",
                    )
                    HorizontalDivider()
                    Text("触发条件", style = MaterialTheme.typography.titleMedium)
                    ConditionEditor(r.condition, { r = r.copy(condition = it) }, 0)
                    Text(
                        "延迟会在条件不再明确满足时取消。开启动作始终受有线电源、电量和温度保护约束。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("取消") }
                    Button(onClick = { save(r) }) { Text("保存规则") }
                }
            }
        }
    }
}

@Composable
private fun ConditionEditor(c: Condition, change: (Condition) -> Unit, depth: Int) {
    Column(
        Modifier.fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .38f),
                RoundedCornerShape(16.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Choice(
            "条件",
            c.type,
            ConditionType.entries
                .filter {
                    depth < 7 ||
                        it !in setOf(ConditionType.ALL, ConditionType.ANY, ConditionType.NOT)
                }
                .map { it to conditionNames.getValue(it) },
        ) { type ->
            change(
                Condition(
                    type,
                    when (type) {
                        ConditionType.BATTERY_RANGE -> "20,100"
                        ConditionType.TEMPERATURE_RANGE -> "0,42"
                        ConditionType.TIME_RANGE -> "00:00-00:00"
                        ConditionType.MANUAL_STATE -> "true"
                        else -> ""
                    },
                    if (type in setOf(ConditionType.ALL, ConditionType.ANY, ConditionType.NOT))
                        listOf(Condition())
                    else emptyList(),
                )
            )
        }
        when (c.type) {
            ConditionType.ALL,
            ConditionType.ANY,
            ConditionType.NOT -> {
                c.children.forEachIndexed { i, child ->
                    Column {
                        ConditionEditor(
                            child,
                            { updated ->
                                change(
                                    c.copy(
                                        children =
                                            c.children.toMutableList().also { it[i] = updated }
                                    )
                                )
                            },
                            depth + 1,
                        )
                        if (c.type != ConditionType.NOT && c.children.size > 1)
                            TextButton(
                                onClick = {
                                    change(
                                        c.copy(
                                            children =
                                                c.children.filterIndexed { index, _ -> i != index }
                                        )
                                    )
                                }
                            ) {
                                Text("移除此子条件")
                            }
                    }
                }
                if (c.type != ConditionType.NOT && c.children.size < 24)
                    TextButton(onClick = { change(c.copy(children = c.children + Condition())) }) {
                        Icon(QOIcons.Add, null)
                        Text("添加子条件")
                    }
            }
            ConditionType.APP_FOREGROUND,
            ConditionType.APP_EXITED ->
                OutlinedTextField(
                    c.value,
                    { change(c.copy(value = it)) },
                    label = { Text("准确应用包名") },
                    supportingText = { Text("可从应用管理页面查看包名；需要使用情况访问权限") },
                    modifier = Modifier.fillMaxWidth(),
                )
            ConditionType.BATTERY_RANGE,
            ConditionType.TEMPERATURE_RANGE ->
                OutlinedTextField(
                    c.value,
                    { change(c.copy(value = it)) },
                    label = {
                        Text(
                            if (c.type == ConditionType.BATTERY_RANGE) "电量下限,上限（%）"
                            else "温度下限,上限（°C）"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            ConditionType.TIME_RANGE ->
                OutlinedTextField(
                    c.value,
                    { change(c.copy(value = it)) },
                    label = { Text("HH:mm-HH:mm") },
                    modifier = Modifier.fillMaxWidth(),
                )
            ConditionType.MANUAL_STATE ->
                Choice("手动控制", c.value, listOf("true" to "正在手动控制", "false" to "未处于手动控制")) {
                    change(c.copy(value = it))
                }
            else -> Unit
        }
    }
}
