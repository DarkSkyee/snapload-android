import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Base64
import java.util.Properties

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
        versionCode = 21
        versionName = "3.1.2"
        ndk {
            abiFilters.clear()
            abiFilters.add("arm64-v8a")
        }
    }

    val localProps = Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            FileInputStream(localPropertiesFile).use { load(it) }
        }
    }

    val keystorePath = System.getenv("RELEASE_STORE_FILE")
        ?: System.getenv("KEYSTORE_FILE")
        ?: localProps.getProperty("release.storeFile")
        ?: localProps.getProperty("storeFile")
        ?: "snapload-release.jks"

    val keystoreFile = if (File(keystorePath).isAbsolute) {
        File(keystorePath)
    } else {
        file(keystorePath)
    }

    if (!keystoreFile.exists()) {
        val b64Keystore = System.getenv("RELEASE_KEYSTORE_BASE64")?.takeIf { it.isNotBlank() }
            ?: System.getenv("KEYSTORE_BASE64")?.takeIf { it.isNotBlank() }
            ?: file("keystore.base64").takeIf { it.exists() }?.readText()
            ?: rootProject.file("app/keystore.base64").takeIf { it.exists() }?.readText()

        if (b64Keystore != null) {
            keystoreFile.parentFile?.mkdirs()
            val sanitized = b64Keystore.replace(Regex("\\s+"), "")
            val bytes = try {
                Base64.getMimeDecoder().decode(sanitized)
            } catch (_: Exception) {
                Base64.getDecoder().decode(sanitized)
            }
            keystoreFile.writeBytes(bytes)
        }
    }

    val storePasswordValue = System.getenv("RELEASE_STORE_PASSWORD")
        ?: System.getenv("KEYSTORE_PASSWORD")
        ?: localProps.getProperty("release.storePassword")
        ?: localProps.getProperty("storePassword")
        ?: "snapload_release_2026"

    val keyAliasValue = System.getenv("RELEASE_KEY_ALIAS")
        ?: System.getenv("KEY_ALIAS")
        ?: localProps.getProperty("release.keyAlias")
        ?: localProps.getProperty("keyAlias")
        ?: "snapload"

    val keyPasswordValue = System.getenv("RELEASE_KEY_PASSWORD")
        ?: System.getenv("KEY_PASSWORD")
        ?: localProps.getProperty("release.keyPassword")
        ?: localProps.getProperty("keyPassword")
        ?: "snapload_release_2026"

    signingConfigs {
        create("release") {
            storeFile = keystoreFile
            storePassword = storePasswordValue
            keyAlias = keyAliasValue
            keyPassword = keyPasswordValue
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isDebuggable = false
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
        includeInApk = true
        includeInBundle = true
    }

    packaging {
      jniLibs {
        useLegacyPackaging = true
      }
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        excludes += "META-INF/DEPENDENCIES"
        excludes += "META-INF/LICENSE"
        excludes += "META-INF/LICENSE.txt"
        excludes += "META-INF/license.txt"
        excludes += "META-INF/NOTICE"
        excludes += "META-INF/NOTICE.txt"
        excludes += "META-INF/notice.txt"
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
  testImplementation("org.json:json:20240303")

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
