# Resonance

A modern, open-source Android music player for [Navidrome](https://www.navidrome.org/) and other [Subsonic](http://www.subsonic.org/)-compatible servers.

Built with Jetpack Compose and Material Design 3 for a smooth, native Android experience.

## Features

- **Stream your library** -- Browse and play music from any Subsonic/Navidrome server
- **Full playback controls** -- Play, pause, skip, seek, shuffle, repeat, queue management
- **Background playback** -- Foreground service with media session and notification controls
- **Lyrics** -- Synced and plain-text lyrics display with auto-scroll
- **Search** -- Full-text search across artists, albums, and songs
- **Offline downloads** -- Cache songs locally for offline listening
- **Favorites** -- Star artists, albums, and songs
- **Themes** -- Multiple color themes (Neon Pulse, Aurora, Midnight Ocean, Solar Flare)
- **Scrobbling** -- Report played tracks back to the server

## Screenshots

<!-- TODO: Add screenshots -->

## Download

Grab the latest APK from the [Releases](../../releases) page, or build from source.

## Building

**Requirements:** JDK 17, Android SDK 34

```bash
git clone https://github.com/your-username/resonance.git
cd resonance
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM, Hilt DI |
| Playback | Media3 / ExoPlayer |
| Networking | Retrofit, OkHttp |
| Database | Room |
| Images | Coil |
| Async | Kotlin Coroutines + Flow |

## Server Compatibility

Tested with:
- [Navidrome](https://www.navidrome.org/) 0.50+
- Any server implementing the [Subsonic API](http://www.subsonic.org/pages/api.jsp) v1.16.1

## Requirements

- Android 12 (API 31) or higher

## Contributing

Contributions are welcome! Please open an issue first to discuss what you'd like to change.

1. Fork the repo
2. Create your branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the GNU General Public License v3.0 -- see the [LICENSE](LICENSE) file for details.
