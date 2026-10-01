package com.example.multi

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** AppCompatActivity + XML 布局：验证 AppCompat 主题、插件资源与布局加载 */
class SecondActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)
        supportActionBar?.title = getString(R.string.second_title)
        findViewById<TextView>(R.id.info).text =
            "收到 msg=${intent.getStringExtra("msg")}\napplication=${application.javaClass.name}\nfilesDir=$filesDir"
        findViewById<Button>(R.id.reply).setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra("reply", "来自第二个页面"))
            finish()
        }
    }
}
