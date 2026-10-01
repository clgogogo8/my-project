package com.example.multiopen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * 宿主自己的真实前台 Service。插件（如微信）里的保活/推送 Service 调 startForeground 时，走的是我们反射
 * 创建、attach 了假 Binder token 的虚拟 Service —— 这个 token AMS 没登记，真实 startForeground 会抛异常、
 * 插件崩。所以把“进程前台化”这件事转交给这个宿主声明的真 Service 来做：它用宿主自己的通知渠道和图标
 * 真正把进程拉到前台（拿到真实优先级），插件那边的 startForeground 由 ART hook 吞掉、不再打到 AMS。
 *
 * 用引用计数：有虚拟 Service 要前台就 [promote]，退出前台就 [demote]，计数归零才真正停前台。
 *
 * 局限（诚实记录，登录后才能验证）：这里显示的是宿主通用通知，不渲染插件自己的通知内容（icon/标题来自
 * 插件资源表，宿主 NotificationManager 解析不了）。渲染插件通知内容是后续项。
 */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ensureChannel(this)
            val notif = buildNotification(this)
            // Android 14（API 34）起前台 Service 必须带类型；这里用 specialUse（清单里声明 + property 说明用途）。
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notif,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
        } catch (t: Throwable) {
            Log.e(MultiOpenApp.TAG, "KeepAliveService startForeground 失败", t)
            stopSelf()
        }
        return START_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "multiopen.keepalive"
        private const val NOTIFICATION_ID = 0x4D4F // 'MO'
        /** 有多少个虚拟 Service 当前要求前台状态 */
        private val refCount = AtomicInteger(0)

        /** 一个虚拟 Service 请求前台：引用计数 +1，必要时拉起宿主前台 Service */
        fun promote(host: Context) {
            val n = refCount.incrementAndGet()
            if (n == 1) {
                try {
                    val i = Intent(host, KeepAliveService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) host.startForegroundService(i)
                    else host.startService(i)
                    Log.i(MultiOpenApp.TAG, "KeepAliveService promote -> 宿主前台已拉起")
                } catch (t: Throwable) {
                    Log.e(MultiOpenApp.TAG, "KeepAliveService promote 失败", t)
                    refCount.decrementAndGet()
                }
            }
        }

        /** 一个虚拟 Service 退出前台：引用计数 -1，归零才真正停前台 */
        fun demote(host: Context) {
            if (refCount.get() <= 0) return
            val n = refCount.decrementAndGet()
            if (n <= 0) {
                refCount.set(0)
                try {
                    host.stopService(Intent(host, KeepAliveService::class.java))
                    Log.i(MultiOpenApp.TAG, "KeepAliveService demote -> 宿主前台已停")
                } catch (t: Throwable) {
                    Log.w(MultiOpenApp.TAG, "KeepAliveService demote 失败", t)
                }
            }
        }

        private fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "后台运行", NotificationManager.IMPORTANCE_LOW)
                        .apply { setShowBadge(false) }
                )
            }
        }

        private fun buildNotification(ctx: Context): Notification {
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Notification.Builder(ctx, CHANNEL_ID) else @Suppress("DEPRECATION") Notification.Builder(ctx)
            return builder
                .setContentTitle(ctx.applicationInfo.loadLabel(ctx.packageManager))
                .setContentText("正在后台运行")
                .setSmallIcon(ctx.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.sym_def_app_icon)
                .setOngoing(true)
                .build()
        }
    }
}
