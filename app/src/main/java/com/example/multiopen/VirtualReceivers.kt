package com.example.multiopen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * 把插件 manifest 里声明的静态广播接收器，在实例激活时按其 action 动态注册到进程。收到广播时反射创建
 * 真实 receiver 并用插件 Context 调 onReceive。每个实例只注册一次。
 *
 * 局限：动态注册收不到只发给“已安装静态 receiver”的广播（如开机 BOOT_COMPLETED），Android 13+ 又默认
 * 以 RECEIVER_NOT_EXPORTED 注册（收不到其它 App 的广播）。覆盖应用内自发广播与常见系统 action。
 */
object VirtualReceivers {
    private val registered = ConcurrentHashMap.newKeySet<String>()

    @Synchronized
    fun ensure(host: Context, rt: PluginRuntime) {
        if (!registered.add(rt.app.instanceId)) return
        val manifest = runCatching { ManifestParser.parse(rt.app.apkFile) }.getOrNull() ?: return
        for (r in manifest.receivers) {
            if (r.actions.isEmpty()) continue // 只有静态 action 才能转成 IntentFilter 动态注册
            try {
                val filter = IntentFilter().apply { r.actions.forEach { addAction(it) } }
                register(host, Dispatcher(host, rt, r.name), filter)
                Log.i(MultiOpenApp.TAG, "receiver registered: ${r.name} ${r.actions}")
            } catch (t: Throwable) {
                Log.w(MultiOpenApp.TAG, "register receiver ${r.name} 失败", t)
            }
        }
    }

    private fun register(host: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) {
            host.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            host.registerReceiver(receiver, filter)
        }
    }

    private class Dispatcher(
        private val host: Context, private val rt: PluginRuntime, private val className: String,
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val real = rt.classLoader.loadClass(className).getDeclaredConstructor().newInstance() as BroadcastReceiver
                val id = rt.app.instanceId
                val vctx = VirtualContext(host.applicationContext, id, VirtualCore.dataDir(rt.app), rt) { VirtualApplications.get(id) }
                real.onReceive(vctx, intent)
            } catch (t: Throwable) {
                Log.e(MultiOpenApp.TAG, "receiver $className onReceive 失败", t)
            }
        }
    }
}
