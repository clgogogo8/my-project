plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.multiopen"
    compileSdk = 34
    defaultConfig { applicationId = "com.example.multiopen"; minSdk = 24; targetSdk = 34; versionCode = 1; versionName = "0.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":plugin-api"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    // ART 方法级 hook（现成 .so + Java API）：拦 ApplicationPackageManager.getInstallerPackageName 等
    // 微信绕过我们代理、直接经该 Java 方法打到真实系统的调用。Android 14 兼容性待真机验证。
    implementation("top.canyie.pine:core:0.3.0")
}
