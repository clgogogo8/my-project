package com.example.multiopen

import android.app.Activity
import java.util.concurrent.atomic.AtomicInteger

/**
 * 桩 Activity 池。所有虚拟 Activity 都要“借”一个桩在系统里启动。
 *
 * 若只有一个桩：微信带 FLAG_ACTIVITY_SINGLE_TOP 启动下一个 Activity 时，系统发现“目标 == 栈顶桩”，
 * 直接把 Intent 投给栈顶实例（START_DELIVERED_TO_TOP）而不新建，newActivity 不触发，目标 Activity
 * 永远建不出来 → 启动循环 / ANR。
 *
 * 用多个桩类轮换分配，使“目标桩 ≠ 栈顶桩”，系统就会新建实例。
 * 注：当前都是 standard 桩；launchMode=singleTask/singleInstance 的目标后续再加对应 launchMode 的桩。
 */
open class StubActivity : Activity() {
    companion object {
        const val EXTRA_INSTANCE = "multiopen.instance"
        const val EXTRA_CLASS = "multiopen.class"

        /** 池里所有桩的类名（含基类自身）；必须与 AndroidManifest 里声明的保持一致 */
        val classNames: List<String> = buildList {
            add(StubActivity::class.java.name)
            for (i in 1..7) add("com.example.multiopen.StubActivity$i")
        }

        private val cursor = AtomicInteger(0)

        /** round-robin 取下一个桩类名，保证相邻两次不同（池大小 > 1） */
        fun nextStub(): String = classNames[Math.floorMod(cursor.getAndIncrement(), classNames.size)]
    }
}

// 桩池成员：仅用于提供不同的类名，无任何逻辑（newActivity 会把它们换成真实 Activity）
class StubActivity1 : StubActivity()
class StubActivity2 : StubActivity()
class StubActivity3 : StubActivity()
class StubActivity4 : StubActivity()
class StubActivity5 : StubActivity()
class StubActivity6 : StubActivity()
class StubActivity7 : StubActivity()
