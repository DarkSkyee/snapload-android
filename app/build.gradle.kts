import java.io.File
import java.io.FileOutputStream
import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.mediadownloader"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.mediadownloader"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "3.0.7"
        ndk {
            abiFilters.clear()
            abiFilters.add("arm64-v8a")
        }
    }

    val keystoreFile = file("snapload-release.jks")
    if (!keystoreFile.exists()) {
        val b64Keystore = "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"
        keystoreFile.writeBytes(Base64.getDecoder().decode(b64Keystore))
    }

    signingConfigs {
        create("release") {
            storeFile = keystoreFile
            storePassword = "snapload_release_2026"
            keyAlias = "snapload"
            keyPassword = "snapload_release_2026"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
      jniLibs {
        useLegacyPackaging = true
      }
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        excludes += "/META-INF/DEPENDENCIES"
        excludes += "META-INF/**/LICENSE*"
        excludes += "META-INF/**/NOTICE*"
        excludes += "META-INF/**/DEPENDENCIES"
        excludes += "META-INF/**/AL2.0"
        excludes += "META-INF/**/LGPL2.1"
        excludes += "META-INF/*.version"
        excludes += "META-INF/*.properties"
        excludes += "META-INF/**/*.version"
        excludes += "META-INF/**/*.properties"
        excludes += "META-INF/version-control-info.textproto"
        excludes += "META-INF/androidx/**"
        excludes += "META-INF/com/android/build/gradle/**"
        excludes += "META-INF/kotlin-tooling-metadata.json"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Downloader & FFmpeg
  implementation(libs.youtubedl.library)
  implementation(libs.youtubedl.ffmpeg)
  implementation(libs.coil.compose)
  implementation(libs.androidx.compose.material.icons)
}
