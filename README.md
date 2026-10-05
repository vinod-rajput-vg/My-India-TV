# My India TV

A lightweight Android TV application for browsing and opening Indian TV channel streams by category.

## Overview

**My India TV** is a Kotlin-based Android TV app designed around a simple, remote-friendly channel browser. Channel metadata is bundled with the application as local assets, while playback is handed off to an installed external video/player application.

### Highlights

- Android TV / Leanback launcher support
- D-pad-friendly TV navigation
- Category-based channel browsing
- Five-column channel grid
- Channel artwork bundled with the app
- Data-driven channel catalog stored in `assets/channels/`
- External-player playback using Android `ACTION_VIEW`
- Landscape TV-oriented interface
- GitHub Actions build pipeline
- No Android Studio required for the Gradle command-line build

## Channel Categories

The current catalog is organized into:

- Entertainment
- Infotainment
- News
- Kids
- Drama
- Music
- Movies
- Sports

Channel availability depends on the stream source. Some catalog entries may not currently have a working stream URL.

## Project Structure

```text
My-India-TV/
├── .github/
│   └── workflows/
│       └── build.yml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── channels/
│       │       ├── drama.txt
│       │       ├── entertainment.txt
│       │       ├── infotainment.txt
│       │       ├── kids.txt
│       │       ├── movies.txt
│       │       ├── music.txt
│       │       ├── news.txt
│       │       └── sports.txt
│       ├── java/com/myindiatv/
│       │   ├── Channel.kt
│       │   ├── ChannelData.kt
│       │   └── MainActivity.kt
│       └── res/
│           ├── ch-drawable-nodpi/
│           ├── drawable/
│           └── values/
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── wrapper/
├── settings.gradle.kts
└── README.md
```

## Channel Data Format

Each channel entry is stored as a three-line record:

```text
"Channel Name"
"Stream URL"
"icon.png"
```

The files under `app/src/main/assets/channels/` are parsed at runtime by `ChannelData.kt`.

This makes the channel catalog easy to maintain without changing the main UI implementation.

## Android Configuration

| Component | Version / Setting |
|---|---|
| Language | Kotlin |
| Java | 17 |
| Android Gradle Plugin | 9.4.0 |
| Gradle | 9.6.0 |
| Compile SDK | 36 |
| Target SDK | 36 |
| Minimum SDK | 23 |
| Version | 1.0.0 |
| Package | `com.myindiatv` |

## Build Without Android Studio

The project uses the Gradle Wrapper, so Android Studio is not required.

### Windows PowerShell

```powershell
git clone https://github.com/vinod-rajput-vg/My-India-TV.git
cd My-India-TV

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat clean assembleDebug
```

The generated debug APK is placed under:

```text
app/build/outputs/apk/debug/
```

### Linux / macOS

```bash
git clone https://github.com/vinod-rajput-vg/My-India-TV.git
cd My-India-TV

./gradlew clean assembleDebug
```

Java 17 is recommended for the project configuration.

## Continuous Integration

GitHub Actions builds the project on pushes to the main branches, pull requests, and manual workflow dispatch.

The build workflow:

1. Checks out the repository.
2. Sets up Temurin JDK 17.
3. Restores Gradle caching.
4. Runs a clean debug build.
5. Verifies that the debug APK was produced.
6. Uploads the APK as a workflow artifact.

The repository does not currently publish a GitHub Release, so CI artifacts are the appropriate place to obtain the generated debug APK.

## Playback Model

My India TV does **not** implement a full media player internally.

When a channel is selected, the application sends the stream URL to an installed Android application through an `ACTION_VIEW` intent. If no compatible application is available, the app reports the playback failure instead of embedding a player.

This keeps the application lightweight and separates channel browsing from media playback.

## Known Limitations

- Stream availability is dependent on the original source.
- Some channel records may contain an empty or unavailable stream URL.
- Stream URLs can change or expire.
- Some sources may use HTTP rather than HTTPS.
- Playback depends on an installed compatible external player.
- The current player-selection/settings UI is not a complete persistent player preference system.
- Channel data and icon mappings are currently maintained as bundled application resources/assets.
- There is no built-in stream-health monitoring system.

## Contributing

Contributions are welcome.

Typical contribution areas include:

- Updating channel metadata
- Improving channel categorization
- Adding or correcting channel artwork
- Improving Android TV navigation
- Improving playback handoff
- Improving build automation
- Fixing UI or performance issues

Before submitting changes, build the project locally with:

```bash
./gradlew clean assembleDebug
```

Keep channel data changes limited to sources you are authorized to use.

## Legal & Content Notice

This project is an Android TV channel-browser/player frontend. It does not claim ownership of third-party channel names, logos, broadcasts, or stream sources.

Stream URLs and media content may be provided by third parties and can change independently of this project. Users are responsible for ensuring that their use of any stream, logo, or other content complies with applicable laws, licenses, and the terms of the relevant content provider.

Do not add or redistribute streams that you do not have permission to use.

## License

No open-source license is currently specified for this repository.

Until a license is added by the repository owner, do not assume that the source code or bundled assets are available for unrestricted redistribution or reuse.

## Project Status

**Current status:** Active development

The project currently focuses on a simple, lightweight Android TV channel browsing experience with a data-driven channel catalog and external playback.

---

Built with Kotlin and Gradle for Android TV.
