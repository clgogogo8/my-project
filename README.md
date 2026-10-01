# MultiOpen — Android 多开 MVP

## 结构
- `app`：宿主。管理插件 APK 的沙箱安装（每个实例独立目录）、DexClassLoader 加载、ProxyActivity 转发生命周期。
- `plugin-api`：宿主与插件共享的接口（`PluginActivity`）。
- `demo-plugin`：演示插件，编译出 APK 后可在宿主里添加多次，得到多个独立实例。

## 运行
需要 JDK 17 与 Android SDK（`local.properties` 里设置 `sdk.dir`）。
```
gradle wrapper
./gradlew :app:installDebug :demo-plugin:assembleDebug :test-hello:assembleDebug :test-multi:assembleDebug
```
把 `demo-plugin/build/outputs/apk/debug/*.apk` 放到手机里，在宿主点"添加插件 APK"选择它，重复添加即可多开。

## 路线图
1. [x] MVP：加载"按我们的接口写的"插件
2. [x] 解析 APK Manifest，自动获取包名与入口 Activity（`ManifestParser`）
3. [~] 运行**未修改**的普通 APK
   - [x] 3a：解除隐藏 API 限制 + 桩 Activity + Instrumentation 替换（`HookInstrumentation`），启动普通 APK 的入口 Activity，替换 Resources/Theme（待真机验证）
   - [x] 3b-1：插件内 Activity 间跳转（`ExecHookInstrumentation` + `rewriteIntent`）、插件自己的 `Application`（`VirtualApplications`）、按 Activity 取主题（待真机验证，测试用 `test-multi`）
   - [ ] 3b-2：桩池（launchMode / 方向 / 透明主题）、Service / Broadcast / ContentProvider
   - [x] 数据隔离：`VirtualContext` 重定向 files/cache/databases/SharedPreferences 到实例目录（待真机验证）
   - [ ] 3c：getPackageName 等伪装、getApplicationContext、外部存储、native so
4. [ ] Service / Broadcast / ContentProvider / 通知
5. [ ] Native 层路径重定向与设备信息伪装
6. [ ] 兼容性适配（隐藏 API 限制、64/32 位 so）
