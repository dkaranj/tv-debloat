plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.tvdock.home"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.tvdock.home"
        minSdk = 21
        targetSdk = 30   // keeps legacy storage/package-visibility behaviour simple on old TV firmware
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") } // debug-signed: fine for sideloading
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
// no dependencies on purpose: smallest APK, fastest start
