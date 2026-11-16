# Phone usability corrections implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Correct the eight screenshot findings approved by the user, retaining Mediacenter's own visual style.
**Architecture:** Update existing Compose components and callbacks only. Keep one original Remote application, existing target leases, protocols, stores and pairing data.
**Tech Stack:** Existing Kotlin/Compose/API37 and shared JVM/device Compose tests.
**Spec:** User-approved screenshot findings 1,2,3,4,6,7,8,9 in the current conversation; finding5 (different Mediacenter styling) explicitly excluded.

## Global constraints
- No middle-dot or bullet separators in user-facing text.
- TV app shortcut icons have no visible captions; preserve accessible app names and launch actions.
- Preserve original application ID and signer; install with adb install -r, never uninstall/clear data.
- Keep Mediacenter visually distinct; no LG-style redesign or protocol changes.
- Preserve all LG/Mediacenter credentials, VPN setup, HDMI and unrelated files.

## Review focus
- Narrow screens and 200% text: inset selector text and all controls remain reachable.
- Long TV/app names: no rounded-edge glyph clipping or shortcut-caption overflow.
- Screen-reader app names and target selection retain independent command ownership.
- Idle/disconnected/active playback: truthful status and correct transport session controls.
- Native/browser switching: menu/playback actions and browser context acknowledgements unchanged.

### Task 1: Screenshot correction and verified original-app update
**Files:** TargetSelector.kt, TvAppsSheet.kt, TvAppArtwork.kt, mediacenter/MediacenterTargetPage.kt, RendererControls.kt, NowPlayingControls.kt; shared usability tests and affected existing UI tests.
**Interfaces:** Existing TargetSelector and renderer/controller signatures remain; use existing MediaSessionController.destination and LibraryViewModel.search for a clear library entry action.
- [x] Add meaningful failing rendered regressions for selector text insets/no separators, icon-only accessible launches, centered direction pad, selected navigation and idle browse-library action.
- [x] Run the focused shared JVM tests; retain actual RED output.
- [x] Make selector an inset outlined Change device control, showing current name once and connection status separately; dialog target/status on separate text elements.
- [x] Make TV shortcut tiles square and icon-only; preserve picker names beside icons and accessible content descriptions.
- [x] Add Mediacenter page header margins and selected scrolling tabs; center renderer controls and put long menu/playback buttons on separate full-width rows.
- [x] Replace raw idle status and disabled idle transport clutter with a clear Browse library entry; query the library on entry. Keep active playback controls and browser behavior intact.
- [x] Run focused then full Android tests, quality/lint and standard original-ID APK builds. Do not rerun unchanged Rust.
- [x] Obtain one fresh read-only review of the complete diff; resolve blocking findings.
- [x] Verify APK package/signature against installed original; install -r preserving data. Verify actual LG/Mediacenter screenshots on Pixel, current connection and icon captions/insets, and remove test runner afterward.
- [x] Save sanitized evidence and continuation checkpoint; commit only scoped files.
