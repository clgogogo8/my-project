package com.example.multi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** 普通 Activity：验证 Application 虚拟化、applicationContext 隔离、Activity 间跳转与返回结果 */
class MainActivity : Activity() {
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        val info = TextView(this).apply {
            textSize = 15f
            text = "application=${activity.application.javaClass.name}\n" +
                "applicationContext===application: ${activity.applicationContext === activity.application}\n" +
                "applicationContext.filesDir=${activity.applicationContext.filesDir}\n" +
                "app prefs app_starts=${activity.applicationContext.getSharedPreferences("app", MODE_PRIVATE).getInt("app_starts", -1)}"
        }
        val go = Button(this).apply {
            text = "打开第二个 Activity"
            setOnClickListener {
                startActivityForResult(Intent(activity, SecondActivity::class.java).putExtra("msg", "来自第一个页面"), 1)
            }
        }
        result = TextView(this).apply { text = "结果: (无)" }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(info); addView(go); addView(result)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        result.text = "结果: requestCode=$requestCode resultCode=$resultCode data=${data?.getStringExtra("reply")}"
    }
}
