package com.example.hello

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/** 普通 Activity：不依赖宿主的任何接口，用来验证"运行未修改 APK"与数据隔离。 */
class HelloActivity : Activity() {
    private var count = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 数据隔离验证：每个实例各自累计"启动次数"，分别存在 SharedPreferences 和 files 目录的文件里
        val prefs = getSharedPreferences("test", MODE_PRIVATE)
        val launches = prefs.getInt("launches", 0) + 1
        prefs.edit().putInt("launches", launches).commit()
        val note = File(filesDir, "note.txt")
        val fileLaunches = (note.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 0) + 1
        note.writeText(fileLaunches.toString())

        val activity = this
        val tv = TextView(this).apply {
            textSize = 16f
            text = getString(R.string.hello_text) + "\n" +
                "packageName=${activity.packageName}\n" +
                "filesDir=${activity.filesDir}\n" +
                "activityClassLoader=${activity.javaClass.classLoader?.javaClass?.name}\n" +
                "prefs 启动次数=$launches\n" +
                "文件 启动次数=$fileLaunches"
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
