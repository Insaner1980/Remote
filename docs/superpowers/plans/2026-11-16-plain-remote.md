# Plain Remote Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. The user explicitly requests removal of all explanatory/technical text across the app, including detail screens, and has opted out of approval loops.

**Goal:** Make the whole original Remote interface readable through concise labels, ordinary controls and content, without technical dumps or explanatory paragraphs.

**Architecture:** Keep protocol/controller ownership, authenticated commands, saved settings and confirmations intact. Remove product-side diagnostic/help copy; replace raw status/error strings with concise truthful presentation. Use menus for secondary item actions and bounded readable content rows; keep the selected Settings overview.

**Tech Stack:** Existing Kotlin/Compose Material 3 and native/official icons; no new app or protocol.

**Spec:** All 17 user screenshots `Screenshot_20261116-163121.png` through `163333.png`, inspected in private Mediacenter `.superpowers/sdd/2026-11-16-plain-remote/contact-*.png`, and latest user instruction: remove all unnecessary explanation and technical information, not merely move it to Advanced.

## Global constraints

- Original `com.emmah.colordock.remote`, label Remote; same-signer in-place update, preserve both pairs/preferences.
- No dot separators; streaming shortcuts icons only with accessible action labels.
- No CLI, LAN/discovery/listener/UUID/epoch/native/session/metadata explanations anywhere in UI, including Advanced. Keep implementation documentation outside the app.
- Keep only concise actionable errors, necessary selection/verification data and essential confirmation questions; never fake success or replay uncertain commands.
- Preserve the selected quiet Settings overview; inspect every screenshot-related detail page and Remote/Library on the actual phone.

## Review focus

- Secondary menus must not launch playback or mutate the wrong item.
- Sound selection remains a saved preference, not falsely labelled confirmed playback.
- Uncertain/pending/recovered operations retain truthful state, cancellation and no-replay behavior.
- Large text, portrait/landscape, long names and offline states keep navigation/actions reachable.
- Pairing trust words, browser hostname approval, confirmation cancellation and target input leases remain functional.

### Task 1: Settings details

Files: `SettingsScreen.kt`, `LifetimeSettings.kt`, `AudioSettings.kt`, `VpnSettingsScreen.kt`, source/drive/folder components, `ThemeSettings.kt`, new shared control/copy helpers.

- [x] Add meaningful failing tests for concise Advanced/Sound/VPN pages, retained callbacks and source action menus.
- [x] Run RED, implement concise controls and remove all diagnostic/help/prose sections.
- [x] Preserve short labels, actual saved selection, trailing switches and safe disabled states; run focused GREEN.

### Task 2: Remote and library

Files: `MediacenterTargetPage.kt`, `NowPlayingControls.kt`, `LibraryScreen.kt`, queue/playlist/streaming/browser controls.

- [x] Add failing tests for no idle instruction block, one readable media title per row, secondary action menus and retained data/action identity.
- [x] Implement compact controls; remove duplicate Ready/Now playing/help/status copy, filename extensions and repeated item names in actions. Use existing thumbnails where available.
- [x] Keep all transport/queue/playlist actions available through clear controls and run focused GREEN.

### Task 3: Remaining dialogs and status

Files: connection/pairing dialogs, confirmation/recovery components, status/copy helpers.

- [x] Remove technical explanations throughout remaining flows; retain essential human confirmation/identity information and concise honest status.
- [x] Audit all production `Text` and dynamic-message renderers; adapt semantic tests to the intended new labels without weakening controller/safety assertions.
- [x] Full unit/Compose tests, Detekt, ktlint, lint, app/test builds; one fresh whole-diff review and fixes.

### Task 4: Actual phone acceptance

- [x] Same-signer update; verify encrypted pairs/preferences byte-identical before launch.
- [x] Focused physical tests and inspect/capture all screenshot-related pages, not just Settings overview. Check actual controls/back and both targets, no real media/test sound autoplay or settings mutations.
- [x] Preserve private screenshots/content, archive final APK, update truthful engineering checkpoint and task evidence, remove only test runner, restore phone timeout/font and commit only owned files.
