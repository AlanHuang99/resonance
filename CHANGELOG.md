# Changelog

All notable changes to Resonance will be documented in this file.

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
