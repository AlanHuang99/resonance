# Resonance UI / Navigation Redesign

Date: 2026-06-12. Status: approved (Approach 1), executing.

## Goals

- Fix the wasted vertical space (empty bands at top and bottom). Root cause: under edge-to-edge, nested Scaffolds (a root one with the bottom bar, plus each screen's own Scaffold with a top bar) each apply the system status-bar / navigation-bar insets, so they get counted twice.
- Move Search and Settings into a persistent bottom navigation bar, alongside Home and Library (four tabs).
- Keep the bottom bar + mini-player on top-level and detail screens. The full Now Playing player slides up over everything.
- Gesture operations: swipe the mini-player up to expand / the full player down to minimize; swipe the album art left/right for previous/next; pull-to-refresh on Home and Library lists.
- Rework the core screens (Home, Search, Library, Player) for cohesion.

## Architecture (Approach 1)

A single root Scaffold owns the system insets and the bottom bar; screens render inset-light content; the full player is a slide-up overlay.

- Root `ResonanceNavHost`: one `Scaffold` with `contentWindowInsets = WindowInsets(0)` and `bottomBar = { Column { MiniPlayer(if playing); ResonanceBottomBar(four tabs) } }`. The `NavigationBar` consumes the system navigation-bar inset itself. Content is the `NavHost`, offset by the Scaffold's inner padding (bottom = bar height).
- The bottom bar is visible whenever the current route is not Login and not a full-screen overlay (player / lyrics / queue). Tabs: Home, Search, Library, Settings; re-selecting a tab pops to its root.
- Per-screen Scaffolds keep their `TopAppBar` (which owns the status-bar inset) but set `contentWindowInsets = WindowInsets(0)` so they do not re-add the bottom inset. The manual `bottom = 80.dp` list paddings are removed; the root bar now provides that space.
- Full Player: a slide-up overlay layered above the Scaffold in a root `Box`, so it covers the bottom bar. Swipe down dismisses it. Lyrics and Queue stay as pushed destinations reached from the player.

## Inset fix

The empty top band is the status-bar inset being applied by both the root Scaffold and each screen's `TopAppBar`. Fix: the root consumes no content insets; only the `TopAppBar`s apply the top inset and only the bottom bar applies the bottom inset, so each inset is applied exactly once.

## Screen reworks

- Home: keep the recent redesign (greeting, hero, shuffle-all, shelves). Remove the now-redundant search/settings actions from the top bar (they live in the bottom bar). Add pull-to-refresh.
- Search (now a tab): a top search field plus debounced results (artists / albums / songs), as a first-class tabbed screen with a clear empty state and no back button.
- Library: unchanged tabs; fix insets; add pull-to-refresh.
- Player (full Now Playing): large art, marquee title/artist, progress (its own flow), transport, shuffle/repeat, and the favorite / lyrics / queue row. Swipe down to dismiss; swipe art left/right for previous/next.

## Gestures

- Mini-player: tap or swipe up to expand to the full player. Full player: swipe down to minimize.
- Full player album art: horizontal drag triggers previous()/next().
- Home and Library: pull-to-refresh reloads from the server.

## Implementation phases (each verified on-device)

- R1: root Scaffold + four-tab bottom bar + Search/Settings as tabs + inset fix across all screens. Verify no gaps/overlaps on every screen and that the bar persists on detail screens.
- R2: full Player as a slide-up overlay that covers the bottom bar; swipe down to dismiss.
- R3: mini-player swipe-up to expand.
- R4: player swipe-art for previous/next.
- R5: pull-to-refresh on Home and Library.
- R6: screen polish (Home top bar, Search screen, Player layout).
- R7: version/changelog, merge to master, push, tag, GitHub release. Not F-Droid.

## Testing

On the connected Xiaomi (mondrian) after each phase: install, screenshot every screen, check insets (no empty bands, no content hidden under bars), verify navigation and gestures, and check logcat for crashes. Use the test server.

## Risks

- Inset tuning is empirical; verify per-screen on-device.
- The player overlay must handle back/gestures correctly and must not disturb the MediaController flow.
- Keep the app working and releasable at every commit.
