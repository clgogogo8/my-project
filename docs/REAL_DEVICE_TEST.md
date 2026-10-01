# 真机验证指南（给本地 AI / 本地开发机执行）

本云端环境没有 Android SDK，编译不了。这份文档是让你在**本地带 Android SDK 的机器 + 一台真机**上把 MultiOpen 跑起来、抓日志的完整步骤。
先用项目自带的测试 App 验证框架本身，**最后**再试微信（微信极难，见文末）。

---

## 0. 前置条件

- JDK 17（`java -version` 显示 17）
- Android SDK：含 `platform-tools`(adb)、`platforms;android-34`、`build-tools;34.0.0`
- Gradle 8.7+（AGP 8.5.2 要求）。没有就让项目自带 wrapper：见第 2 步。
- 一台真机，Android 9～14（API 28～34），**开启「开发者选项 → USB 调试」**，用数据线连上，`adb devices` 能看到它。
- 这些 hook 依赖系统私有字段，**模拟器/不同 ROM 可能字段名不同**，优先用真机。

## 1. 配置 SDK 路径

在项目根目录建 `local.properties`（不要提交）：

```
sdk.dir=/你的/Android/Sdk 路径
```

macOS 一般是 `/Users/你/Library/Android/sdk`，Linux 一般是 `/home/你/Android/Sdk`。

## 2. 编译

项目没带 gradle wrapper，先生成（只需一次）：

```bash
cd <项目根目录>
gradle wrapper --gradle-version 8.7
```

然后编译宿主 + 两个测试 App：

```bash
./gradlew :app:assembleDebug :test-hello:assembleDebug :test-multi:assembleDebug
```

产物：
- 宿主：`app/build/outputs/apk/debug/app-debug.apk`
- 测试：`test-hello/build/outputs/apk/debug/test-hello-debug.apk`
- 测试：`test-multi/build/outputs/apk/debug/test-multi-debug.apk`

**编译报错就先停下，把完整错误贴回来**——不要改 Android 版本去绕过。

## 3. 安装宿主、推送测试 APK 到手机

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb push test-hello/build/outputs/apk/debug/test-hello-debug.apk /sdcard/Download/
adb push test-multi/build/outputs/apk/debug/test-multi-debug.apk /sdcard/Download/
```

## 4. 开始抓日志（操作前先开着）

新开一个终端：

```bash
adb logcat -c                       # 清空旧日志
adb logcat MultiOpen:I AndroidRuntime:E *:S
```

- `MultiOpen:I` 是本框架自己打的日志（TAG=`MultiOpen`）。
- `AndroidRuntime:E` 是崩溃栈。
- 看不全就换全量：`adb logcat | grep -E "MultiOpen|AndroidRuntime|FATAL"`

## 5. 用测试 App 验证框架（关键）

在手机上打开 **MultiOpen** App：

### 5.1 数据隔离（test-hello）
1. 点「添加插件 APK」→ 文件选择器里进 `Download`，选 `test-hello-debug.apk`。
2. **再添加一次同一个文件**（这就是“多开”）。列表应出现两项。
3. 分别点开第 1 项、第 2 项，各自多开几次。
4. **预期**：每个实例界面显示的 `prefs 启动次数` / `文件 启动次数` **各自独立累加**（实例 A 开 3 次是 3，实例 B 第一次开是 1），`filesDir` 路径带不同的实例目录。
   → 说明 `VirtualContext` 的数据隔离生效。
5. 界面还会显示 `packageName=com.example.hello`（不是宿主包名）→ 说明 getPackageName 伪装生效。

### 5.2 Application / Activity 跳转（test-multi）
1. 同样添加 `test-multi-debug.apk`，打开。
2. **预期**：
   - `application=com.example.multi.MyApp`（插件自己的 Application，不是宿主的）
   - `applicationContext===application: true`
   - 点「打开第二个 Activity」能跳转，第二页显示收到的 msg，点「回复」能带结果返回第一页。
   → 说明虚拟 Application、Activity 间跳转、AppCompat 主题与资源加载都生效。

### 5.3 判断成功/失败
- **成功**：上面界面都正常显示、数值符合预期、没崩。logcat 里有 `newActivity: stub -> ...`、`virtual Application created: ...` 等 MultiOpen 日志。
- **失败**：App 闪退 / 白屏 / 数值不对。**把 logcat 里从你点击那一刻起、所有 `MultiOpen` 和 `AndroidRuntime`/`FATAL` 的行完整复制回来**（尤其崩溃栈的 `Caused by:` 部分）。

## 6. 把结果回传给云端 AI

无论成功失败，回传这些，我就能精准定位：

1. 第 2 步编译是否通过（失败贴完整报错）。
2. 第 5 步每个测试 App 的实际表现（界面截图或文字描述）。
3. logcat 输出：`MultiOpen` 全部行 + 任何崩溃栈。
4. 手机的 Android 版本（`adb shell getprop ro.build.version.release`）和机型。

---

## 7. 试微信（高难度，别期待一次成功）

**先过了第 5 步再做这步。** 微信是业界最难多开的 App 之一，有大量 native 校验、反调试、环境检测，当前框架大概率还跑不起来——这一步的目的是**抓第一个崩溃点**，让云端 AI 知道下一个要攻克什么。

两个硬坑先知道：

1. **必须是单个完整 APK**。应用商店装的微信通常是 split APK（base + `config.arm64_v8a` + `config.xxhdpi` 等多个文件），本框架目前只处理单个 `base.apk`，**split 包会缺 so/资源直接崩**。
   - 办法：用 `adb shell pm path com.tencent.mm` 看它有几个 apk；如果多于一个，需要先合并成一个 universal apk（比如用 APKEditor 之类工具 merge），或者找单包版本的微信安装包。
2. 微信很大，`adb push` 和复制进沙箱都要时间。

操作同第 5 步：push 微信 apk → 在 MultiOpen 里添加 → 打开 → **立刻在 logcat 里抓第一个 `FATAL` / `Caused by:`**，连同它前面的 `MultiOpen` 日志一起回传。

那第一个崩溃栈，就是下一步要修的地方。
