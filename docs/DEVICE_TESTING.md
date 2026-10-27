# Device testing

The device suite uses a separate `com.resonance.music.debug` installation and a local Subsonic fixture. The existing production installation and its account remain intact.

## Fixture

From the project root, generate a short audio fixture, start the server, and forward its port to the connected phone:

```bash
mkdir -p /tmp/resonance-fixture
ffmpeg -nostdin -hide_banner -loglevel error -f lavfi -i 'sine=frequency=440:duration=65' -c:a flac /tmp/resonance-fixture/tone.flac
cp docs/icon.png /tmp/resonance-fixture/cover.png
adb reverse tcp:8765 tcp:8765
uv run --no-project tools/subsonic_fixture.py /tmp/resonance-fixture
```

The fixture binds to loopback and supplies three songs, an album, an artist, a two-song playlist in a different order from the album, favorites, search, and an intentionally denied download. The test account is `device-test` with password `fixture-only` at `http://127.0.0.1:8765`. The server records request counts without authentication parameters at `/stats`.

## Build and device suite

In another terminal:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class 'com.resonance.music.MediaIntegrationTest#downloadsPersistRejectErrorsAndIsolateAccounts,com.resonance.music.MediaIntegrationTest#favoriteSyncQueuesWifiDownloadsAndCanBeDisabled,com.resonance.music.MediaIntegrationTest#browserDiscoversLibrarySearchesAndControlsPlayback,com.resonance.music.MediaIntegrationTest#legacyCarBrowserConnectsAndStartsPlaybackById,com.resonance.music.MediaIntegrationTest#artworkProviderSharesImagesAndRejectsWritesAndOtherAccounts,com.resonance.music.MediaIntegrationTest#carPlaylistSelectionPreservesItsOrder,com.resonance.music.MediaIntegrationTest#accountChangeRejectsInFlightPlaybackAndBrowse,com.resonance.music.MediaIntegrationTest#retryRepairsArtworkWithoutDownloadingAudioAgain,com.resonance.music.MediaIntegrationTest#voiceSearchRejectsAccountChanges' com.resonance.music.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The tests check completed transfers, local playback URIs, saved and streamed artwork, duplicate requests, denied downloads, missing-file recovery, account isolation, favorites sync, modern and legacy browsing, search, album and playlist queues, transport controls, and shared favorite/repeat state. The artwork provider also rejects writes, unpublished paths, and requests from a previous account. Delayed playback, browse, and voice-search requests are rejected after logout/login; a failed artwork fetch can be retried without downloading audio again.

After preparing downloads, disconnect the fixture and restart the app process before testing offline playback:

```bash
adb reverse --remove tcp:8765
adb shell am force-stop com.resonance.music.debug
adb shell am instrument -w -e class 'com.resonance.music.MediaIntegrationTest#offlinePlaybackAfterProcessRestart' com.resonance.music.debug.test/androidx.test.runner.AndroidJUnitRunner
adb reverse tcp:8765 tcp:8765
```

For a full disconnected-device check, record the phone's existing network settings, temporarily disable its network connections, run the offline test, and restore those settings.

To check Wi-Fi-only scheduling, record the active Wi-Fi network's metered override with `adb shell cmd netpolicy list wifi-networks`, temporarily set that network to metered with `adb shell cmd netpolicy set metered-network NETWORK_ID true`, and run `MediaIntegrationTest#wifiFavoritesWaitForUnmeteredNetwork`. It verifies that queued favorites issue no download or artwork requests. Restore the original override afterward (`undefined` for an automatic setting).

## Minified build and media card

Build a release variant signed with the debug key and using the test package ID:

```bash
./gradlew assembleRelease -PdeviceTest
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am force-stop com.resonance.music.debug
adb shell am start -n com.resonance.music.debug/com.resonance.music.MainActivity
```

This property is for local device verification only. Normal release builds retain the production package ID and signing configuration. The minified test variant replaces the debug fixture app while retaining its downloaded files and metadata.

Open Downloads, play the saved album, and verify artwork and controls in Quick Settings and on the lock screen. Check pause/play, previous/next, seeking, favorite state, and all repeat modes against `adb shell dumpsys media_session`. Repeat playback after force-stopping the app and disconnecting the fixture. Verify collection download actions and a failed-download retry through the UI.

## Android Auto head unit

Use Google's [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu) with the connected phone. Enable Android Auto developer mode, allow unknown sources for the sideloaded build, start the head-unit server, and forward `tcp:5277`. Android Auto's first-run consent is a user decision. After setup, verify discovery, all five library roots, album and playlist browsing, search, playback by media ID, transport actions, and Downloads without the server.

Car artwork uses a read-only content provider, following Android's [media artwork requirements](https://developer.android.com/training/cars/media/create-media-browser/media-artwork), checked on 2026-10-26. Verify images in browse lists as well as the player.

Stop the head-unit server, remove the temporary port forwards, and restore Android Auto development settings and the phone's original network settings when finished. When removing test data, remove only the fixture music and test artifacts created by this procedure.

## Navigation label check, 2026-10-27

The K60 reproduced wrapped Downloads and Settings labels at a 360 dp screen width with 1.3 font scale. With single-line labels, all five tabs remained aligned at 320 dp and 360 dp widths and at the original display settings. The 360 dp check covered both Home and Downloads selected at 1.3 font scale. Labels use an ellipsis when needed, while accessibility text retains the full names. The phone's original density and font scale were restored after testing the minified release variant.

## Results, 2026-10-26

Verified on a K60 running Android 16, using the separate test installation:

- All 21 JVM tests and all eleven device integration tests passed.
- Debug, instrumentation, minified test release, and normal production release builds passed. Android lint reported zero errors.
- Downloads survived a process restart and played with Wi-Fi disabled and the fixture connection removed. Local artwork remained available.
- Modern Media3 and platform MediaBrowser clients browsed the library and started playback by media ID. Search, album queues, and transport commands passed.
- The minified release downloaded a removed song again through the album action, restored three saved songs, and played its local queue after a process restart with the fixture disconnected.
- The native media card displayed track metadata and artwork. Play/pause, previous/next, favorite state, and repeat off/all/one were checked on the device.

Android Auto setup was completed with the user's consent. The Desktop Head Unit verified Downloads, Albums, Artists, Playlists, and Favorite songs; album and artist drill-down; text search and playback from results; play/pause and previous/next; favorite state; and all repeat modes. A two-song playlist preserved its order when selected from the car and when advancing to the next song. After force-stopping Resonance, disabling Wi-Fi, and removing the fixture connection, Downloads still showed artwork and played the local three-song queue. The temporary head-unit server and development settings were restored afterward.

Head-unit testing identified and corrected missing artwork in browse lists and album-order playback for playlist selections. Regression checks cover readable artwork content URIs, rejected writes and account changes, playlist order from both IDs and full browsed items, offline Downloads selections, artwork repair, and Wi-Fi-only scheduling. A separate unprivileged probe app connected before controller authorization was added and was rejected afterward. Android Auto remains authorized through controller trust or its verified production signing certificates.

Local review artifacts are in `app/build/reports/device/`: `k60-system-media-card.png`, `k60-android-auto-player.png`, `k60-android-auto-albums.png`, `k60-android-auto-search.png`, `k60-android-auto-playlist-queue.png`, and `k60-android-auto-offline-player.png`. `resonance-device-test.apk` is the minified test APK installed on the K60; it uses a separate package ID and the debug signing key.
