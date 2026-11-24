plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.riz.adbexec"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.riz.adbexec"
        minSdk = 30
        targetSdk = 34
        versionCode = 5
        versionName = "1.4-trace"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("com.github.MuntashirAkon:sun-security-android:1.1")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
}
