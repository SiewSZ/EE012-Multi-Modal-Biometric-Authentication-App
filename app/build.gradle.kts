@file:Suppress("DEPRECATION")
import org.gradle.api.JavaVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.chaquopy)
}

// --- THIS IS THE CORRECT, UNIFIED CHAQUOPY CONFIGURATION ---
chaquopy {
    // Tell Chaquopy where your Python source files are located.
    sourceSets {
        getByName("main") {
            srcDir("src/main/python")
        }
    }
    // Define the Python version and libraries to install.
    defaultConfig {
        // Switch to a more broadly compatible Python version
        version = "3.8"
        pyc {
            src = false
        }
        pip {
            options("--find-links", "https://chaquo.com/jp/chaquopy/wheels")
            // Specify known compatible versions
            install("numpy")                  // Used everywhere
            install("Pillow")
            install("scikit-image")
            install("opencv-python")
            install("face_recognition")
            install("pydub")
            install("scipy")
            install("scikit-learn")
            install("librosa==0.9.2")
            install("resampy==0.3.1")
            install("python_speech_features") // For voice_auth.py

        }
    }
}


android {
    namespace = "com.example.ee012"
    compileSdk = 36

    aaptOptions {
        noCompress += ".tflite"
    }

    // The python sourceSets block has been REMOVED from here.
    // It is now correctly placed in the top-level chaquopy block.

    defaultConfig {
        applicationId = "com.example.ee012"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
    dependenciesInfo {
        includeInBundle = true
        includeInApk = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.constraintlayout)
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-database")
    implementation(libs.androidx.activity)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("com.google.mlkit:face-detection:16.1.6")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.guava:guava:33.0.0-android")
    val cameraVersion = "1.5.1"
    implementation("androidx.camera:camera-view:${cameraVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraVersion}")
    implementation("androidx.camera:camera-core:${cameraVersion}")
    implementation("androidx.camera:camera-camera2:${cameraVersion}")
    implementation("com.google.android.gms:play-services-base:18.4.0")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
}
