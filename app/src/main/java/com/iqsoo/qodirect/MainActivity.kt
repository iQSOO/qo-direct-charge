package com.iqsoo.qodirect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.iqsoo.qodirect.foreground.AutomationService
import com.iqsoo.qodirect.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startAutomatic() else vm.message("通知权限未授予。请在设置中开启后启动管理")
        }
    private val exportRules =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
            it?.let { uri -> vm.export(uri, false) }
        }
    private val exportLogs =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
            it?.let { uri -> vm.export(uri, true) }
        }
    private val importRules =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(vm::importRules)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QOApp(
                vm,
                ::toggleAutomatic,
                { exportRules.launch("QO-rules.json") },
                { importRules.launch(arrayOf("application/json", "text/plain")) },
                { exportLogs.launch("QO-logs.jsonl") },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        vm.graph.runtime.setUiVisible(true)
    }

    override fun onStop() {
        vm.graph.runtime.setUiVisible(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        vm.resync()
    }

    private fun toggleAutomatic(enabled: Boolean) {
        if (!enabled) {
            vm.command("stop")
            return
        }
        if (!vm.shizuku.value.connected) {
            vm.permission()
            vm.message("Shizuku 连接成功后再启动智能管理")
            return
        }
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startAutomatic()
    }

    private fun startAutomatic() {
        lifecycleScope.launch {
            try {
                vm.graph.repository.update { it.copy(automatic = true, paused = false) }
                ContextCompat.startForegroundService(
                    this@MainActivity,
                    Intent(this@MainActivity, AutomationService::class.java),
                )
                vm.command("resume")
            } catch (e: Exception) {
                vm.graph.repository.update { it.copy(automatic = false) }
                vm.message("启动失败：${e.message}")
            }
        }
    }
}
