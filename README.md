# Tensei

An anime &amp; manga streaming app for Android with a 5-tab, cinema-inspired interface.

![Platform](https://img.shields.io/badge/Platform-Android-green.svg)
![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![MinSDK](https://img.shields.io/badge/MinSDK-26-orange.svg)

## Features

- **Multi-provider anime &amp; manga**: Browse, search, and stream from AniList, MyAnimeList (MAL), TMDB, Jikan, and MangaDex, backed by a full extension (Tachiyomi-compatible) catalogue system.
- **5-tab navigation**: Schedule, Anime, Home, Manga, and Search — with a cinematic vertical-scroll item animation shared across the schedule, status list, and profile favorites/history.
- **Streaming player**: Media3/ExoPlayer engine with HLS support, multiple server/extension sources, live proxy server, and skip-opening/intro detection.
- **Torrent &amp; magnet streaming**: Built-in libtorrent4j engine with a local streaming server for magnet links.
- **Full offline experience**: Download episodes and manga chapters for reading offline, video cache, and a comprehensive cache manager.
- **Manga reader**: Mihon/Tachiyomi-based reader with horizontal pager and continuous scroll modes, zoomable images, resume progress, and chapter tracking.
- **Tracking &amp; lists**: Status lists (watching/completed/planning/etc.) for both anime and manga, with cross-provider sync between AniList and MAL (including startup and diff sync).
- **Profile, favorites &amp; history**: Track favorites and watch/read history for both anime and manga with the cinematic item animation.
- **Airing schedule widget**: Glance app-widget showing the upcoming airing schedule on the home screen.
- **Rich presence**: Discord integration via the native SDK.
- **In-app updates**: Checks GitHub Releases for new APK builds and downloads/installs them in-app.
- **Subtitles**: Fully stylable subtitle settings — font size, color, outline, shadow — plus embedded subtitle extraction.
- **Themes &amp; preferences**: Light/dark theme, configurable startup tab, and per-tab app icon theming.
- **Extensions browser**: Browse, install, and manage community catalogue extensions directly in-app.

## Requirements

- Android 8.0+ (API 26+)
- arm64-v8a or armeabi-v7a (separate ABI APKs are published)

## Installation

Download the APK from [Releases](https://github.com/TheBlissless/tensei/releases) and install.

## Tech Stack

- **Language**: Kotlin (with native C++ via CMake for the torrent engine)
- **UI**: Jetpack Compose (Material 3, BOM), Compose Foundation, Material Icons Extended, Glance app-widgets
- **Networking**: OkHttp, Retrofit + Gson converter, kotlinx.serialization (JSON + okio), Jsoup for HTML parsing
- **Concurrency &amp; streams**: Kotlin Coroutines, RxJava
- **Media**: AndroidX Media3 (ExoPlayer, UI, session, HLS, OkHttp datasource)
- **Streaming/torrents**: libtorrent4j (core + ARM ABIs)
- **Image loading**: Coil
- **Persistence**: DataStore Preferences, WorkManager
- **APIs**: AniList GraphQL, MyAnimeList (MAL OAuth), TMDB, Jikan, MangaDex, AnimeThemes, AnimeSkip
- **Integration**: Discord Partner SDK
- **Other**: AndroidX Core KTX, Lifecycle (runtime/compose/viewmodel), SplashScreen, Preferences KTX, JUnit/Mockito/Truth for testing

## Disclaimer

This project is for educational purposes only. It does not host, store, or distribute any copyrighted content. Users are solely responsible for compliance with applicable laws in their jurisdiction. All third-party APIs and services used are independent and not affiliated with this project.
