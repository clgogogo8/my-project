# MultiOpen — Android 多开 MVP

## 结构
- `app`：宿主。管理插件 APK 的沙箱安装（每个实例独立目录）、DexClassLoader 加载、ProxyActivity 转发生命周期。
- `plugin-api`：宿主与插件共享的接口（`PluginActivity`）。
- `demo-plugin`：演示插件，编译出 APK 后可在宿主里添加多次，得到多个独立实例。

## 运行
需要 JDK 17 与 Android SDK（`local.properties` 里设置 `sdk.dir`）。
```
gradle wrapper
./gradlew :app:installDebug :demo-plugin:assembleDebug
```
把 `demo-plugin/build/outputs/apk/debug/*.apk` 放到手机里，在宿主点"添加插件 APK"选择它，重复添加即可多开。

## 路线图
1. [x] MVP：加载"按我们的接口写的"插件
2. [ ] 解析 APK Manifest，自动获取入口与组件
3. [ ] 桩 Activity 池 + Hook ActivityManager，运行**未修改**的第三方 APK
4. [ ] Service / Broadcast / ContentProvider / 通知
5. [ ] Native 层路径重定向与设备信息伪装
6. [ ] 兼容性适配（隐藏 API 限制、64/32 位 so）
