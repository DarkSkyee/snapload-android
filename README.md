# Media Downloader (Android) 🎬🎵

Aplikacja mobilna na system Android umożliwiająca pobieranie multimediów z platform **YouTube / YouTube Shorts** oraz **TikTok** bezpośrednio na urządzeniu (**100% On-Device**), bez zewnętrznych serwerów proxy.

## ✨ Główne Funkcje

- **Wklejanie lub automatyczne udostępnianie (Share Sheet)**:
  - Wklejanie linku bezpośrednio w aplikacji (z przyciskiem "Wklej ze schowka").
  - Bezpośrednia integracja z systemowym menu **Udostępnij** w YouTube Shorts i TikToku – aplikacja automatycznie przechwytuje link, oczyszcza go z opisów i parametrów śledzących oraz pobiera podgląd.
- **Wybór formatu**:
  - **MP4 (Wideo HD)**: Automatyczne łączenie (muxing) strumienia wideo i audio w najwyższej jakości za pomocą wbudowanego silnika **FFmpeg**.
  - **MP3 (Tylko dźwięk)**: Ekstrakcja ścieżki dźwiękowej i konwersja do MP3 (VBR jakość 0).
- **Automatyczny zapis do Galerii systemowej**:
  - Wideo MP4 trafia bezpośrednio do `Movies/Downloader` w systemowej **Galerii / Rolce aparatu** (`MediaStore.Video`).
  - Audio MP3 trafia bezpośrednio do `Music/Downloader` w bibliotece muzycznej (`MediaStore.Audio`).
- **Działanie w tle (Foreground Service)**:
  - Długie pobieranie i konwersja działają nieprzerwanie w tle z powiadomieniem pokazującym procent postępu, prędkość (MB/s) oraz czas do ukończenia (ETA).
- **Aktualizacje silnika w locie (OTA)**:
  - Możliwość zaktualizowania rdzenia `yt-dlp` jednym kliknięciem (przycisk odświeżania na górnym pasku), co chroni przed zmianami w API YouTube.

---

## 📱 Jak zainstalować APK na telefonie

1. Przejdź do zakładki [Releases](../../releases).
2. Pobierz najnowszy plik `MediaDownloader-release.apk` (lub `app-release.apk`).
3. Na telefonie z Androidem otwórz pobrany plik.
4. Jeśli pojawi się monit o "Zezwolenie na instalowanie aplikacji z tego źródła", włącz tę opcję w ustawieniach.
5. Kliknij **Zainstaluj** i ciesz się aplikacją!

---

## 🛠️ Architektura Techniczna

- **Język i UI**: Kotlin 2.3 + Jetpack Compose (Material 3).
- **Silnik ekstrakcji**: `youtubedl-android` (opakowany `yt-dlp` + `FFmpeg` + `Python`).
- **Scoped Storage**: Android `MediaStore` API (`IS_PENDING` workflow, kompatybilność z Android 10 - 16+).
- **Usługa w tle**: Android `ForegroundService` (`dataSync`).
- **Obrazy**: Coil Compose.
- **Wieloplatformowość procesorów**: Wsparcie dla architektur `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`.
