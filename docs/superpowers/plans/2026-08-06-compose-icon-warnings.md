# Compose Auto-Mirrored Icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the four Compose deprecation warnings by using the supported auto-mirrored icons for directional actions.

**Architecture:** Keep the existing Material icon dependency and UI structure. Import the auto-mirrored `PlaylistAdd` and `Sort` vectors and replace only the deprecated icon references, preserving left-to-right rendering while allowing correct right-to-left mirroring.

**Tech Stack:** Kotlin 2.0, Jetpack Compose Material Icons, Android Gradle Plugin 8.5

## Global Constraints

- Do not change menu behavior, labels, callbacks, or layout.
- Keep the existing wildcard filled-icon imports for unrelated icons.
- Verify both debug and minified release builds.

---

### Task 1: Replace deprecated directional icon references

**Files:**
- Modify: `app/src/main/java/com/resonance/music/ui/components/SongListItem.kt`
- Modify: `app/src/main/java/com/resonance/music/ui/screens/album/AlbumScreen.kt`
- Modify: `app/src/main/java/com/resonance/music/ui/screens/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/resonance/music/ui/screens/playlist/PlaylistScreen.kt`

**Interfaces:**
- Consumes: Compose `Icons.AutoMirrored.Filled.PlaylistAdd` and `Icons.AutoMirrored.Filled.Sort` image vectors.
- Produces: The same `Icon` composables without deprecated API usage and with right-to-left mirroring support.

- [x] **Step 1: Verify the warning regression is present**

Run:

```bash
build_output=$(./gradlew compileDebugKotlin --rerun-tasks 2>&1); printf '%s\n' "$build_output"; ! printf '%s\n' "$build_output" | rg '@property:Deprecated.*(PlaylistAdd|Sort)'
```

Expected: FAIL because the compiler reports four matching deprecation warnings.

- [x] **Step 2: Apply the minimal icon migration**

Add `import androidx.compose.material.icons.automirrored.filled.PlaylistAdd` to `SongListItem.kt`, `AlbumScreen.kt`, and `PlaylistScreen.kt`, then replace each `Icons.Default.PlaylistAdd` with `Icons.AutoMirrored.Filled.PlaylistAdd`.

Add `import androidx.compose.material.icons.automirrored.filled.Sort` to `LibraryScreen.kt`, then replace `Icons.Default.Sort` with `Icons.AutoMirrored.Filled.Sort`.

- [x] **Step 3: Verify the deprecation regression is gone**

Run the Step 1 command again.

Expected: PASS with no matching deprecation warning.

- [x] **Step 4: Run the repository CI command from a clean task graph**

Run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --stacktrace --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`, nine unit tests with zero failures, lint success, and both APK artifacts present.

- [x] **Step 5: Commit the focused change**

```bash
git add docs/superpowers/plans/2026-08-06-compose-icon-warnings.md app/src/main/java/com/resonance/music/ui/components/SongListItem.kt app/src/main/java/com/resonance/music/ui/screens/album/AlbumScreen.kt app/src/main/java/com/resonance/music/ui/screens/library/LibraryScreen.kt app/src/main/java/com/resonance/music/ui/screens/playlist/PlaylistScreen.kt
git commit -m "Fix deprecated Compose icon usage"
```
