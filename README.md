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
   - [~] 3b-2：Service / Broadcast / ContentProvider（进程内实现，见下方“适配微信”）；桩池已支持 standard/singleTop/singleTask/singleInstance，方向/透明主题待办
   - [x] 数据隔离：`VirtualContext` 重定向 files/cache/databases/SharedPreferences 到实例目录（待真机验证）
   - [~] 3c：伪装与路径
     - [x] native so：按设备 ABI 从 APK 解出 `lib/<abi>/*.so`（`NativeLibs`），作为 `DexClassLoader` 的 librarySearchPath
     - [x] `getApplicationInfo()` 伪装：包名 / dataDir / nativeLibraryDir / sourceDir 指向本实例（`VirtualContext`）
     - [x] `getPackageName()` 伪装 + `IActivityManager` 代理 hook（`ActivityManagerHook`）：应用读插件包名，binder 层把误入 AMS 的虚拟包名归一回宿主包名
     - [x] `PackageManager` 代理 hook（`PackageManagerHook`）：拦截对虚拟包的 getPackageInfo / getApplicationInfo（按包名）与 getActivityInfo / getServiceInfo / getProviderInfo / getReceiverInfo（按 ComponentName），用 `VirtualAppInfo` 现解 APK 构造结果
     - [x] 外部存储重定向（app 私有外部目录 → 实例目录，`VirtualContext`；公共路径待 native）
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
- [~] 后台保活 / startForeground：宿主自带真前台 Service（`KeepAliveService`，Android 14 `specialUse` 类型）+ ART hook 拦虚拟 Service 的 `startForeground`/`stopForeground` 转交它（引用计数）。代码就位；实际保活/推送拉起效果需登录后才能验证
- [ ] content:// 跨组件解析（hook ContentResolver / getContentProvider，FileProvider 跨 App 分享）
- [x] Tinker 热修复（微信 Application 基于 Tinker）：attachBaseContext 需 `ApplicationInfo.metaData`（已补 `GET_META_DATA`）；attachBaseContext 期间会重入 startService（已改占位登记）
- [x] 宿主权限声明（INTERNET / ACCESS_NETWORK_STATE 等）：修 Cronet 的 "Neither user nor process has ACCESS_NETWORK_STATE"
- [x] 资源系统：`le5.j` 查不到资源是因为微信后台基础设施用进程全局 `currentApplication` 拿 Resources → 已将虚拟 Application 设为 `mInitialApplication`，`createConfigurationContext` 派生 context 也保留插件资源
- [x] 核心 Kernel（mCoreAccount）初始化：靠进程名伪装（`fakeProcessName` → 包名，多进程 Kernel 分发）+ provider 先于 Application.onCreate
- [x] 启动期跨线程死锁：`ensure` 的 onCreate 移出锁（ForkJoin worker 回调 bindService 不再被锁住）
- [x] 桩池（`StubActivity` + `StubActivity1..7` 轮换）：破 SINGLE_TOP 单桩启动循环 → **WelcomeActivity 创建、微信欢迎页显示出来**
- [x] Cronet `BuildInfo` 崩溃（登录页 SIGTRAP@libcronet）：Pine ART hook `ApplicationPackageManager.getInstallerPackageName`，对虚拟包返回宿主 installer——微信绕过所有代理的 `ig5.n1` 路径最终也走这个 Java 方法，被 ART hook 从源头挡住（`ArtHook` + `top.canyie.pine:core`）
- [x] **UI 可导航**：欢迎页 → 手机号登录页（`MobileInputUI`）→ 其他登录方式 → 账号密码登录页（`LoginUI`）全部稳定显示、无崩溃（真机验证）
- [x] 启动期 ANR 查明：**仅首次冷启动**（~8.8s，其中 30 个 provider.onCreate 串行约 3.7s/42% + 微信自身 Application 重）；热启动 ~0.44s、无 ANR。属冷启动特性，可接受（provider 并行化有顺序依赖风险、只影响首次，暂不动）
- [ ] 登录（**需用户本人操作 + 不可控**）：到登录输入页 OK；真正提交登录要连腾讯服务器做设备注册/安全校验/风控，非官方容器 + 伪造环境下很可能被拦，这一步不在代码能解决范围
- [x] 桩池按 launchMode 分配：standard/singleTop 通用池 + singleTask/singleInstance 专用桩（manifest 声明）
- [x] app 私有外部存储隔离（`getExternalFilesDir` 等 → 实例目录）
- [ ] 竖屏锁定 / 透明主题的桩（深层页面可能需要）

### 还需真机专项（都要动 hidden 类型或 native，不能盲写，需单独一轮真机调）
- [~] content:// 跨组件解析：`ActivityManagerHook` 拦 `getContentProvider`/`getContentProviderExternal`，对已登记虚拟 authority 返回本地 provider 的 `IContentProvider`（`ContentProviderHolder`，进程内）。代码就位、回归通过；实际 FileProvider 分享效果需登录后聊天场景验证
- [~] 系统级保活 / startForeground / 推送：宿主声明真前台 Service（`KeepAliveService`），虚拟 Service 的 `startForeground` 经 ART hook 转交它真正把进程拉到前台（系统感知）。进程内 Service 模拟仍在；推送拉起（被系统/AMS 唤起冷启动）需登录后验证
- [ ] 渲染插件自己的通知内容：当前 `KeepAliveService` 只显示宿主通用通知，不渲染插件的 icon/标题（插件资源表宿主 NotificationManager 解析不了）
- [ ] native 层路径重定向 + 设备信息伪装：需 PLT/inline hook（如 xhook）改 /proc、Environment 公共路径、设备标识；与登录风控强相关，且不保证能过
