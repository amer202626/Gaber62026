import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.dalyly"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dalyly"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Dynamically read GEMINI_API_KEY from environment configuration files
        val envFile = project.rootProject.file(".env")
        val fallbackFile = project.rootProject.file(".env.example")
        var geminiKey = ""
        if (envFile.exists()) {
            val properties = Properties()
            properties.load(envFile.inputStream())
            geminiKey = properties.getProperty("GEMINI_API_KEY") ?: ""
        } else if (fallbackFile.exists()) {
            val properties = Properties()
            properties.load(fallbackFile.inputStream())
            geminiKey = properties.getProperty("GEMINI_API_KEY") ?: ""
        }
        if (geminiKey.isEmpty()) {
            geminiKey = System.getenv("GEMINI_API_KEY") ?: ""
        }
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.common)

    // Utilities
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.register("printApkSize") {
    doLast {
        val apkFile = project.rootProject.file(".build-outputs/app-debug.apk")
        if (apkFile.exists()) {
            val bytes = apkFile.length()
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            println("=== APK SIZE REPORT ===")
            println("APK Path: " + apkFile.absolutePath)
            println(String.format("APK Size: %.2f MB (%d bytes)", mb, bytes))
            println("=======================")
        } else {
            println("=== APK SIZE REPORT ===")
            println("APK file not found yet. Run an assemble task first.")
            println("=======================")
        }

        // Print drawable sizes
        val drawableDir = project.file("src/main/res/drawable")
        if (drawableDir.exists() && drawableDir.isDirectory) {
            println("=== LOCAL DRAWABLES SIZE ===")
            drawableDir.listFiles()?.forEach { file ->
                println("${file.name}: ${file.length()} bytes")
            }
            println("============================")
        }
    }
}

tasks.register("convertToWebp") {
    doLast {
        val srcFile = project.file("src/main/res/drawable/ic_app_foreground_asset.png")
        val destFile = project.file("src/main/res/drawable/ic_app_foreground_asset.webp")
        if (srcFile.exists()) {
            try {
                val pb = ProcessBuilder("cwebp", "-q", "75", srcFile.absolutePath, "-o", destFile.absolutePath)
                val process = pb.start()
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    println("Successfully converted PNG to WebP with 75% quality!")
                    println("WebP size: ${destFile.length()} bytes")
                } else {
                    println("cwebp failed with exit code $exitCode")
                }
            } catch (e: Exception) {
                println("cwebp command not found on host: ${e.message}")
            }
        } else {
            println("Source PNG file not found at: ${srcFile.absolutePath}")
        }
    }
}


