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
   - [~] 3b-2：Service / Broadcast / ContentProvider（进程内实现，见下方“适配微信”）；桩池（launchMode / 方向 / 透明主题）待办
   - [x] 数据隔离：`VirtualContext` 重定向 files/cache/databases/SharedPreferences 到实例目录（待真机验证）
   - [~] 3c：伪装与路径
     - [x] native so：按设备 ABI 从 APK 解出 `lib/<abi>/*.so`（`NativeLibs`），作为 `DexClassLoader` 的 librarySearchPath
     - [x] `getApplicationInfo()` 伪装：包名 / dataDir / nativeLibraryDir / sourceDir 指向本实例（`VirtualContext`）
     - [x] `getPackageName()` 伪装 + `IActivityManager` 代理 hook（`ActivityManagerHook`）：应用读插件包名，binder 层把误入 AMS 的虚拟包名归一回宿主包名
     - [x] `PackageManager` 代理 hook（`PackageManagerHook`）：拦截对虚拟包的 getPackageInfo / getApplicationInfo（按包名）与 getActivityInfo / getServiceInfo / getProviderInfo / getReceiverInfo（按 ComponentName），用 `VirtualAppInfo` 现解 APK 构造结果
     - [ ] 外部存储重定向
4. [~] Service / Broadcast / ContentProvider（进程内，已可回调生命周期）；系统级保活与通知待办
5. [ ] Native 层路径重定向与设备信息伪装
6. [ ] 兼容性适配（隐藏 API 限制、64/32 位 so）

## 适配微信（WeChat）进度
微信是重度 native + 多组件 App，跑起来还缺：
- [x] native 库解压与加载（否则 `System.loadLibrary("wechatxxx")` 直接崩）
- [x] `ApplicationInfo` 路径伪装（微信大量读自身 dataDir / nativeLibraryDir）
- [x] `IActivityManager` 代理 hook + `getPackageName` 伪装（微信几乎处处校验自身包名）
- [x] `PackageManager` 代理 hook：微信用自身包名查 `getPackageInfo` / `getApplicationInfo` 不再 NameNotFound
- [x] Service（进程内分发 onCreate/onStartCommand/onBind/onDestroy，`VirtualServices`）
- [x] 静态 BroadcastReceiver（按 action 动态注册转发，`VirtualReceivers`）
- [x] ContentProvider 实例化 + onCreate（`VirtualProviders`）
- [ ] 后台保活 / startForeground / 推送拉起（需系统级桩 Service + AMS 方案）
- [ ] content:// 跨组件解析（hook ContentResolver / getContentProvider，FileProvider 跨 App 分享）
- [~] Tinker 热修复（微信 Application 基于 Tinker）：attachBaseContext 需 `ApplicationInfo.metaData`（已补 `GET_META_DATA`）；attachBaseContext 期间会重入 startService（已改占位登记）
- [ ] 多桩池（不同 launchMode / 竖屏锁定 / 透明主题）
