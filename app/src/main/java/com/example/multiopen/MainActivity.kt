package com.example.multiopen

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import com.example.multiopen.api.PluginActivity

class MainActivity : Activity() {
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val add = Button(this).apply { text = "添加插件 APK（可重复添加以多开）" }
        list = ListView(this)
        root.addView(add, ViewGroup.LayoutParams(-1, -2))
        root.addView(list, ViewGroup.LayoutParams(-1, -1))
        setContentView(root)

        add.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }, REQ_PICK)
        }
        refresh()
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == REQ_PICK && res == RESULT_OK && data?.data != null) {
            try {
                val app = VirtualCore.install(this, data.data!!)
                Toast.makeText(this, "已添加 ${app.packageName}\n入口 ${app.mainClass}", Toast.LENGTH_LONG).show()
                refresh()
            } catch (e: Exception) {
                Toast.makeText(this, "添加失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refresh() {
        val apps = VirtualCore.list(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, apps.map { it.label })
        list.setOnItemClickListener { _, _, i, _ -> launch(apps[i]) }
    }

    /** 入口类是 PluginActivity → 走 ProxyActivity；否则按普通 APK 走桩 Activity + Instrumentation 替换 */
    private fun launch(app: VirtualApp) {
        val rt = VirtualRuntimes.get(this, app, false)
        val isPlugin = try {
            PluginActivity::class.java.isAssignableFrom(rt.classLoader.loadClass(app.mainClass))
        } catch (t: Throwable) { false }
        if (isPlugin) {
            startActivity(Intent(this, ProxyActivity::class.java).putExtra(ProxyActivity.EXTRA_INSTANCE, app.instanceId))
        } else {
            val launchMode = try { rt.manifest.activityLaunchModes[app.mainClass] ?: 0 } catch (t: Throwable) { 0 }
            startActivity(Intent()
                .setComponent(ComponentName(this, StubActivity.nextStub(launchMode)))
                .putExtra(StubActivity.EXTRA_INSTANCE, app.instanceId)
                .putExtra(StubActivity.EXTRA_CLASS, app.mainClass))
        }
    }

    companion object { const val REQ_PICK = 1 }
}
