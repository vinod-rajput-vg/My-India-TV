<div align="center">

# 📺 My India TV

### A modern, lightweight Android TV channel browser

Browse channels by category, navigate with your TV remote, and hand off playback to your preferred installed player.

<br>

[![Android TV](https://img.shields.io/badge/Android%20TV-Ready-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/tv/)
[![Kotlin](https://img.shields.io/badge/Kotlin-17-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.6-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![SDK](https://img.shields.io/badge/SDK-23%20%E2%86%92%2036-4285F4?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)

<br><br>

**Simple. Fast. TV-focused.**

</div>

---

## ✨ What is My India TV?

**My India TV** is a Kotlin-based Android TV application built for a clean, remote-friendly channel browsing experience.

The app keeps channel metadata and artwork inside the project, organizes channels into categories, and launches compatible installed players through Android's \`ACTION_VIEW\` intent.

> Built for Android TV — designed around the D-pad, large-screen navigation, and a lightweight UI.

---

## 🚀 Features

| | Feature | Description |
|---|---|---|
| 🎮 | **TV Remote Ready** | D-pad navigation designed for Android TV |
| 📂 | **Categories** | Browse channels by genre |
| 🖼️ | **Channel Artwork** | Bundled channel icons for a polished grid |
| ⚡ | **Lightweight** | Simple custom Canvas/Paint-based interface |
| ▶️ | **External Playback** | Opens streams using compatible installed players |
| 📦 | **Data Driven** | Channel lists are maintained in local asset files |
| 🖥️ | **TV Optimized** | Landscape-first interface with a 5-column grid |
| 🤖 | **CI Ready** | Automated debug builds through GitHub Actions |

---

## 📡 Channel Categories

The current catalog is organized into:

**Entertainment · Infotainment · News · Kids · Drama · Music · Movies · Sports**

Channel availability depends on the underlying stream source. Some catalog entries may not currently contain a working stream URL.

---

## 🧩 Architecture

~~~text
┌─────────────────────────────────────────────┐
│                 My India TV                 │
├─────────────────────────────────────────────┤
│                                             │
│   Android TV UI                             │
│        │                                    │
│        ▼                                    │
│   Category Browser                          │
│        │                                    │
│        ▼                                    │
│   Channel Grid                              │
│        │                                    │
│        ▼                                    │
│   ChannelData.kt                            │
│        │                                    │
│        ▼                                    │
│   assets/channels/*.txt                     │
│        │                                    │
│        ▼                                    │
│   ACTION_VIEW ──► Installed Player          │
│                                             │
└─────────────────────────────────────────────┘
~~~

The application intentionally keeps playback outside the core UI. This keeps the app small and lets the installed player handle media playback.

---

## 📁 Project Structure

~~~text
My-India-TV/
│
├── .github/
│   └── workflows/
│       └── build.yml
│
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       │
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
│       │
│       ├── java/com/myindiatv/
│       │   ├── Channel.kt
│       │   ├── ChannelData.kt
│       │   └── MainActivity.kt
│       │
│       └── res/
│           ├── ch-drawable-nodpi/
│           ├── drawable/
│           └── values/
│
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── wrapper/
├── settings.gradle.kts
└── README.md
~~~

---

## 📝 Channel Data

Adding or updating a channel uses a simple three-line format:

~~~text
"Channel Name"
"Stream URL"
"icon.png"
~~~

Channel lists live under:

~~~text
app/src/main/assets/channels/
~~~

The catalog is loaded by \`ChannelData.kt\`, keeping channel content separate from the main application logic.

---

## 🛠️ Tech Stack

| Technology | Configuration |
|---|---|
| **Language** | Kotlin |
| **Java** | 17 |
| **Android Gradle Plugin** | 9.4.0 |
| **Gradle** | 9.6.0 |
| **Compile SDK** | 36 |
| **Target SDK** | 36 |
| **Minimum SDK** | 23 |
| **App Version** | 1.0.0 |
| **Package** | \`com.myindiatv\` |
| **UI** | Custom Canvas / Paint |
| **Playback** | Android \`ACTION_VIEW\` |

---

## ⚙️ Build Without Android Studio

You only need Git and a compatible JDK. The repository includes the Gradle Wrapper.

### Windows

~~~powershell
git clone https://github.com/vinod-rajput-vg/My-India-TV.git
cd My-India-TV

.\gradlew.bat clean assembleDebug
~~~

### Linux / macOS

~~~bash
git clone https://github.com/vinod-rajput-vg/My-India-TV.git
cd My-India-TV

./gradlew clean assembleDebug
~~~

### Output

~~~text
app/build/outputs/apk/debug/
~~~

**Java 17** is the project toolchain.

---

## 🤖 GitHub Actions

The repository includes a CI workflow that:

- Builds on pushes to the main branches
- Builds pull requests
- Supports manual workflow dispatch
- Uses Temurin JDK 17
- Uses Gradle caching
- Runs a clean debug build
- Verifies the APK
- Uploads the generated APK as a workflow artifact

> The repository currently does not publish a GitHub Release. CI artifacts are therefore the available build output.

---

## ▶️ Playback

My India TV is a **channel browser**, not a built-in media player.

When a channel is selected, the app sends its stream URL to Android using:

~~~kotlin
Intent.ACTION_VIEW
~~~

A compatible installed player is then responsible for playback.

If Android cannot find a suitable application, playback fails gracefully instead of embedding a player inside My India TV.

---

## ⚠️ Current Limitations

- Stream availability depends on third-party sources.
- Some entries may have empty or unavailable stream URLs.
- Stream URLs can change, expire, or become unavailable.
- Some sources currently use HTTP rather than HTTPS.
- Playback requires a compatible installed external player.
- Player-selection settings are not yet a complete persistent preference system.
- Channel data and icon mappings are bundled with the application.
- There is currently no built-in stream-health monitoring.

---

## 🗺️ Roadmap

Possible future improvements:

- [ ] Built-in player support
- [ ] Persistent default-player selection
- [ ] Stream availability / health checks
- [ ] More channel categories
- [ ] Search and favorites
- [ ] Improved channel-data management
- [ ] More robust artwork mapping
- [ ] Release APK distribution
- [ ] Additional Android TV UI polish

---

## 🤝 Contributing

Contributions, fixes, and improvements are welcome.

Good areas for contribution include:

- Channel metadata
- Categorization
- Channel artwork
- Android TV navigation
- Playback handoff
- UI and performance
- Build automation

Before submitting changes:

~~~bash
./gradlew clean assembleDebug
~~~

Only add channel data, artwork, or streams that you are authorized to use.

---

## ⚖️ Legal & Content Notice

My India TV is an Android TV frontend for browsing channel metadata and handing stream URLs to installed playback applications.

The project does **not** claim ownership of third-party channel names, logos, broadcasts, or stream sources.

Third-party stream URLs and media may change independently of this project. Users are responsible for complying with applicable laws, licenses, copyrights, and the terms of the relevant content provider.

**Do not add or redistribute streams or media that you do not have permission to use.**

---

## 📄 License

**No open-source license is currently specified.**

Until the repository owner adds a license, the source code and bundled assets should **not** be assumed to be available for unrestricted redistribution or reuse.

---

<div align="center">

### Built for Android TV with Kotlin + Gradle

**My India TV** · Lightweight · Remote-friendly · Data-driven

</div>
