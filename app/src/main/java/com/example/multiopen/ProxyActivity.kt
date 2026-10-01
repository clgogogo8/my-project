package com.example.multiopen

import android.app.Activity
import android.content.res.Resources
import android.os.Bundle
import android.widget.Toast
import com.example.multiopen.api.PluginActivity

/** 宿主侧的"壳"Activity：加载插件类并转发生命周期 */
class ProxyActivity : Activity() {
    private var plugin: PluginActivity? = null
    private var runtime: PluginRuntime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apk = intent.getStringExtra(EXTRA_INSTANCE)!!
        val app = VirtualCore.list(this).first { it.instanceId == apk }
        runtime = PluginRuntime(this, app)
        val instance = runtime!!.classLoader.loadClass(app.mainClass).getDeclaredConstructor().newInstance()
        if (instance !is PluginActivity) {
            // MVP 限制：尚不支持运行未经修改的普通 Activity（路线图第 3 步）
            Toast.makeText(this, "${app.mainClass} 不是 PluginActivity，暂不支持", Toast.LENGTH_LONG).show()
            finish(); return
        }
        plugin = instance.also { it.attach(this); it.onCreate(savedInstanceState) }
    }

    override fun getResources(): Resources = runtime?.resources ?: super.getResources()
    override fun getClassLoader(): ClassLoader = runtime?.classLoader ?: super.getClassLoader()
    override fun getFilesDir() = runtime?.let { VirtualCore.dataDir(it.app) } ?: super.getFilesDir()

    override fun onStart() { super.onStart(); plugin?.onStart() }
    override fun onResume() { super.onResume(); plugin?.onResume() }
    override fun onPause() { plugin?.onPause(); super.onPause() }
    override fun onStop() { plugin?.onStop(); super.onStop() }
    override fun onDestroy() { plugin?.onDestroy(); super.onDestroy() }

    companion object { const val EXTRA_INSTANCE = "instance" }
}
