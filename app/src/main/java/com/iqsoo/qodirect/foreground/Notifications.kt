package com.iqsoo.qodirect.foreground

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.iqsoo.qodirect.MainActivity
import com.iqsoo.qodirect.R

object Notifications {
    const val CHANNEL = "qo_management"
    const val ID = 81

    fun channels(c: Context) {
        c.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(CHANNEL, "智能直驱管理", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "供电自动管理、状态变化与权限提示"
                }
            )
    }

    private fun open(c: Context) =
        PendingIntent.getActivity(
            c,
            1,
            Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun ongoing(c: Context, paused: Boolean, reason: String): Notification {
        channels(c)
        val action = if (paused) "resume" else "pause"
        val control =
            PendingIntent.getService(
                c,
                2,
                Intent(c, AutomationService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                c,
                3,
                Intent(c, AutomationService::class.java).setAction("stop"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_qo)
            .setContentTitle(if (paused) "QO 智能直驱 · 已暂停" else "QO 智能直驱 · 正在管理")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setContentIntent(open(c))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, if (paused) "恢复" else "暂停", control)
            .addAction(0, "停止", stop)
            .build()
    }

    fun event(c: Context, message: String) {
        channels(c)
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return
        NotificationManagerCompat.from(c)
            .notify(
                82,
                NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_qo)
                    .setContentTitle("QO 智能直驱")
                    .setContentText(message)
                    .setContentIntent(open(c))
                    .setAutoCancel(true)
                    .build(),
            )
    }
}
