package com.iqsoo.qodirect.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iqsoo.qodirect.QOApplication
import com.iqsoo.qodirect.automation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class InstalledApp(val label: String, val packageName: String, val icon: Bitmap)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val graph = (app as QOApplication).graph
    val preferences = graph.repository.preferences
    val rules = graph.repository.rules
    val logs = graph.repository.logs
    val runtime = graph.runtime.state
    val environment = graph.runtime.monitor.state
    val native = graph.controller.snapshot
    val shizuku = graph.gateway.state
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val loadingApps = MutableStateFlow(false)

    init {
        resync()
        loadApps()
    }

    fun message(s: String) {
        messages.tryEmit(s)
    }

    fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message(e.message ?: "操作失败")
            }
        }
    }

    fun refresh() = graph.runtime.command("refresh")

    fun resync() = graph.runtime.command("sync")

    fun command(s: String) = graph.runtime.command(s)

    fun permission() {
        graph.gateway.requestPermission()?.let(::message)
    }

    fun updatePolicy(p: Policy) = work {
        Validation.policy(p)
        graph.repository.update { it.copy(policy = p) }
        message("策略已保存")
    }

    fun saveRule(r: Rule) = work {
        graph.repository.saveRule(r)
        message("规则已保存")
    }

    fun removeRule(id: String) = work { graph.repository.deleteRule(id) }

    fun saveApp(a: AppRule) = work { graph.repository.putApp(a) }

    fun removeApp(pkg: String) = work {
        graph.repository.update { it.copy(apps = it.apps.filterNot { r -> r.packageName == pkg }) }
    }

    fun batchApps(packages: Set<String>, landscape: Boolean) = work {
        graph.repository.update { p ->
            p.copy(
                apps =
                    p.apps.filterNot { it.packageName in packages } +
                        apps.value
                            .filter { it.packageName in packages }
                            .map { AppRule(it.packageName, it.label, landscapeOnly = landscape) }
            )
        }
        message("已批量设置 ${packages.size} 个应用")
    }

    fun loadApps() {
        if (loadingApps.value) return
        work {
            loadingApps.value = true
            try {
                apps.value =
                    withContext(Dispatchers.IO) {
                        val pm = getApplication<Application>().packageManager
                        pm.queryIntentActivities(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                                0,
                            )
                            .map { it.activityInfo.applicationInfo }
                            .distinctBy { it.packageName }
                            .map {
                                InstalledApp(
                                    it.loadLabel(pm).toString(),
                                    it.packageName,
                                    it.loadIcon(pm).toBitmap(96, 96),
                                )
                            }
                            .sortedBy { it.label.lowercase() }
                    }
            } finally {
                loadingApps.value = false
            }
        }
    }

    fun export(uri: Uri, logs: Boolean) = work {
        val text = if (logs) graph.repository.exportLogs() else graph.repository.exportRules()
        withContext(Dispatchers.IO) {
            getApplication<Application>()
                .contentResolver
                .openOutputStream(uri, "wt")
                ?.bufferedWriter()
                ?.use { it.write(text) } ?: error("无法写入目标文件")
        }
        message("导出完成")
    }

    fun importRules(uri: Uri) = work {
        val text =
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        out.write(buffer, 0, count)
                        require(out.size() <= 512_000) { "文件超过 500 KB" }
                    }
                    val bytes = out.toByteArray()
                    require(bytes.size <= 512_000) { "文件超过 500 KB" }
                    bytes.toString(Charsets.UTF_8)
                } ?: error("无法读取文件")
            }
        graph.repository.importRules(text)
        message("规则和策略已导入，自动管理保持关闭")
    }

    fun clearLogs() = work {
        graph.repository.dao.clearLogs()
        message("日志已清空")
    }
}
