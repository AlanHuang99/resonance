# Offline playback, Android Auto, and system media controls

**Goal:** Play downloaded music inside Resonance, browse and play through Android Auto, and expose album art and playback actions in Android's system media card.

**Architecture:** Keep DownloadManager for persistent original-file transfers and add a private, account-scoped metadata index. Resolve completed downloads before stream URLs. Extend the existing Media3 service to a MediaLibraryService so the app, system controls, and Android Auto share one player and queue.

**Scope:** Single-song and album/playlist/artist downloads; an offline Downloads screen with progress, retry, removal, play, and shuffle; opt-in favorites downloads; car browsing and search; system title, artist, artwork, seek, previous, next, play/pause, favorite, and repeat controls. Android owns the system card layout.

## Implementation

- [x] Persist song metadata alongside existing download IDs, isolate servers/accounts, handle missing and failed files, and cache artwork for offline use.
- [x] Add durable bulk-download and favorites-sync jobs, collection download actions, and a Downloads screen. Keep completed files usable after app restart and without a connection.
- [x] Share media-item construction and local-file resolution between phone and car playback. Keep UI queue state synchronized with changes initiated outside the app.
- [x] Add car discovery metadata, browsable albums/artists/playlists/favorites/downloads, pagination, search, media-ID resolution, and signed-out/error handling.
- [x] Expose custom favorite/repeat commands and artwork through the media session. Reserve transport slots and keep actions synchronized across clients.
- [x] Add focused tests for account isolation, pagination, persisted downloads, external playback, and download failures. Build unit tests, lint, debug, and minified release.
- [x] Test on the K60: batch downloads, offline playback after restart, local artwork, system-card transport/actions, and media-browser discovery/playback.
- [x] Complete Android Auto's first-run setup with user consent and verify the desktop head-unit UI.
- [x] Update README and changelog to reflect verified behavior and record device results.

- [x] Review controller authorization, account transitions, Wi-Fi restrictions, full-item playlist selection, and durable artwork repair; add regression checks.

## Verification constraints

Use a separate test application ID when the installed release signing key is unavailable. Preserve existing app data and downloaded music. Use a controlled Subsonic test server and generated audio for repeatable device checks. Test missing files, duplicate requests, account changes, and disconnected playback. Do not publish or push without approval.

## References

- [Android media controls](https://developer.android.com/media/implement/surfaces/mobile), retrieved 2026-10-26.
- [Android for Cars media integration](https://developer.android.com/media/implement/surfaces/cars), retrieved 2026-10-26.
