# Changelog

All notable changes to Resonance will be documented in this file.

## [Unreleased]

### Added

- Casting to DLNA/UPnP renderers and Google Cast devices (Chromecast, Google Home, Cast groups) on the LAN, with a cast sheet in the player, device discovery, and playback driven through the phone player.
- Speaker volume follows the phone's media volume keys while casting, over UPnP RenderingControl or the Cast receiver's volume.
- The car gets the app's shape instead of a flat list: Home shelves (Shuffle, Recently played, New, Often played, Random) and a Library folder with Artists, Albums, Genres, Playlists and Favorite songs, laid out with the car's own style hints.

### Fixed

- Reconnect a device that drops off the network, quits the receiver, or comes back up empty, for both Cast and DLNA, resuming at the phone player's position instead of going silent; a lost device now shows a message instead of failing quietly.
- Hand the device the following track so it advances by itself: the speaker's own next button works and there is no silence between tracks.
- Follow the device when it skips on its own, so the phone's queue, notification and Android Auto stay on the track that is actually playing.
- End casting when playback is stopped on the device itself, handing audio back to the phone instead of resurrecting the cast.
- Mirror volume changes made on the device back to the phone, so its keys do not fight the speaker.
- Repeat one keeps playing through the speaker instead of falling silent after the first play, and a reordered or trimmed queue no longer leaves the device playing a stale next track.
- Keep casting through a slow or unreliable network: a device that stops answering, drops the connection, comes back empty or is taken over by another app is detected within seconds instead of failing quietly.
- Hold a Cast load until the receiver is ready, refuse it when the device rejects the stream, and surface that to the cast sheet instead of failing quietly.
- Deliver the final Stop before closing the Cast socket, verify transport commands actually took effect, and stop polling while the device's pushed status is still fresh.
- Keep the cast renderer playing through buffering gaps between tracks: pause/play mirrors `playWhenReady` instead of `isPlaying`, so a transition no longer sends a spurious Pause.
- Let the system media card and Bluetooth controls drive the session again: platform media clients were being rejected by the controller allow-list.
- Android Auto voice search no longer queries the server twice for the same query; the result is cached until the account changes.
- Playing a single song by media ID plays that song instead of expanding it into its album queue.
- Declare the correct DLNA mime per track in DIDL metadata (from the server's content type or the file suffix) instead of labeling every stream audio/mpeg, so strict renderers play FLAC/Opus/WAV.
- Cap the provider-side artwork cache (prune covers older than 30 days, 200 MB total) and serve failed artwork lookups from a short negative cache instead of blocking a binder thread on a dead server again.

## [0.8.3] - 2026-11-09

### Fixed

- Trust CA certificates installed in Android's user certificate store when connecting to a music server.

## [0.8.2] - 2026-11-08

### Fixed

- Keep the lyrics screen open when the server is unavailable and clear previous lyrics when changing tracks.
- Stop playback and show an error when a stream redirects to an unsupported URL, preserving the selected track.

## [0.8.1] - 2026-10-27

### Fixed

- Keep bottom navigation labels on one line at narrow widths and larger text sizes, with an ellipsis when space is limited.

## [0.8.0] - 2026-10-26

### Added

- In-app offline playback with a Downloads library, saved artwork, progress, retry, removal, and account isolation.
- Album, playlist, and artist downloads, plus optional Wi-Fi syncing of favorite songs.
- Android Auto discovery, library browsing, search, and playback through a shared MediaLibraryService.
- System media-card artwork, favorite and repeat actions, and reserved previous/next controls.

### Fixed

- Synchronize the phone's queue and favorite state with external media controllers.
- Report playback from the service so scrobbling also works when a car starts playback.

## [0.7.0] - 2026-09-25

### Added

- Download a song from its menu to `Music/Resonance/Artist/Album`, preserving the original format, with Android download notifications and duplicate detection.

### Fixed

- Keep playback loading with the screen off and retry temporary connection failures without skipping through the queue.
- Make play/pause work while buffering and allow playback to restart after an error or the end of the queue.
- Seek once when releasing the progress slider instead of restarting the stream on every drag movement.
- Use mirrored Compose icons for direction-sensitive song, album, library, and playlist actions.

## [0.6.1] - 2026-06-13

### Changed
- Release builds are now reproducible: pinned R8 to a deterministic version and disabled the non-reproducible baseline profile, so a build from source can be verified against the published APK (for F-Droid)

## [0.6.0] - 2026-06-13

### Added
- Play next and add to queue, from a song's menu or for a whole album or playlist
- Editable play queue: drag to reorder, swipe to remove, and clear
- Artist pages now have a Tracks tab alongside Albums, plus play, shuffle, and loop
- Tap an artist or album name on the album screen or the player to navigate to it

### Changed
- "Play next" now inserts after the current track instead of playing immediately

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
