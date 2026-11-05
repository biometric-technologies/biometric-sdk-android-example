// The Android Gradle plugin and Kotlin versions of the SDK (sdk/settings.gradle.kts and
// sdk/build.gradle.kts): with the SDK build included, the Android plugin refuses a second version.
plugins {
    id("com.android.application") version "8.7.3" apply false
    kotlin("android") version "2.2.20" apply false
}
