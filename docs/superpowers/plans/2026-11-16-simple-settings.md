# Simple Settings Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans. Execute inline; the user selected the design and authorized implementation without further approval questions.

**Goal:** Replace the long Mediacenter settings page with the selected six-row settings design in the original Remote Android app.

**Architecture:** Keep native Compose controllers and existing authenticated commands. A small settings navigation state selects dedicated detail screens; the target page owns the heading, compact device selector and bottom navigation. Keep recovery details behind a clearly accessible attention action without resetting uncertainty or replaying commands.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing drawables and official Material icons.

**Spec:** User-selected image `exec-c270b14d-bdd0-4731-8cb8-a93002557d9a.png`; private reference copy in Mediacenter `.superpowers/sdd/2026-11-16-simple-settings/selected-reference.png`.

## Global Constraints

- Original `com.emmah.colordock.remote`, named Remote; install in place with the same signer.
- Preserve saved pairings, input ownership and confirmation semantics.
- No middle-dot or bullet visual separators; streaming shortcuts remain icons only.
- Settings overview: Sound, VPN, Media, Devices, Appearance, Advanced. No diagnostic paragraphs or inline switches.
- Compact single-line Mediacenter selector and persistent Remote / Library / Settings bottom navigation.

## Review Focus

- Large text and landscape: rows, back and bottom navigation remain usable.
- Offline state: overview navigates, mutating controls remain disabled.
- Pending confirmations and pairing codes: existing cancellation and volatility remain intact on navigation.
- Uncertain commands: details stay accessible without replay or false success.
- Phone appearance and connection callbacks: reachable from their respective sections without changing device ownership.

### Task 1: Settings hierarchy and chrome

Files: `TargetSettings.kt`, `SettingsScreen.kt`, new settings navigation/chrome components, `MediacenterTargetPage.kt`, `TargetSelector.kt`, `MainActivity.kt`, shared Compose tests.

- [x] Write integration tests for six-row overview, dedicated pages/back, accessible compact selector, aligned switches, bottom navigation and offline/large-text behavior.
- [x] Run targeted tests and confirm missing overview/navigation failures.
- [x] Implement native overview, section pages, icons, compact device selector, heading and bottom navigation; retain all existing settings actions.
- [x] Adapt existing settings tests to navigate sections and retain command/confirmation assertions. Run targeted tests green.

### Task 2: Contextual attention and verification

Files: target-page feedback component, shared tests, `design-qa.md`, engineering checkpoint/evidence.

- [x] Add regression proving uncertain input is visible via attention details while overview stays calm and no action is replayed.
- [x] Implement compact attention entry with full truthful details; retain controller uncertainty and recovery behavior.
- [x] Format, run unit/Compose tests, Detekt, ktlint, Android lint and build app/test APKs.
- [x] Obtain one fresh whole-diff review and fix material findings.
- [x] Update original app in place and verify credential hashes unchanged.
- [ ] Exercise all sections and 29 focused tests on unlocked Pixel at existing font scale (phone currently locked).
- [ ] Compare selected reference and actual Settings screenshot in one normalized comparison; fix P0/P1/P2 issues and save passing design QA.
- [ ] Inspect LG and Mediacenter on phone, preserve private evidence, archive APK and update engineering checkpoint; commit only task-owned files.
