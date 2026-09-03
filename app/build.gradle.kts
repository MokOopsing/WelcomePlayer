plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val buildVersionName = project.findProperty("versionName") as String? ?: "1.0"

android {
    namespace = "com.mokoopsing.welcomeplayer"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.mokoopsing.welcomeplayer"
        minSdk = 23
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
}
