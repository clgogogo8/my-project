package com.example.demoplugin

import android.os.Bundle
import android.widget.TextView
import com.example.multiopen.api.PluginActivity

class DemoActivity : PluginActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val tv = TextView(host).apply {
            textSize = 20f
            text = "Hello from plugin!\n数据目录: ${host.filesDir}"
        }
        host.setContentView(tv)
    }
}
