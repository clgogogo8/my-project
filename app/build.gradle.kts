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
}
