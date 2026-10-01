plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.demoplugin"
    compileSdk = 34
    defaultConfig { applicationId = "com.example.demoplugin"; minSdk = 24; targetSdk = 34; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    // compileOnly：接口类由宿主提供，避免重复打包导致 ClassLoader 冲突
    compileOnly(project(":plugin-api"))
}
