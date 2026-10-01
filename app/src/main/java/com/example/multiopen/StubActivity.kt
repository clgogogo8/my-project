package com.example.multiopen

import android.app.Activity

/**
 * 桩 Activity：系统只认识它（已在宿主 Manifest 声明）。
 * HookInstrumentation.newActivity 会把它"换成"插件里的真实 Activity，所以这里不会有任何逻辑。
 * 目前只有一个桩；不同 launchMode / 主题 / 方向的需求后续用桩池解决。
 */
class StubActivity : Activity() {
    companion object {
        const val EXTRA_INSTANCE = "multiopen.instance"
        const val EXTRA_CLASS = "multiopen.class"
    }
}
