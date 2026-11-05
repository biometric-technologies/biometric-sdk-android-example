import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "net.iriscan.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.iriscan.example"
        minSdk = 23 // as the SDK
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // TensorFlow Lite maps the FaceNet model from the assets, which needs it uncompressed
    androidResources {
        noCompress += "tflite"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // built from the SDK checkout in sdk/ when there is one (see settings.gradle.kts)
    implementation("net.iriscan:biometric-sdk:0.7.0")
    // FileProvider, to give the camera app a file for the full-size photo
    implementation("androidx.core:core:1.13.1")
}
