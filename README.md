# Tensei

A modern anime list manager and tracker for Android.

![Platform](https://img.shields.io/badge/Platform-Android-green.svg)
![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![MinSDK](https://img.shields.io/badge/MinSDK-26-orange.svg)

## Screenshots

| Schedule | Explore | Home | Widget |
|----------|---------|------|--------|
| ![Schedule](screenshots/airing.png) | ![Explore](screenshots/explore.png) | ![Home](screenshots/home.png) | ![Widget](screenshots/widget.png) |

## Features

- **AniList and MyAnimeList Integration** - Login to sync your anime list
- **Progress Tracking** - Automatically sync watch progress
- **Explore** - Browse trending, seasonal, and top-rated anime
- **Extension System** - Optional plugin architecture for third-party content sources
- **Video Player** - Opening and ending skip buttons, quality selection, resize button

## Requirements

- Android 8.0+ (API 26+)

## Installation

Download the APK from [Releases](https://github.com/YOUR_USERNAME/tensei/releases) and install.

## Tech Stack

- **Kotlin + Jetpack Compose** - UI framework
- **Media3 ExoPlayer** - Video playback
- **AniList GraphQL API** - Anime list syncing, metadata, user data
- **TMDB API** - Episode metadata (titles, descriptions, thumbnails)
- **Jikan API (v4)** - MyAnimeList favorites and history sync for MAL users
- **MVVM Architecture** - ViewModel + StateFlow pattern
- **OkHttp + kotlinx.serialization** - HTTP client and JSON parsing
- **Coil** - Image loading and caching

## Forking the repository

`local.properties` file with the following keys needed:

CLIENT_ID_ANILIST  
TMDB_API_KEY  
MAL_CLIENT_ID  

## Disclaimer

This project is for educational purposes only. It does not host, store, or distribute any copyrighted content. Users are solely responsible for compliance with applicable laws in their jurisdiction. All third-party APIs and services used are independent and not affiliated with this project.
