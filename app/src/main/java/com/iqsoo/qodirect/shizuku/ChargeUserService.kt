package com.iqsoo.qodirect.shizuku

import android.os.Bundle
import android.os.Process
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Runs only inside Shizuku's shell-UID process, never in the ordinary application. */
@Keep
class ChargeUserService : IChargeService.Stub() {
    companion object {
        const val SNAPSHOT = 0
        const val ENABLE = 1
        const val DISABLE = 2
    }

    @Volatile private var activeChild: java.lang.Process? = null

    private data class Output(val code: Int, val text: String)

    private fun run(vararg args: String): Output {
        var child: java.lang.Process? = null
        return try {
            child = ProcessBuilder(*args).redirectErrorStream(true).start()
            activeChild = child
            if (!child.waitFor(1800, TimeUnit.MILLISECONDS)) {
                child.destroyForcibly()
                Output(-110, "命令执行超时")
            } else
                Output(
                    child.exitValue(),
                    child.inputStream.bufferedReader().use { it.readText().take(4096).trim() },
                )
        } catch (e: Exception) {
            Output(-1, "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            child?.inputStream?.close()
            child?.errorStream?.close()
            child?.outputStream?.close()
            child?.destroy()
            activeChild = null
        }
    }

    @Synchronized
    override fun execute(operation: Int): Bundle {
        val b = Bundle()
        b.putInt("uid", Process.myUid())
        if (Process.myUid() != 2000) {
            b.putInt("exit", -13)
            b.putString("error", "UserService UID 必须为 shell(2000)，请使用无线调试模式启动 Shizuku")
            return b
        }
        fun capture(key: String, vararg args: String) {
            val o = run(*args)
            b.putInt("${key}Exit", o.code)
            b.putString(key, o.text)
        }
        when (operation) {
            SNAPSHOT -> {
                capture(
                    "settings",
                    "/system/bin/settings",
                    "get",
                    "secure",
                    "settings_battery_charge_director",
                )
                capture("bypass", "/system/bin/getprop", "persist.vivo.game_bypass_charge_flag")
                capture(
                    "allSupport",
                    "/system/bin/getprop",
                    "persist.vivo.all_bypass_charge_support",
                )
                capture(
                    "gameSupport",
                    "/system/bin/getprop",
                    "persist.vivo.game_bypass_charge_support",
                )
                b.putInt(
                    "exit",
                    if (
                        listOf("settings", "bypass", "allSupport", "gameSupport").all {
                            b.getInt("${it}Exit") == 0
                        }
                    )
                        0
                    else -5,
                )
            }
            ENABLE,
            DISABLE -> {
                val o =
                    run(
                        "/system/bin/settings",
                        "put",
                        "secure",
                        "settings_battery_charge_director",
                        if (operation == ENABLE) "1" else "0",
                    )
                b.putInt("exit", o.code)
                b.putString("output", o.text)
            }
            else -> {
                b.putInt("exit", -22)
                b.putString("error", "未知固定操作")
            }
        }
        return b
    }

    override fun destroy() {
        activeChild?.destroyForcibly()
        exitProcess(0)
    }
}
