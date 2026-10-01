package com.example.hello

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** 普通 Activity：不依赖宿主的任何接口，用来验证"运行未修改 APK"。 */
class HelloActivity : Activity() {
    private var count = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            textSize = 18f
            // getString(R.string...) 用来验证 Resources 已被替换为插件自己的
            text = getString(R.string.hello_text) + "\npackageName=$packageName\nfilesDir=$filesDir\nclassLoader=${javaClass.classLoader}"
        }
        val btn = Button(this).apply {
            text = "点击计数: 0"
            setOnClickListener { count++; text = "点击计数: $count" }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv); addView(btn)
        })
    }
}
