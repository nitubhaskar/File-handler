# 📺 FileHandler — Android TV File Manager

[![Android TV](https://img.shields.io/badge/Platform-Android%20TV-00E5FF?logo=androidtv&logoColor=black)](https://developer.android.com/tv/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-30%20%28Android%2011%29-10B981)](https://developer.android.com/about/versions/11)
[![Jetpack Compose](https://img.shields.io/badge/UI-Compose%20for%20TV-38BDF8?logo=jetpackcompose&logoColor=black)](https://developer.android.com/tv/compose)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)

**FileHandler** is a modern, fast, and high-performance two-pane **Android TV File Manager** built from the ground up using **Jetpack Compose for TV** and **Kotlin Coroutines**. It is designed specifically for 10-foot TV experiences with 100% remote control (D-Pad) focus navigation.

---

## ✨ Features

- **📺 TV-Optimized Two-Pane Layout:**
  - **Left Pane:** D-Pad scrollable list of files and folders with glowing Cyan focus indicators and category color badges.
  - **Right Pane:** Live preview panel showing file metadata (Name, Size, Modified Date, Read/Write permissions) and quick TV action buttons.

- **📁 File & Directory Management:**
  - Full file browsing, subfolder navigation, and breadcrumb path header.
  - Directory actions menu (Info, Delete, Confirmation safety dialogs).
  - Categorized media icons for Folders, Videos, Images, Audio, and Documents.

- **🔌 Storage Source Switcher (USB & SD Card Support):**
  - Detects and allows switching between **Internal Storage** (`/storage/emulated/0`), connected **SD Cards**, and **USB Flash Drives** (`/storage/XXXX-XXXX`).
  - Displays storage capacity statistics (Free space vs. Total space) with an active volume indicator.

- **🔒 Android 11 Storage Management (`MANAGE_EXTERNAL_STORAGE`):**
  - Seamless handling of Android 11+ All Files Access permissions with dedicated TV prompt screens.

- **☁️ In-App Google Drive Update Checker:**
  - Connects to Google Drive to check for app updates.
  - High-performance **64 KB network streaming buffer** for fast downloads on TV hardware.
  - Modal TV progress dialog showing live 0–100% download progress and direct Package Installer launching.

- **🎮 Smart TV Remote Navigation & Controls:**
  - **Smart Back Button Handling:** Navigates up folder levels when inside sub-directories, closes open modal dialogs, and prompts *"Press Back again to exit"* at the root folder.
  - **Automatic Focus Trapping:** Modal dialogs automatically capture D-Pad focus and isolate navigation from background windows.

---

## 🛠️ Tech Stack & Architecture

- **UI Framework:** [Jetpack Compose for TV](https://developer.android.com/tv/compose) (`androidx.tv:tv-material`, `androidx.tv:tv-foundation`)
- **Language:** 100% Kotlin
- **Architecture:** MVVM with Kotlin `StateFlow` and Coroutines (`Dispatchers.IO` / `Dispatchers.Main`)
- **Min SDK:** `30` (Android 11) | **Target SDK:** `35` (Android 15)
- **Security:** `FileProvider` for secure APK package installation handling

---

## 🎨 Branding & Assets

- **Android TV Launcher Banner (`android:banner`):** Clean 16:9 landscape banner in `drawable-xhdpi/tv_banner.png` (`320×180 px`).
- **App Launcher Icon (`android:icon`):** 1:1 Adaptive Icon with `72×72 dp` safe zone padding in `mipmap-xhdpi/ic_launcher.png` (`512×512 px`).

---

## 🚀 Getting Started

### 1. Clone the Repository
```bash
git clone https://github.com/YOUR_USERNAME/FileHandler-AndroidTV.git
cd FileHandler-AndroidTV
```

### 2. Build Debug APK
Open the project in Android Studio and run:
```bash
./gradlew assembleDebug
```
The generated APK will automatically be named with the version number:
`app/build/outputs/apk/debug/FileHandler-v1.9.apk`

### 3. Deploy to Android TV Device or Emulator
```bash
adb connect <YOUR_TV_IP_ADDRESS>
adb install app/build/outputs/apk/debug/FileHandler-v1.9.apk
```

---

## 📄 License
This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
