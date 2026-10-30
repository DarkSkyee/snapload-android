import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileOutputStream

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
        versionCode = 3
        versionName = "2.0.1"
        ndk {
            abiFilters.clear()
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      jniLibs {
        useLegacyPackaging = true
      }
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        excludes += "/META-INF/DEPENDENCIES"
        excludes += "/META-INF/LICENSE*"
        excludes += "/META-INF/NOTICE*"
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

fun optimizeNativeZip(zipFile: File, excludeExact: Set<String>, excludePrefixes: List<String>) {
    if (!zipFile.exists()) return
    val tempFile = File(zipFile.parentFile, "${zipFile.name}.opt.tmp")
    val srcZip = ZipFile.builder().setFile(zipFile).get()
    ZipArchiveOutputStream(FileOutputStream(tempFile)).use { outZip ->
        outZip.setLevel(9)
        val entries = srcZip.entries
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name
            val skip = excludeExact.contains(name) || excludePrefixes.any { name.startsWith(it) }
            if (!skip) {
                val newEntry = ZipArchiveEntry(name).apply {
                    externalAttributes = entry.externalAttributes
                    unixMode = entry.unixMode
                    time = entry.time
                }
                outZip.putArchiveEntry(newEntry)
                if (!entry.isDirectory) {
                    srcZip.getInputStream(entry).use { inputStream ->
                        inputStream.copyTo(outZip)
                    }
                }
                outZip.closeArchiveEntry()
            }
        }
    }
    srcZip.close()
    if (tempFile.exists() && tempFile.length() > 0L) {
        zipFile.delete()
        tempFile.renameTo(zipFile)
    }
}

tasks.matching { it.name == "mergeReleaseNativeLibs" }.configureEach {
    doLast {
        val baseDir = file("build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a")
        if (!baseDir.exists()) return@doLast
        val ffmpegZip = File(baseDir, "libffmpeg.zip.so")
        val pythonZip = File(baseDir, "libpython.zip.so")

        val ffmpegExclude = setOf(
            "usr/lib/libgio-2.0.so.0.8600.0",
            "usr/lib/libgio-2.0.so.0",
            "usr/lib/libgio-2.0.so",
            "usr/lib/libharfbuzz-subset.so",
            "usr/lib/libbrotlienc.so",
            "usr/lib/libpcre2-16.so",
            "usr/lib/libpcre2-32.so",
            "usr/lib/librubberband-jni.so",
            "usr/lib/libwebpdecoder.so",
            "usr/lib/libtheora.so",
            "usr/lib/libgirepository-2.0.so.0.8600.0",
            "usr/lib/libgirepository-2.0.so.0",
            "usr/lib/libgirepository-2.0.so",
            "usr/lib/libOpenCL.a",
            "usr/lib/libOpenCL.la",
            "usr/lib/libsyn123.so",
            "usr/lib/libout123.so",
            "usr/lib/libharfbuzz-gobject.so",
            "usr/lib/libgnutlsxx.so",
            "usr/lib/libgnutls-dane.so",
            "usr/lib/libgmpxx.so",
            "usr/lib/libharfbuzz-cairo.so",
            "usr/lib/libv4l2rds.so",
            "usr/lib/libfftw3f_threads.so",
            "usr/lib/libfftw3l_threads.so",
            "usr/lib/libfftw3_threads.so",
            "usr/lib/libv4l/ov511-decomp",
            "usr/lib/libv4l/ov518-decomp",
            "usr/lib/glib-2.0/include/glibconfig.h"
        )
        val ffmpegPrefixes = listOf("usr/lib/girepository-1.0/")
        optimizeNativeZip(ffmpegZip, ffmpegExclude, ffmpegPrefixes)

        val pyExclude = setOf(
            "usr/lib/quickjs/libquickjs.a",
            "usr/lib/libreadline.so.8.3",
            "usr/lib/libreadline.so.8",
            "usr/lib/libreadline.so",
            "usr/lib/libncursesw.so.6.5",
            "usr/lib/libncursesw.so.6",
            "usr/lib/libncursesw.so",
            "usr/lib/libncurses.so.6.5",
            "usr/lib/libncurses.so.6",
            "usr/lib/libncurses.so",
            "usr/lib/libcurses.so.6.5",
            "usr/lib/libcurses.so.6",
            "usr/lib/libcurses.so",
            "usr/lib/python3.12/lib-dynload/_curses.cpython-312.so",
            "usr/lib/python3.12/lib-dynload/_curses_panel.cpython-312.so",
            "usr/lib/python3.12/lib-dynload/readline.cpython-312.so",
            "usr/lib/python3.12/lib-dynload/_testcapi.cpython-312.so",
            "usr/lib/python3.12/lib-dynload/_testbuffer.cpython-312.so",
            "usr/lib/python3.12/lib-dynload/_ctypes_test.cpython-312.so",
            "usr/lib/python3.12/lib-dynload/_testclinic.cpython-312.so",
            "usr/lib/python3.12/lib-dynload/_testinternalcapi.cpython-312.so",
            "usr/lib/python3.12/lib-dynload/_testmultiphase.cpython-312.so",
            "usr/lib/python3.12/lib-dynload/_xxtestfuzz.cpython-312.so",
            "usr/lib/python3.12/lib-dynload/_testsinglephase.cpython-312.so",
            "usr/lib/python3.12/lib-dynload/_testimportmultiple.cpython-312.so",
            "usr/lib/python3.12/doctest.py",
            "usr/lib/python3.12/pydoc.py",
            "usr/lib/python3.12/pdb.py",
            "usr/lib/python3.12/nntplib.py",
            "usr/lib/python3.12/imaplib.py",
            "usr/lib/python3.12/smtplib.py",
            "usr/lib/python3.12/poplib.py",
            "usr/lib/python3.12/telnetlib.py",
            "usr/lib/python3.12/ftplib.py",
            "usr/lib/python3.12/mailbox.py",
            "usr/lib/python3.12/tempfile.py.orig"
        )
        val pyPrefixes = listOf(
            "usr/lib/python3.12/curses/",
            "usr/lib/python3.12/lib2to3/",
            "usr/lib/python3.12/venv/",
            "usr/lib/python3.12/wsgiref/"
        )
        optimizeNativeZip(pythonZip, pyExclude, pyPrefixes)
    }
}
