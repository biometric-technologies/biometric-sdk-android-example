pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "biometric-sdk-android-example"
include(":app")

// With a biometric-sdk checkout in sdk/ (as CI makes it, branch iris-v2), the app is built against
// that source; otherwise Gradle takes net.iriscan:biometric-sdk from Maven Central.
val localSdk = file("sdk")
if (localSdk.resolve("settings.gradle.kts").exists()) {
    includeBuild(localSdk)
}
