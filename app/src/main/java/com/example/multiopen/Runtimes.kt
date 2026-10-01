package com.example.multiopen

import android.content.Context

/** 缓存每个实例的 ClassLoader / Resources。进程被杀后会按需从磁盘重建。 */
object VirtualRuntimes {
    private val cache = HashMap<String, PluginRuntime>()

    @Synchronized
    fun get(ctx: Context, app: VirtualApp, isolated: Boolean): PluginRuntime =
        cache.getOrPut("${app.instanceId}/$isolated") { PluginRuntime(ctx.applicationContext, app, isolated) }

    fun get(ctx: Context, instanceId: String, isolated: Boolean): PluginRuntime =
        get(ctx, VirtualCore.list(ctx).first { it.instanceId == instanceId }, isolated)
}
