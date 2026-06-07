# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A simple Android MP3 player application for meditation music ("冥想播放器"). The app scans a "播放文件" (Play Files) directory for MP3 files organized in subdirectories, allowing continuous playback with an optional sleep timer.

**Language:** Java (not Kotlin)
**Package:** `com.mymeditation.player`
**Min SDK:** 24, **Target SDK:** 34

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug APK to connected device
./gradlew installDebug

# Clean build
./gradlew clean
```

Output APK location: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

### Service-Based Audio Playback

The app uses a **foreground service** (`MusicService`) for audio playback to ensure music continues playing even when the user navigates away from the app. The service:

- Runs as a foreground service with `mediaPlayback` type
- Uses `MediaPlayer` for audio playback
- Manages playlist, state (playing/paused/stopped), and auto-advances to next track
- Maintains a persistent notification with play/pause/stop controls
- Communicates with the Activity via callbacks (progress updates, timer updates)

### Activity-Service Binding Pattern

`MainActivity` binds to `MusicService` using `bindService()` with `BIND_AUTO_CREATE`. Key patterns:

- `LocalBinder` pattern allows Activity to access Service methods directly
- `ProgressCallback` and `TimerCallback` interfaces update UI from Service
- Service callbacks run on UI thread via `runOnUiThread()`

### Theme System

`ThemeManager` is a singleton that manages 5 color themes:

- Ocean Blue (default)
- Forest Green
- Sunset Orange
- Lavender Purple
- Deep Blue

Theme preference is persisted via `SharedPreferences`. Theme changes dynamically update all UI elements (header, player controls, buttons, progress bar, status bar).

### File Discovery

The app searches for the "播放文件" directory in multiple locations (in order):
1. `/sdcard/播放文件` (most common)
2. `/storage/emulated/0/播放文件`
3. App external storage directory
4. App internal storage directory

Subdirectories containing MP3 files are displayed in a `RecyclerView`. Clicking a directory loads its MP3 files into a second `RecyclerView`. Individual files can be selected to play from that position.

### Key Components

| Class | Responsibility |
|-------|---------------|
| `MainActivity` | UI, directory/file browsing, service binding, theme switching |
| `MusicService` | Audio playback, playlist management, timer, foreground notification |
| `ThemeManager` | Theme color definitions and persistence |
| `DirectoryAdapter` | RecyclerView adapter for directory list |
| `FileAdapter` | RecyclerView adapter for file list with selection highlighting |
| `DirectoryItem` | Data model for directories (name, path, MP3 count) |
| `FileItem` | Data model for files (name, path, size) |

## Permissions

- **Android 13+**: `READ_MEDIA_AUDIO`
- **Android 12 and below**: `READ_EXTERNAL_STORAGE`
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` for the music service

## Pushing Test Files to Device

```bash
# Push the entire "播放文件" directory to device
adb push "播放文件" /sdcard/

# Or manually place files at /sdcard/播放文件/ on the device
```

## Dependencies

- `androidx.appcompat:appcompat:1.6.1`
- `com.google.android.material:material:1.11.0`
- `androidx.constraintlayout:constraintlayout:2.1.4`
- `androidx.recyclerview:recyclerview:1.3.2`
- `androidx.media:media:1.7.0`
