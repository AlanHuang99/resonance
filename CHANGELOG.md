# Changelog

All notable changes to Resonance will be documented in this file.

## [0.5.2] - 2026-06-12

### Changed
- Slimmer top bar and a more compact bottom navigation, leaving more room for content

## [0.5.1] - 2026-06-12

### Fixed
- Pull-to-refresh indicator no longer lingers at the top of Home and Library when idle
- Lyrics screen shows "No lyrics available" instead of a blank page when a track has no lyrics

## [0.5.0] - 2026-06-12

### Added
- Favorite (star) toggle on the album and artist screens
- Artist detail: biography and a similar-artists strip
- Genres tab with a per-genre album grid
- Album library is now a grid with sort options (A–Z, recently added, recently played, most played, random)
- Multi-disc albums are grouped under "Disc N" headers
- Tap a synced lyric line to seek to it
- Pull down to refresh the home feed and the current library tab
- Player gestures: swipe the mini-player up to expand, swipe the full player down to minimize, swipe the album art left/right to change tracks

### Changed
- Search and Settings are now bottom-navigation tabs alongside Home and Library
- The full player opens as a slide-up screen over the bottom bar
- Removed the empty bands at the top and bottom of screens under edge-to-edge layout
- Stored credentials are excluded from cloud backup and device transfer
- F-Droid: omit AGP's DependencyInfoBlock from the APK

## [0.4.0] - 2026-06-12

### Added
- Redesigned home screen with a "jump back in" hero and a shuffle-all action
- New typography using the bundled Space Grotesk and Inter fonts
- New ripple launcher icon with a themed (monochrome) variant
- Redesigned login screen

### Changed
- Rebuilt the UI/playback link on a Media3 `MediaController`; playback position is now a separate flow, so the UI no longer recomposes on every position tick
- Shared, larger image cache and right-sized cover-art requests for faster scrolling
- Release builds now build from source without signing secrets (F-Droid friendly)

### Fixed
- Re-login or server change now takes effect without restarting the app
- Scrobbling now reports plays to the server
- Album art now loads in search results
- Favorite toggle reverts if the server request fails

### Removed
- Local song downloads and the unused on-device cache layer

## [0.3.1] - 2026-04-05

### Changed
- Hardened `PlaybackService` export with `BIND_MEDIA_SESSION_SERVICE`
- Disabled OkHttp HTTP logging in non-debuggable builds
- Refreshed launcher icon foreground

### Fixed
- Reduced playback error loops by pausing after repeated failures
- Improved Compose list performance via better `contentType` hints

### Added
- `signing.properties.example` template for local signing setup

## [0.1.0] - 2025-03-24

### Added
- Initial release
- Subsonic/Navidrome API integration (v1.16.1)
- Music streaming with ExoPlayer / Media3
- Background playback with foreground service and media notifications
- Browse by artists, albums, playlists, and favorites
- Full-text search across artists, albums, and songs
- Full player screen with shuffle, repeat, seek, and favorite controls
- Synced and plain-text lyrics display
- Offline song downloads with progress tracking
- Multi-theme support (Neon Pulse, Aurora, Midnight Ocean, Solar Flare)
- Mini player with playback controls on all screens
- Scrobble reporting
- Gapless playback option
- Cover art loading with caching

### Fixed
- Playback crashes from threading issues (ExoPlayer operations on wrong thread)
- Character encoding issues with non-ASCII usernames and passwords
- ForegroundServiceDidNotStartInTimeException under rapid interactions
- Mini player state lost when app is backgrounded and restored
- Memory leak in position update handler
- ANR risk from blocking calls during dependency injection
