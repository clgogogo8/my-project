package com.example.multiopen

import android.app.Activity
import java.util.concurrent.atomic.AtomicInteger

/**
 * 桩 Activity 池。所有虚拟 Activity 都要“借”一个桩在系统里启动。
 *
 * 单桩会被 SINGLE_TOP 复用（START_DELIVERED_TO_TOP）导致目标建不出来 → 启动循环，所以用多个桩轮换，
 * 使“目标桩 ≠ 栈顶桩”。进一步地，桩要和目标 Activity 的 launchMode 匹配：standard/singleTop 走通用池
 * （已验证能跑），singleTask / singleInstance 用 manifest 里声明了对应 launchMode 的专用桩。
 */
open class StubActivity : Activity() {
    companion object {
        const val EXTRA_INSTANCE = "multiopen.instance"
        const val EXTRA_CLASS = "multiopen.class"

        // standard / singleTop：沿用已跑通的通用池（StubActivity + StubActivity1..7）
        private val standard: List<String> = buildList {
            add(StubActivity::class.java.name)
            for (i in 1..7) add("com.example.multiopen.StubActivity$i")
        }
        // 专用池：类名与 AndroidManifest 里声明的 launchMode 一致
        private val singleTask: List<String> = (0..3).map { "com.example.multiopen.StubTask$it" }
        private val singleInstance: List<String> = (0..1).map { "com.example.multiopen.StubInstance$it" }

        /** newActivity 用来识别“这是一个桩”的全部类名 */
        val classNames: List<String> = standard + singleTask + singleInstance

        private val stdCursor = AtomicInteger(0)
        private val taskCursor = AtomicInteger(0)
        private val instCursor = AtomicInteger(0)

        /** 按目标 launchMode 取下一个桩（0/1 通用池，2 singleTask，3 singleInstance） */
        fun nextStub(launchMode: Int): String = when (launchMode) {
            2 -> singleTask[Math.floorMod(taskCursor.getAndIncrement(), singleTask.size)]
            3 -> singleInstance[Math.floorMod(instCursor.getAndIncrement(), singleInstance.size)]
            else -> standard[Math.floorMod(stdCursor.getAndIncrement(), standard.size)]
        }

        /** 兼容旧调用：未知 launchMode 时按通用池 */
        fun nextStub(): String = nextStub(0)
    }
}

// 通用池
class StubActivity1 : StubActivity()
class StubActivity2 : StubActivity()
class StubActivity3 : StubActivity()
class StubActivity4 : StubActivity()
class StubActivity5 : StubActivity()
class StubActivity6 : StubActivity()
class StubActivity7 : StubActivity()

// singleTask 专用桩
class StubTask0 : StubActivity()
class StubTask1 : StubActivity()
class StubTask2 : StubActivity()
class StubTask3 : StubActivity()

// singleInstance 专用桩
class StubInstance0 : StubActivity()
class StubInstance1 : StubActivity()
