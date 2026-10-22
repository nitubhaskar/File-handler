# 📺 FileHandler — Android TV File Manager

[![Android TV](https://img.shields.io/badge/Platform-Android%20TV-00E5FF?logo=androidtv&logoColor=black)](https://developer.android.com/tv/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-30%20%28Android%2011%29-10B981)](https://developer.android.com/about/versions/11)
[![Version](https://img.shields.io/badge/Version-v2.3-00E5FF)](https://github.com/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Compose%20for%20TV-38BDF8?logo=jetpackcompose&logoColor=black)](https://developer.android.com/tv/compose)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)

**FileHandler** is a modern, fast, and high-performance two-pane **Android TV File Manager** built from the ground up using **Jetpack Compose for TV**, **Jetpack Media3 ExoPlayer**, and **Kotlin Coroutines**. It is designed specifically for 10-foot TV experiences with 100% remote control (D-Pad) focus navigation.

---

## ✨ Features

- **📺 TV-Optimized Two-Pane Layout:**
  - **Left Pane:** D-Pad scrollable list of files and folders with glowing Cyan focus indicators, GPU scale animations, and category color badges.
  - **Right Pane:** Live preview panel showing file metadata (Name, Size, Modified Date, Read/Write permissions) and quick action TV buttons.

- **📁 Complete File Operations:**
  - Full file browsing, subfolder navigation with **TV Focus Memory** (restores focus onto the exact subfolder when returning to parent directory).
  - Create New Folders (`+ Folder`) and New Files (`+ File`) via TV modal text dialogs.
  - Rename, Copy, Cut (Move), and Paste with automatic **Name Collision / Overwrite Dialogs**.
  - Sort file list by Name (A-Z / Z-A), Date Modified (Newest / Oldest), or Size.

- **🎬 In-App TV Media Viewers & Players:**
  - **Video & Audio Player:** Powered by **Jetpack Media3 ExoPlayer** with TV remote controls (Play/Pause, Seek Forward 10s, Rewind 10s, Seek Bar).
  - **Fullscreen TV Image Viewer:** View photos (`.jpg`, `.png`, `.webp`, `.gif`) with memory-optimized downsampling.
  - **TV Text Reader:** Dark scrollable viewer for `.txt`, `.json`, `.xml`, `.log` files.
  - **External App Launcher:** Launch external apps via `FileProvider` (`content://` URIs).

- **🔌 Storage Source Switcher (USB Drive & SD Card Support):**
  - Detects and allows switching between **Internal Storage** (`/storage/emulated/0`), connected **SD Cards**, and **USB Flash Drives** (`/storage/XXXX-XXXX`).

- **🔒 Android 11 Storage Management (`MANAGE_EXTERNAL_STORAGE`):**
  - All Files Access permissions handling with dedicated TV prompt screens.

- **☁️ Automated Google Drive Update Checker:**
  - Reads binary `versionName` directly from remote `.apk` stream to detect updates automatically without requiring manual Google Drive file renames.

- **🎮 Smart TV Remote Navigation & Controls:**
  - **Smart Back Button Handling:** Navigates up folder levels when inside sub-directories, closes open modal dialogs, and prompts *"Press Back again to exit"* at the root folder.
  - **Stationary D-Pad List Scrolling:** List stays steady on screen while focus moves card by card, scrolling naturally only when reaching the edge.

---

## 🛠️ Tech Stack & Architecture

- **UI Framework:** [Jetpack Compose for TV](https://developer.android.com/tv/compose) (`androidx.tv:tv-material`, `androidx.tv:tv-foundation`)
- **Media Engine:** [Jetpack Media3 ExoPlayer](https://developer.android.com/media/media3) (`androidx.media3:media3-exoplayer`, `androidx.media3:media3-ui`)
- **Language:** 100% Kotlin
- **Architecture:** MVVM with Kotlin `StateFlow` and Coroutines (`Dispatchers.IO` / `Dispatchers.Main`)
- **Min SDK:** `30` (Android 11) | **Target SDK:** `35` (Android 15)
- **Security:** `FileProvider` for secure APK package installation handling

---

## 🎨 Branding & Assets

- **Android TV Launcher Banner (`android:banner`):** Clean 16:9 landscape banner in `drawable-xhdpi/tv_banner.png` (`320×180 px`).
- **App Launcher Icon (`android:icon`):** 1:1 Adaptive Icon with `72×72 dp` safe zone padding in `mipmap-xhdpi/ic_launcher.png` (`512×512 px`).

---

## 🚀 Build & Deployment

### 1. Build Final Release APK
Open the project in Android Studio and run:
```bash
./gradlew assembleDebug
```
The generated release APK is available at:
`app/build/outputs/apk/debug/FileHandler-v2.3.apk`

### 2. Deploy to Android TV
```bash
adb connect <YOUR_TV_IP_ADDRESS>
adb install app/build/outputs/apk/debug/FileHandler-v2.3.apk
```

---

## 📄 License
This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
