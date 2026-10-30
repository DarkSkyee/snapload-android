# SnapLoad 🎬📥
### Modern, Lightning-Fast Video & Audio Downloader for Android (100% On-Device)

[![Release](https://img.shields.io/github/v/release/DarkSkyee/snapload-android?style=for-the-badge&color=6366F1&label=Release&logo=github)](https://github.com/DarkSkyee/snapload-android/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/DarkSkyee/snapload-android/total?style=for-the-badge&color=10B981&label=Downloads&logo=github)](https://github.com/DarkSkyee/snapload-android/releases)
[![Stars](https://img.shields.io/github/stars/DarkSkyee/snapload-android?style=for-the-badge&color=F59E0B&label=Stars&logo=github)](https://github.com/DarkSkyee/snapload-android/stargazers)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20%28API%2024%2B%29-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![yt-dlp](https://img.shields.io/badge/Core-yt--dlp%20Engine-FF0000?style=for-the-badge&logo=youtube&logoColor=white)](https://github.com/yt-dlp/yt-dlp)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

<br/>

<p align="center">
  <img src="docs/assets/logo.svg" width="160" height="160" alt="SnapLoad Logo" />
</p>

---

## 🌟 Highlights

**SnapLoad** is an ultra-fast, privacy-first multimedia downloader application engineered for Android. Unlike web-based converters or remote cloud services, **SnapLoad runs 100% on-device** using local Python/yt-dlp binaries and an embedded FFmpeg multimedia engine.

Your links, files, and queries never leave your device. Downloaded media is immediately indexed and saved directly into the official Android system galleries:
- 🎬 **Videos** &rarr; `Movies/SnapLoad` (Instantly viewable in Google Photos, Gallery & Players)
- 🎵 **Audio Tracks** &rarr; `Music/SnapLoad` (Instantly recognized by Spotify, Samsung Music & Media Players)

---

## ✨ Features

- ⚡ **Instant Share Sheet & Clipboard Integration**:
  - One-tap download directly from YouTube, Shorts, and TikTok via the system **"Share to..."** menu.
  - Automatic URL detection and clipboard extraction.
- 🎬 **Multi-Format Video Engine**:
  - **MP4** *(Default & Universal)*, **MKV**, and **WebM**.
  - Resolutions: **Best / Original (Up to 4K)**, **1080p Full HD**, **720p HD**, **480p SD**.
- 🎵 **Studio-Grade Audio Extraction**:
  - **MP3** *(Default 320 kbps)*, **M4A (AAC)**, **FLAC (Lossless)**, **WAV**, and **OPUS**.
  - Bitrates: **320 kbps**, **256 kbps**, **192 kbps**, and **128 kbps**.
- 🔄 **Autonomous Auto-Updates**:
  - **yt-dlp Core Engine**: Automatic background updates every 24 hours to stay resilient against YouTube/TikTok API changes.
  - **In-App GitHub Releases Updater**: Real-time checking and one-tap APK installation directly from GitHub Releases.
- 🌐 **Multilingual Experience (i18n)**:
  - English *(Default)* and Polish.
  - Instant on-the-fly language switching without restarting the app.
- 🎨 **Material 3 Expressive UI**:
  - Smooth spring micro-interactions (*bouncy scale on press*).
  - Animated bottom sheet settings modal.
  - Live progress monitoring with real-time download speeds, ETA, and FFmpeg merger status indicators.

---

## 📲 How to Install

1. Navigate to the **[Latest Releases](https://github.com/DarkSkyee/snapload-android/releases/latest)** page.
2. Download the latest `SnapLoad-v*.apk` package.
3. Open the downloaded APK on your Android phone.
4. If prompted, allow **"Install unknown apps"** for your browser or file manager.
5. Launch **SnapLoad** and start downloading!

---

## 🏗️ Architecture & Technology Stack

- **UI Layer**: Jetpack Compose, Material 3, Coroutine StateFlows, Coil for thumbnail caching.
- **Service Layer**: Foreground Service (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`) with persistent notification and cancelation hooks.
- **Core Engine**: Embedded Python runtime (`youtubedl-android`) + FFmpeg binary distribution.
- **Storage Layer**: Android 10+ Scoped Storage & MediaStore API (`MediaStore.Video` and `MediaStore.Audio`) with zero invasive permissions required.
- **Updater**: GitHub Releases API consumer with Android `FileProvider` package installer integration.

---

## 🛠️ Building from Source

### Prerequisites
- JDK 17 (Recommended: Eclipse Adoptium OpenJDK 17)
- Android SDK API 36 (Build Tools 36.0.0, NDK r25+)

### Commands

```powershell
# Clone repository
git clone https://github.com/DarkSkyee/snapload-android.git
cd snapload-android

# Run Unit Tests
.\gradlew testDebugUnitTest

# Assemble Release APK
.\gradlew assembleRelease

# Output APK is located at:
# app/build/outputs/apk/release/app-release.apk
```

---

## 📄 License

Distributed under the **MIT License**. Created with ❤️ by **DarkSkyee**.
