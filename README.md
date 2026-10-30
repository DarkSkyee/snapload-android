# SnapLoad 🎬📥
### Modern, Lightning-Fast Video & Audio Downloader for Android (100% On-Device)

[![Release](https://img.shields.io/github/v/release/DarkSkyee/snapload-android?style=for-the-badge&color=6366F1&label=Release&logo=github)](https://github.com/DarkSkyee/snapload-android/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/DarkSkyee/snapload-android/total?style=for-the-badge&color=10B981&label=Downloads&logo=github)](https://github.com/DarkSkyee/snapload-android/releases)
[![Stars](https://img.shields.io/github/stars/DarkSkyee/snapload-android?style=for-the-badge&color=F59E0B&label=Stars&logo=github)](https://github.com/DarkSkyee/snapload-android/stargazers)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20%28API%2024%2B%29-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![yt-dlp](https://img.shields.io/badge/Core-yt--dlp%20Engine-FF0000?style=for-the-badge&logo=youtube&logoColor=white)](https://github.com/yt-dlp/yt-dlp)
[![License](https://img.shields.io/badge/License-Non--Commercial%20(Attribution)-orange.svg?style=for-the-badge)](LICENSE)

<br/>

<p align="center">
  <img src="docs/assets/logo.svg" width="160" height="160" alt="SnapLoad Logo" />
</p>

<p align="center">
  <a href="https://github.com/DarkSkyee/snapload-android/releases/latest">
    <img src="https://img.shields.io/badge/%E2%AC%87%EF%B8%8F%20Download-Latest%20Release%20APK-6366F1?style=for-the-badge&logo=android&logoColor=white" height="42" alt="Download Latest Release APK" />
  </a>
</p>

---

## 🌟 Highlights

**SnapLoad** is an ultra-fast, privacy-first multimedia downloader application engineered for Android. Unlike web-based converters or remote cloud services, **SnapLoad runs 100% on-device** using local Python/yt-dlp binaries and an on-demand FFmpeg multimedia engine.

Your links, files, and queries never leave your device. Downloaded media is immediately indexed and saved directly into the official Android system galleries:
- 🎬 **Videos** &rarr; `Movies/SnapLoad` (Instantly viewable in Google Photos, Gallery & Players)
- 🎵 **Audio Tracks** &rarr; `Music/SnapLoad` (Instantly recognized by Spotify, Samsung Music & Media Players)

---

## ✨ Features (v3.0+)

- 🪶 **Ultra-Lightweight (~21 MB base APK)**:
  - Base APK shrunk by **>60%** (from ~56 MB down to **~21 MB**), packaging lightning-fast execution binaries and modular on-demand engine loading.
- 🎧 **Spotify & YouTube Music Support**:
  - Full native support for Spotify tracks, albums, and playlists as well as YouTube Music.
  - Automatically fetches metadata and matches high-fidelity audio streams.
- 📋 **Full Playlist & Album Downloads**:
  - Download entire playlists with a single tap.
  - Clean, live visual progress list showing per-track status, ETA, and success counts.
- 🔄 **Smart Audio Fallback**:
  - Selected MP4 for an audio-only platform (like Spotify)? SnapLoad automatically falls back to crystal-clear 320 kbps MP3 and notifies you seamlessly.
- ⚡ **Instant Share Sheet & Clipboard Integration**:
  - One-tap download directly from YouTube, Shorts, TikTok, Spotify, and YouTube Music via the system **"Share to..."** menu.
  - Automatic URL detection and clipboard extraction.
- 🎬 **Multi-Format Video Engine**:
  - **MP4** *(Default & Universal)*, **MKV**, and **WebM**.
  - Resolutions: **Best / Original (Up to 4K)**, **1080p Full HD**, **720p HD**, **480p SD**.
- 🎵 **Studio-Grade Audio Extraction**:
  - **MP3** *(Default 320 kbps)*, **M4A (AAC)**, **FLAC (Lossless)**, **WAV**, and **OPUS**.
  - Bitrates: **320 kbps**, **256 kbps**, **192 kbps**, and **128 kbps**.
- 🔄 **Autonomous Auto-Updates**:
  - **yt-dlp Core Engine**: Automatic background updates every 24 hours to stay resilient against platform API changes.
  - **In-App GitHub Releases Updater**: Real-time checking and one-tap APK installation directly from GitHub Releases.
- 🌐 **Multilingual Experience (i18n)**:
  - English *(Default)* and Polish.
  - Instant on-the-fly language switching without restarting the app.
- 🎨 **Minimalist & Refined Animations**:
  - Polished, subtle micro-interactions (*refined spring damping*).
  - Smooth animated progress transitions without excessive flashiness.

---

## 📲 How to Install

1. Navigate to the **[Latest Releases](https://github.com/DarkSkyee/snapload-android/releases/latest)** page (or click the download button above).
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

This project is licensed under the **MIT Non-Commercial License (MIT-NC) with Attribution**:
- ✅ **Allowed**: You are completely free to use, copy, modify, fork, and share this application for personal and educational purposes.
- ❌ **Commercial Prohibition**: You **may NOT** sell, resell, or monetize this software, its source code, or any derivative works in any way.
- ❌ **Attribution Required**: You **may NOT** remove the original author's name or claim authorship. All copies and forks must prominently retain attribution to the original creator, **DarkSkyee**.

See the full [LICENSE](LICENSE) file for complete legal terms.
