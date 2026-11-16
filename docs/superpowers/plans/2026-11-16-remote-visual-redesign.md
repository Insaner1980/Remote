# Remote Visual Redesign Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans. Work task by task, in order. Each task lists files, exact specs, steps and acceptance checks. The user has made both design decisions (section 0), so start without further approval questions. This is a visual-only change: every existing callback, command, confirmation guard, pairing, preference and test tag must keep working exactly as today.

**Goal:** Make the original Remote app (`com.emmah.colordock.remote`) look like a modern Material 3 Android app. Keep the existing warm palette and every existing function. **LG TV and Mediacenter keep separate visual styles.** Both get the same color roles, typeface and type scale. Each target's own components are cleaned up in that target's own style.

**Architecture:**
1. Theme first (`AppTheme.kt`): color roles, Roboto Flex typography, shapes. This is shared, because it is the theme, not a target style.
2. A small Mediacenter-only component file.
3. Mediacenter screens rebuilt with those components.
4. Targeted fixes to LG screens that keep LG's existing look (outlined selector, bordered 16 dp keys, vermillion navigation indicator).

No ViewModel, controller, protocol or persistence code changes. If a screen needs a value that is not already in its state object, leave that part out. Do not add new data plumbing.

**Tech stack:** Kotlin, Jetpack Compose Material 3 (BOM `2026.09.00`), existing drawables in `app/src/main/res/drawable`, the existing `DockIcon` helper, and Roboto Flex (bundled).

**Spec images:** `/home/emma/Dev/Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/` (private, git-ignored). Relative from this file: `../../../../Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/`. The folder's `README.md` explains its contents. **Open every image referenced in a task before you edit that task's files.**

---

## 0. Decisions (made by the user on 2026-11-16)

- **D1, separate styles: keep LG TV and Mediacenter visually separate.** `AGENTS.md` stays unchanged:
  > Mediacenter may have a different visual style from the LG Remote view; do not force those styles to match.

  Concretely:
  - LG keeps its outlined three-line "Change device" selector, its bordered 16 dp rounded-rectangle keys, its bordered app tiles, and its vermillion (`primaryContainer`) navigation indicator with the top border line.
  - Mediacenter keeps its compact one-line selector and Remote / Library / Settings navigation, and gets its own pill-shaped, borderless, tonal component set.
  - Do not make a Mediacenter component reusable by LG screens, or the reverse. **The only shared UI composables** are the theme, `DirectionPad` (already shared today: LG `RemoteScreen` and Mediacenter `RendererControls` both use it), `AdaptivePair`, `DockIcon` and the target picker entry point (`TargetSelector`), which already serves both targets.
- **D2, typeface: Roboto Flex, bundled.** Use it for every text style in both targets, with a slightly wider width axis on headline styles (Task 2).

---

## 1. Hard constraints (from `AGENTS.md`, earlier plans and the 2026-11-16 audit notes)

These override anything in the mockups.

1. Original app only: `com.emmah.colordock.remote`, named Remote. Install in place with the same signer. Never uninstall or clear data. Preserve both encrypted pairings and all preferences.
2. **No middle-dot or bullet characters as separators** in user-facing copy. Supporting text is a single value ("TV (HDMI)", "Connected", "4 folders"), never two values joined by a dot.
3. **No colored status dots.** Connection state is neutral text ("Connected" / "Not connected") or the existing `ic_wifi` / `ic_wifi_off` icon in `onSurfaceVariant`. Remove the green/red `Canvas` dot in `RemoteScreen.RemoteHeader`'s fallback branch (constants `CONNECTED_STATUS_ARGB`, `UNAVAILABLE_STATUS_ARGB`).
4. TV/streaming shortcut strips show **icons only, no captions**. Keep every existing `contentDescription` ("Open Netflix", etc.).
5. Mediacenter keeps a **compact one-line device selector** and **Remote / Library / Settings** bottom navigation.
6. Target selection must look actionable, show the current device name **once**, and keep text inside its rounded bounds (single line, ellipsis).
7. Mediacenter Settings overview has **exactly six rows**: Sound, VPN, Media, Devices, Appearance, Advanced. No group headers, paragraphs, diagnostics or inline switches on the overview.
8. Concise everyday labels. Do not show LAN, discovery, UUID, epoch or raw backend text.
9. Secondary library and folder actions stay in icon (overflow) menus. Visible action labels never repeat filenames.
10. **LG and Mediacenter styles stay separate** (D1). Do not edit `AGENTS.md`.
11. Keep every existing `testTag` (list below). New components may add tags, but must never rename or remove existing ones.
12. Respect the user's font scale (Pixel 9, 420 dpi, **font scale 1.15**). Keep the existing large-text fallbacks (`LARGE_TEXT_FONT_SCALE = 1.4f` in `RemoteButtons.kt` and `RemoteScreen.kt`, `APP_ROW_FONT_SCALE`, `STACKED_CHOICE_FONT_SCALE`).
13. Visually inspect **both target screens on the actual phone** before reporting any layout task complete.

Existing test tags that must survive: `target-selector`, `bottom-navigation`, `mediacenter-bottom-navigation`, `media-nav-<Destination>`, `settings-section-<Section>`, `direction-pad`, `compact-controls`, `transport-controls`, `app-shortcuts`, `app-artwork-<id>`, `app-picker-list`, `tv-app-sheet`, `app-shelf-entry`, `sources-screen`, `drive-settings`, `folder-browser`, `shuffle-settings`, `queue-options-<id>`, `media-action-progress`, `browser-touchpad`, `wallpaper-image`.

---

## 2. Image index

`IMG` = `../../../../Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign`

### 2.1 Annotated current screens (findings are numbered on the images)

| Image | Findings |
| --- | --- |
| ![](../../../../Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/annotated/remote-mediacenter.png) `IMG/annotated/remote-mediacenter.png` | 1: 28 sp bold title plus a 64 dp selector take 21% of the screen before any control. 2: "Queue" is a floating orange text button. 3: Menu and Back are thin full-width outlined pills. 4: "Apps unavailable" is a status shown as a heading. 5: the app row is clipped at the right edge and each logo sits inside an outlined button. 6: diagonal seam lines on the D-pad. 7: the nav bar has no selected-item indicator. **Fix all seven.** |
| ![](../../../../Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/annotated/remote-lg-tv.png) `IMG/annotated/remote-lg-tv.png` | 1: the LG selector differs from Mediacenter's. **Keep it; this difference is intentional (D1).** 2: the appearance button is an unlabeled "tune" icon with no container. **Fix.** 3: the "Color Dock" button (opens the app on the TV) and the "Color Dock" tab (manages it) share a name and icon. **Fix with a distinct icon.** 4: Mute TV and Numbers tiles have different heights because "123" (`titleLarge`) is taller than a 24 dp icon. **Fix.** 5: the rockers have no VOL/CH text. **Fix.** 6: the nav bar clips the app row, and logos have extra inner padding. **Fix.** 7: the vermillion nav indicator differs from Mediacenter. **Keep it (D1).** |
| ![](../../../../Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/annotated/vpn-scrolled.png) `IMG/annotated/vpn-scrolled.png` | 1: scrolled content slides under the selector with no surface or edge ("Disconnect" collides). 2: the "VPN for apps" heading is the same size as the rows. 3: no app logos, even though the app already has `tv_app_*` drawables. 4: two oranges on filled controls (salmon switches, vermillion OK). Finding 4 is resolved by the accent rules in 3.1, not by changing either color. **Fix 1–3.** |
| ![](../../../../Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/annotated/library-photos.png) `IMG/annotated/library-photos.png` | 1: tiles slide under the selector. 2: fallback filename text touches the tile edge and uses body size. 3: a generic image icon where the thumbnail should be. 4: pagination is a single unlabeled chevron. **Fix all four.** |

### 2.2 Proposal mockups (dark and light)

| Screen | Dark | Light |
| --- | --- | --- |
| Remote, Mediacenter | ![](../../../../Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/after/remote-mediacenter-dark.png) `IMG/after/remote-mediacenter-dark.png` | `IMG/after/remote-mediacenter-light.png` |
| Remote, LG TV (LG style kept) | ![](../../../../Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/after/remote-lg-tv-dark.png) `IMG/after/remote-lg-tv-dark.png` | `IMG/after/remote-lg-tv-light.png` |
| Mediacenter Settings overview | ![](../../../../Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/after/settings-dark.png) `IMG/after/settings-dark.png` | `IMG/after/settings-light.png` |
| Mediacenter VPN section | ![](../../../../Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/after/vpn-dark.png) `IMG/after/vpn-dark.png` | `IMG/after/vpn-light.png` |
| Mediacenter Library, Photos | ![](../../../../Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/after/library-photos-dark.png) `IMG/after/library-photos-dark.png` | `IMG/after/library-photos-light.png` |
| Device picker sheet (opened from Mediacenter) | ![](../../../../Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/after/device-picker-dark.png) `IMG/after/device-picker-dark.png` | `IMG/after/device-picker-light.png` |

**Known differences between the mockups and this plan (follow the plan):**
- **Device picker mockup:** the overflow menu with Reconnect / Forget TV on the LG row is illustrative only. Do not add that entry point. Reconnect and Forget TV stay in the existing `TvConnectionDialog`. The sheet itself is used only when the picker is opened from Mediacenter (Task 5).
- **Library mockup:** fallback photo tiles show only an icon. **Keep a short title** on fallback tiles (Task 10), because 117 of 118 photo originals are currently missing and icon-only tiles would be indistinguishable.
- **App tiles:** the mockups show placeholder wordmarks. Use the existing artwork (`TvAppArtwork`, `tv_app_*` drawables).
- **Mockup scale:** a 360 px wide mockup phone corresponds to 412 dp. Use the dp values in this plan, not measured pixels.
- **LG mockup:** this mockup shows the LG fixes in LG's own style. Anything in it that already matches today's LG screen (selector, key shapes and borders, nav bar) stays as it is.

### 2.3 Current screens (raw, for before/after comparison)

All in `IMG/before/`:

- Mediacenter: `mc-remote.png`, `mc-library-photos.png`, `mc-library-photos-scrolled.png`, `mc-settings.png`, `mc-settings-sound.png`, `mc-settings-vpn.png`, `mc-settings-vpn-scrolled.png`, `mc-settings-media.png`, `mc-settings-devices.png`, `mc-settings-appearance.png`, `mc-settings-advanced.png`, `mc-device-dialog.png`
- LG TV: `lg-remote.png`, `lg-remote-apps-scrolled.png`, `lg-device-dialog.png` (TvConnectionDialog), `lg-wallpapers.png`, `lg-appearance-dialog.png`, `lg-color-dock.png`

---

## 3. Design specification

### 3.1 Color roles (shared theme)

Keep every existing named color. What changes is the **role mapping**. Today `SoftDarkColors` puts Masala `#403D39` into five roles (surface, surfaceVariant, surfaceBright, surfaceContainer, surfaceContainerHigh), so every container has the same tone.

Accent rules:

| Color | Role | Used for | Never used for |
| --- | --- | --- | --- |
| Vermillion `#EB5E28` | `primaryContainer` | D-pad **OK** (both targets); **LG** nav indicator (existing LG style, unchanged) | Mediacenter nav, chips, text |
| Salmon `#FF9B75` (dark) / `#A63C12` (light) | `primary` | Switch tracks, the single filled button per screen, Mediacenter section labels, LG power icon (as today) | Status text, device names, list icons |
| Tonal `#55372A` (dark) / `#FFDBCC` (light) | `secondaryContainer` | **Mediacenter**: nav indicator, selected chip and segment, selected sheet row, list-row icon circles, Queue button | LG screens (LG has no tonal accents) |
| Neutral `#CCC5B9` (dark) / `#5E5952` (light) | `onSurfaceVariant` | Status text ("Connected"), supporting text, inactive icons | |
| Error `#FFB4AB` / `#B3261E` | `error` | Destructive actions: Forget TV, Remove phone, Remove computer, Stop playback | |

**Dark scheme (`SoftDarkColors`) — set exactly:**

| Role | Value | Change |
| --- | --- | --- |
| background / onBackground | `#252422` / `#FFFCF2` | same |
| surface / onSurface | `#252422` / `#FFFCF2` | **was Masala** |
| surfaceDim | `#201F1D` | new value |
| surfaceBright | `#4B4843` | new value |
| surfaceContainerLowest | `#201F1D` | new value |
| surfaceContainerLow | `#2D2B28` | new value |
| surfaceContainer | `#34322E` | new value |
| surfaceContainerHigh | `#403D39` (Masala) | same tone, now only here |
| surfaceContainerHighest | `#4B4843` | was `#514D47` |
| surfaceVariant / onSurfaceVariant | `#403D39` / `#CCC5B9` | same |
| primary / onPrimary | `#FF9B75` / `#252422` | same |
| primaryContainer / onPrimaryContainer | `#EB5E28` / `#252422` | same |
| secondary / onSecondary | `#CCC5B9` / `#252422` | same |
| secondaryContainer / onSecondaryContainer | `#55372A` / `#FFDBCC` | **was ChromeWhite / WarmBlack** |
| tertiary roles | unchanged | |
| outline | `#9B9488` | same |
| outlineVariant | `#5C5750` | **unchanged** (LG borders depend on it) |
| error roles, inverse roles, scrim | unchanged | |
| surfaceTint | `Color.Transparent` | same |

**Light scheme (`WarmLightColors`) — set exactly:**

| Role | Value |
| --- | --- |
| background / onBackground | `#FFFCF2` / `#252422` |
| surface / onSurface | `#FFFCF2` / `#252422` |
| surfaceDim | `#E9E3D7` |
| surfaceBright | `#FFFCF2` |
| surfaceContainerLowest | `#FFFFFF` |
| surfaceContainerLow | `#F8F3E9` |
| surfaceContainer | `#F1ECE1` |
| surfaceContainerHigh | `#E9E3D7` |
| surfaceContainerHighest | `#DED7CB` |
| surfaceVariant / onSurfaceVariant | `#E9E3D7` / `#5E5952` |
| primary / onPrimary | `#A63C12` / `#FFFCF2` |
| primaryContainer / onPrimaryContainer | `#EB5E28` / `#252422` |
| secondary / onSecondary | `#403D39` / `#FFFCF2` |
| secondaryContainer / onSecondaryContainer | `#FFDBCC` / `#5C1F05` |
| outline / outlineVariant | `#79736A` / `#CCC5B9` (outlineVariant unchanged) |
| error roles, inverse roles, scrim, tertiary | unchanged |

Define new constants next to the existing `*_ARGB` constants in `AppTheme.kt`. Name them after their role, for example `DARK_CONTAINER_LOW_ARGB`.

### 3.2 Typeface and type scale (shared theme)

Bundle Roboto Flex (see Task 2). Every style uses the `RobotoFlex` family. Headline and title-large styles use the `RobotoFlexWide` family (width 112).

| M3 style | Size / line height | Weight | Family | Use |
| --- | --- | --- | --- | --- |
| headlineSmall | 24 / 32 sp | 600 | Wide | Mediacenter top-level page title (Library, Settings) |
| titleLarge | 22 / 28 sp | 600 | Wide | Mediacenter sub-page title, sheet title, now-playing title, LG selector device name (LG already uses `titleLarge` there) |
| titleMedium | 16 / 24 sp | 500 | Normal | List-row headline, Mediacenter selector name, LG "TV apps" label (as today) |
| titleSmall | 14 / 20 sp | 600 | Normal | Mediacenter section label (color `primary` at call site) |
| bodyLarge | 16 / 24 sp | 400 | Normal | Body text, text-field input |
| bodyMedium | 14 / 20 sp | 400 | Normal | Supporting text, status, notices (color `onSurfaceVariant` at call site) |
| bodySmall | 12 / 16 sp | 400 | Normal | LG selector "Connected" line (as today) |
| labelLarge | 15 / 20 sp | 500 | Normal | Button and key labels |
| labelMedium | 12 / 16 sp | 500 | Normal | Nav labels, vertical tile labels, LG "Change device" line, VOL/CH |
| labelSmall | 11 / 16 sp | 500 | Normal | Fallback photo title |

Remove ad-hoc sizes that fight this scale. `SettingsOverview` uses `fontSize = 18.sp`. `MediacenterPageHeading` uses `headlineLarge` + `FontWeight.Bold`.

### 3.3 Shapes

Pass `shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp))` to `MaterialTheme`.

| Element | Mediacenter | LG (keep its style) |
| --- | --- | --- |
| Remote keys | `CircleShape` pill, no border, `surfaceContainerHigh` | `RoundedCornerShape(16.dp)`, 1 dp `outlineVariant` border, `surfaceContainerHigh` (today `surface` = Masala; same tone) |
| Two-line tiles | n/a | 16 dp, bordered, **fixed 64 dp height** |
| Rockers | n/a | 24 dp (as today), bordered |
| Device selector | one-line pill (`CircleShape`) | outlined 16 dp three-line box (as today) |
| App tiles | 18 dp, no border | 16 dp, bordered (as today) |
| Filter chips | 8 dp (M3 default) | n/a |
| List groups / rows | 22 dp outer / 4 dp inner | n/a |
| Sheets | 28 dp top (M3 default) | dialogs unchanged |
| Search field | `CircleShape` | n/a |

### 3.4 Spacing

Add `internal object RemoteDimens` in a new file `ui/RemoteDimens.kt`. These are numbers, not components, so both targets may use them.

```kotlin
internal object RemoteDimens {
    val ScreenGutter = 16.dp   // was 20.dp in MediacenterTargetContent; LG RemoteControls already 16.dp
    val GroupGap = 2.dp        // between rows inside a Mediacenter list group
    val ItemGap = 8.dp         // between sibling buttons / chips
    val BlockGap = 12.dp       // between blocks inside a page
    val SectionGap = 24.dp     // between sections
    val MinTouch = 48.dp
    val KeyHeight = 56.dp      // one-line remote key (both targets)
    val TileHeight = 64.dp     // LG two-line tile
    val RowMinHeight = 64.dp   // Mediacenter list row with supporting text; 56.dp without
}
```

---

## 4. Tasks

### Task 1 — Theme roles and the `surface` sweep

**Files:** `ui/AppTheme.kt`, every call site that uses `colorScheme.surface` as a container color, new unit test `app/src/test/java/com/emmah/colordock/remote/ui/AppThemeContrastTest.kt`.

**Why this is risky:** `surface` changes from Masala to the background tone. Every component that relied on `surface` to look raised would disappear. Sweep them all in this task.

Steps:
1. Run `grep -rn "colorScheme.surface\b\|colors.surface\b" app/src/main/java` and list every hit in the task log. Known hits:
   - `TargetSelector.kt` `CompactTargetSelector`
   - `RemoteButtons.kt` `remoteColors()`, `DirectionPad`, `Rocker`
   - `TvAppsSheet.kt` `TvAppTile`
   - `mediacenter/LibraryItemRow.kt` `PhotoTile`
   - `mediacenter/MediacenterPageChrome.kt` `MediacenterBottomNavigation`
2. Write `AppThemeContrastTest` first. For both schemes:
   - Assert WCAG contrast ≥ 4.5 for onBackground/background, onSurface/surfaceContainer, onSurface/surfaceContainerHigh, onSurfaceVariant/surfaceContainer, onSurfaceVariant/surfaceContainerHigh, onPrimary/primary, onSecondaryContainer/secondaryContainer, and primary/background.
   - Assert ≥ 3.0 for onPrimaryContainer/primaryContainer (large "OK" text only).
   - Assert the five container tones are pairwise distinct.
   - Run it and confirm it fails on the current mapping.
3. Change both schemes to the tables in 3.1.
4. Point every sweep hit at **`surfaceContainerHigh`**, which keeps today's exact tone, so LG looks the same. **Exception:** `MediacenterBottomNavigation` uses `surfaceContainer` (Task 6).
5. Run the new test green, then `:app:testDebugUnitTest`. Update only assertions that encode the old role mapping (likely `ComposeContractsTest`, `ThemeSettingsTest`), and name them in the commit message.

Acceptance: build passes. In both themes, a debug build of LG Remote looks identical to `IMG/before/lg-remote.png` apart from font (no container disappeared).

### Task 2 — Roboto Flex, typography and shapes

**Files:** `ui/AppTheme.kt`, new `app/src/main/res/font/roboto_flex.ttf`, new `app/src/main/assets/licenses/roboto_flex_OFL.txt`, `ui/mediacenter/MediacenterPageChrome.kt`, `ui/mediacenter/SettingsNavigation.kt`.

Steps:
1. Download the Roboto Flex variable font from the official Google Fonts repository: `https://github.com/google/fonts/tree/main/ofl/robotoflex`.
   - Use the file whose name starts with `RobotoFlex[` and has the axes in brackets.
   - Save it as `res/font/roboto_flex.ttf` (resource names must be lowercase, with no brackets).
   - Copy that folder's `OFL.txt` to `assets/licenses/roboto_flex_OFL.txt`.
   - Record the source URL and the file's SHA-256 in the task log.
2. In `AppTheme.kt`, define the two families with `androidx.compose.ui.text.font.Font` and `FontVariation` (API ≥ 26; this app's minSdk allows it, so verify `minSdk` in `app/build.gradle.kts` first):
   ```kotlin
   @OptIn(ExperimentalTextApi::class)
   private fun flex(weight: Int, width: Float = 100f) =
       Font(
           R.font.roboto_flex,
           weight = FontWeight(weight),
           variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
       )
   private val RobotoFlex = FontFamily(flex(400), flex(500), flex(600))
   private val RobotoFlexWide = FontFamily(flex(500, 112f), flex(600, 112f))
   ```
   If `minSdk < 26`, keep the same families. Older devices then ignore the variation settings and render the default instance, which is acceptable.
3. Replace `Typography().let { ... }` in `RemoteTheme` with an explicit `Typography(...)` built from 3.2 (set `fontFamily`, `fontSize`, `lineHeight` and `fontWeight` for every listed style; leave unlisted styles at M3 defaults but with `fontFamily = RobotoFlex`).
4. Pass the `Shapes` from 3.3 into `MaterialTheme`.
5. Remove the ad-hoc sizes: `SettingsOverview` `fontSize = 18.sp` and `MediacenterPageHeading` `headlineLarge` + `FontWeight.Bold` (Task 8 sets their final styles).
6. Run `:app:testDebugUnitTest` and `IconRenderingTest` / `DesignCaptureTest` locally if they compare text bounds. Update only text-size-dependent expectations.

Acceptance: on a debug build, both targets use Roboto Flex. Mediacenter rows, section labels and supporting text have three distinct sizes. The APK grows by roughly the font file size, which you record.

### Task 3 — Mediacenter component set (Mediacenter only)

Create `ui/mediacenter/MediacenterDesign.kt`. **Do not call these from LG screens** (D1). Each composable takes `modifier: Modifier = Modifier` as its first optional parameter (Compose rules lint).

1. **`ListGroup(content: @Composable ColumnScope.() -> Unit)`**: a `Column` with `spacedBy(RemoteDimens.GroupGap)`.
2. **`ListRow(title, supporting: String?, position: ListRowPosition, leadingIcon: Int? = null, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null, titleColor: Color = Color.Unspecified)`**:
   - Background `surfaceContainer`. Shape by `position` (`Single` 22 dp; `First` 22/22/4/4; `Middle` 4; `Last` 4/4/22/22). Clip the click ripple to the same shape.
   - Min height 64 dp with supporting text, otherwise 56 dp. Padding 16 dp horizontal, 12 dp vertical. Gap 16 dp.
   - Leading: a 40 dp circle in `secondaryContainer` with a 20 dp `onSecondaryContainer` icon, **or** the custom `leading` slot (app logos: 36 dp, 10 dp corners).
   - Title `titleMedium` `onSurface` (or `titleColor`), one line plus ellipsis; two lines when `supporting == null`. Supporting `bodyMedium` `onSurfaceVariant`, one line plus ellipsis.
   - Trailing: chevron `ic_keyboard_arrow_right` in `onSurfaceVariant`, or a `Switch`, `RadioButton`, `ItemActions` overflow or check icon.
   - Clickable rows use `Modifier.clickable(role = Role.Button)` across the whole row.
   - **No `HorizontalDivider`** anywhere in Mediacenter lists.
3. **`SectionLabel(text: String, trailing: (@Composable () -> Unit)? = null)`**: `titleSmall` in `primary`, 4 dp start padding, 24 dp above and 8 dp below. The trailing slot holds a list-level `RefreshAction`.
4. **`MediaKey(text, icon: Int?, enabled, onClick, description: String? = null)`**: the Mediacenter remote key.
   - `Button` with container `surfaceContainerHigh`, content `onSurface`, height 56 dp, `CircleShape`, **no border**.
   - Icon 20 dp in `onSurfaceVariant`, 8 dp gap, `labelLarge`. Disabled state: M3 defaults (38%).
5. **`MediaIconButton(icon, label, onClick, enabled, emphasized = false)`**: 48 dp `FilledTonalIconButton`. Neutral = `surfaceContainerHigh` / `onSurface`. Emphasized = `secondaryContainer` / `onSecondaryContainer`. Always has a `contentDescription`.
6. **`DeviceSelector(name, connected, size: SelectorSize, onClick)`**: the Mediacenter selector. Always one line and keeps `testTag("target-selector")`.
   - `Large` (Remote tab): height 48 dp, `CircleShape`, `surfaceContainerHigh`, padding start 8 dp / end 12 dp.
     - Leading: a 32 dp `secondaryContainer` circle with `ic_tv`.
     - Then: the name in `titleMedium` (one line, ellipsis, `weight(1f, fill = false)`), `ic_wifi` / `ic_wifi_off` at 18 dp in `onSurfaceVariant` with contentDescription "Connected" / "Not connected", and `ic_keyboard_arrow_down` at 18 dp. The width wraps its content.
   - `Compact` (Library and Settings headings): height 36 dp, same shape and color, no circle. A 16 dp `ic_tv` in `onSurfaceVariant`, the name in `labelLarge`, then the chevron.
7. **`PillSearchField(value, onValueChange, placeholder)`**: `TextField` with `CircleShape`, container `surfaceContainerHigh` (focused and unfocused), indicators `Transparent`, leading `ic_search` (new drawable, Material Symbols "search"), height 56 dp, `singleLine`.

Add `@Preview`s for each in `RemoteTheme(AppTheme.SoftDark)` and `RemoteTheme(AppTheme.WarmLight)`.

### Task 4 — Shared `DirectionPad` cleanup

**File:** `ui/RemoteButtons.kt` (`DirectionPad`, `DirectionKeys`). This component is already shared by both targets, so a change here does not merge any styles.

Reference: `IMG/after/remote-mediacenter-dark.png`, `IMG/after/remote-lg-tv-dark.png`, annotated MC finding 6.

1. **Delete the `Canvas` with the two diagonal `drawLine`s** and the constant `DIRECTION_DIVIDER_INSET_FRACTION`.
2. Container `surfaceContainerHigh`. Keep `border = BorderStroke(1.dp, outlineVariant)`. Set `shadowElevation = 0.dp`. Keep size 220 dp.
3. Arrow icons 28 dp (today 34 dp) in `onSurfaceVariant`. Keep the hit-area sizes and all `RemoteKey` press/repeat logic.
4. OK: 88 dp `primaryContainer` circle, `shadowElevation = 0.dp`, surrounded by a 6 dp `surfaceContainerHigh` gap ring and a 1 dp `outlineVariant` ring (`Modifier.border` on a 102 dp `Box`). The label is "OK" in `titleLarge` in `onPrimaryContainer`.

Tests: `RemoteScreenTest` and `ExistingTvRegressionTest` (the `direction-pad` tag and the "OK"/"Up"/… descriptions are unchanged).

### Task 5 — Target picker and connection dialogs

**Files:** `ui/TargetSelector.kt`, `ui/MainScreenRoutes.kt` (`TvConnectionDialog`), `ui/mediacenter/MediacenterSetupDialog.kt` (`MediacenterConnectionActions` only).

Reference: `IMG/after/device-picker-dark.png`, `IMG/before/mc-device-dialog.png`, `IMG/before/lg-device-dialog.png`.

1. **Selector rendering in `TargetSelector`:**
   - Mediacenter branch: replace `CompactTargetSelector` with `DeviceSelector` (Task 3.6). The size comes from a new parameter `compact: Boolean` that callers pass (Task 8).
   - LG branch: **keep the `OutlinedButton`** with "Change device" / name / status. Only change its type styles to the 3.2 roles it already uses (`labelMedium`, `titleLarge`, `bodySmall`), which happens automatically through the theme. No other LG change.
2. **Picker, opened from Mediacenter** (`state.selected == Mediacenter`): replace `TargetSelectionDialog` with a `ModalBottomSheet` (default sheet color and shape, drag handle shown).
   - Title "Control device" in `titleLarge`, 24 dp horizontal padding.
   - One row per `ControlTarget.entries`: a 40 dp `surfaceContainerHigh` circle with `ic_tv`, the name in `titleMedium`, and "Connected" / "Not connected" in `bodyMedium` `onSurfaceVariant`.
   - The selected target's row has a `secondaryContainer` background (`RoundedCornerShape(18.dp)`), `onSecondaryContainer` content, a 40 dp leading circle in `primaryContainer`, and a trailing `ic_check`. Tapping a row calls `onSelect(target)`.
   - Then a 1 dp `outlineVariant` divider with 12 dp side insets.
   - Then the row "`${state.selected.label} connection`" (same text) with leading `ic_settings` and a trailing chevron, calling `onConnection()`.
   - No "Close" button. Any dismissal calls `onDismiss` once.
3. **Picker, opened from LG** (`state.selected == LgTv`): keep the `AlertDialog` (LG style). Only fix its colors:
   - Target names in `onSurface` (not `primary`), status in `bodySmall` `onSurfaceVariant`, and the selected target followed by a 18 dp `ic_check`.
   - The "… connection" button and "Close" stay as text buttons.
4. **`TvConnectionDialog` (LG):** keep the `AlertDialog`. The status line goes in `bodyMedium` `onSurfaceVariant`. "Reconnect" stays a text button. "**Forget TV**" uses `error` content color. Same callbacks and order.
5. **`MediacenterConnectionActions`** (Mediacenter setup dialog): restyle its buttons only. Primary action = filled `Button`, "Remove computer" = `TextButton` in `error`, "Try again" = `TextButton`. Do not convert this dialog to a sheet; it carries setup and permission flow.

Tests: update `ControlTargetTest`, `TargetAppShellTest` and `PhoneUsabilityTest` where they assume an `AlertDialog` or a "Close" node for Mediacenter. Add tests:
- Opening the picker from Mediacenter shows a sheet with no "Close" node.
- Opening it from LG still shows "Close".
- Selecting a target calls `onSelect` once in both cases.

### Task 6 — Mediacenter page chrome and navigation

**Files:** `ui/mediacenter/MediacenterPageChrome.kt`, `ui/mediacenter/MediacenterTargetPage.kt` (`MediacenterTargetContent`).

Reference: `IMG/after/settings-dark.png`, `IMG/after/library-photos-dark.png`, `IMG/after/vpn-dark.png`, annotated VPN finding 1, annotated Library finding 1.

1. `MediacenterTargetContent`:
   - Horizontal padding 20 dp → `RemoteDimens.ScreenGutter` (16 dp) on both columns.
   - Stop rendering `header()` as a separate block. Pass it into `MediacenterPageHeading`'s new `selector` slot. Keep the `header` parameter on `MediacenterTargetPage` so callers do not change.
   - The caller (where `TargetSelector` is passed as `header`) passes `compact = destination != Remote`.
2. `MediacenterPageHeading(destination, section, onBack, selector, actions, attention)`:
   - **Remote tab:** no title. Row = `selector` (Large) + spacer + `actions` (Queue, Task 7) + `attention`.
   - **Library / Settings overview:** title (`headlineSmall`) + spacer + `actions` + `attention` + `selector` (Compact), all on one row, min height 64 dp.
   - **Settings sub-page:** back `IconButton` + a column with the section name in `titleLarge` and "Mediacenter" in `bodyMedium` `onSurfaceVariant` + spacer + `actions` + `attention`. **No selector on sub-pages.**
   - `MediaRecoveryStatus` (attention) stays visible on every page.
3. **Scroll edge:** wrap the heading in `Surface(color = …)`. The color is `background` when `contentScroll.value == 0`, otherwise `surfaceContainer`, animated with `animateColorAsState(tween(150))`.
4. `MediacenterBottomNavigation`:
   - Container `surfaceContainer`. Indicator `secondaryContainer` (today `Color.Transparent`).
   - Selected icon `onSecondaryContainer`, selected label `onSurface`. Unselected icon and label `onSurfaceVariant`.
   - Labels `labelMedium`. Keep `windowInsets = WindowInsets(0)` and the tags.
5. **Do not touch `RemoteTab.kt` / `RemoteAppShell`** (LG navigation keeps its vermillion indicator and top border).

### Task 7 — Mediacenter Remote tab

**Files:** `ui/mediacenter/MediacenterTargetPage.kt` (`TargetRemote`), `ui/mediacenter/NowPlayingControls.kt`, `ui/mediacenter/RendererControls.kt`, `ui/mediacenter/StreamingServices.kt`, `ui/mediacenter/PhotoControls.kt`, `ui/mediacenter/BrowserControls.kt` (styling only). New drawables: `ic_queue_music.xml`, `ic_menu.xml`, `ic_pause.xml`, `ic_stop.xml`, `ic_skip_previous.xml`, `ic_skip_next.xml` (Material Symbols, 24 dp, same format as existing `ic_*.xml`).

Reference: `IMG/after/remote-mediacenter-dark.png`, `IMG/after/remote-mediacenter-light.png`, `IMG/annotated/remote-mediacenter.png`, `IMG/before/mc-remote.png`.

Today's behavior (keep every condition):

- `transport-controls` holds either:
  - `NowPlayingControls`: title, Play/Pause, Stop, Queue, Previous/Next, `PlayerAdjustments`; or
  - `PhotoControls` + "Queue" in photo mode.
- "Menu" is always shown; "Playback" only when `activity.sessionId != null`.
- `StreamingServices`: "Apps unavailable" and the logo row.
- Then either `RendererControls` (notice, "Controls unavailable", D-pad, Back, "Search on computer") or `BrowserControls`.

New layout:

1. **Queue → heading `actions`:**
   - A `MediaIconButton(ic_queue_music, "Queue", emphasized = true)` calling `media.queue(true)`.
   - It is shown exactly when today's Queue button is shown: `NowPlayingControls`' `hasQueue || session != null`, or photo mode. Extract that condition into a function `queueAvailable(state)` in `NowPlayingControls.kt` so both places use it.
   - In the same change, remove the "Queue" `TextButton`s from `NowPlayingControls` and from `TargetRemote` (photo branch). Queue must exist exactly once.
2. **Now playing card** (only when `NowPlayingControls` renders today): a `Surface(surfaceContainer, RoundedCornerShape(22.dp))` with 16 dp padding and `spacedBy(12.dp)`.
   - Title in `titleLarge` (`mediaTitle(...)`), one line plus ellipsis.
   - A row of `MediaIconButton`s, with exactly the same enabled conditions and commands:
     - Previous (`ic_skip_previous`, only when `queueSummary.sessionId != null`)
     - **Play/Pause** as a 56 dp `FilledIconButton` (`primary` / `onPrimary`, `ic_play_arrow` / `ic_pause`, contentDescription "Play" / "Pause")
     - Next (`ic_skip_next`, same condition as Previous)
     - Stop (`ic_stop`)
   - `PlayerAdjustments` goes below the row, using `ListRow`s and switches.
   - Photo mode: the same card wrapping `PhotoControls`.
3. **Streaming** (`StreamingServices`):
   - When none can launch, show one line with 16 dp `ic_error` in `onSurfaceVariant` and "Apps unavailable" in `bodyMedium` `onSurfaceVariant`.
   - Tiles: replace the `OutlinedButton` with a `Surface(onClick, shape = 18.dp, color = surfaceContainerHigh)` at 64 dp, **no outline**. The image fills the tile with 0 dp padding, clipped to 18 dp. Keep `DISABLED_APP_ALPHA` and the contentDescriptions.
   - The row gets an end content padding of 16 dp and a right-edge fade: `Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent { drawContent(); drawRect(Brush.horizontalGradient(0.85f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn) }`.
4. **Vertical balance:** when the content is shorter than the viewport, put a `Spacer(Modifier.weight(1f))` above the D-pad block inside a `Column(Modifier.fillMaxHeight())`. When it is taller, scroll normally. Use `BoxWithConstraints` + `heightIn(min = maxHeight)` on the inner column.
5. **D-pad** (`RendererControls`): uses the shared `DirectionPad` (Task 4). "Controls unavailable" and the notice text ("Not confirmed" / "Try again") become one `bodyMedium` `onSurfaceVariant` line above the pad.
6. **Back | Menu row** directly under the pad, as an `AdaptivePair` of two `MediaKey`s:
   - **Back** (`ic_undo`) calls `controller.back()` with the same enabled rule.
   - **Menu** (`ic_menu`) runs `controllers.browser?.endGesture(); media.command(RemoteCommand.ActivityShowMenu(Empty))` with the same enabled rule.
   - **Playback** (conditional) is a full-width `MediaKey` (`ic_play_arrow`) under the pair, with the same command and condition.
   - Implementation: `RendererControls` gets a `bottom: @Composable () -> Unit = {}` slot rendered right under the pad. `TargetRemote` passes the row through it. Delete the old outlined Back from `RendererControls` and the outlined Menu/Playback from `TargetRemote`. Each action exists once.
7. **"Search on computer"** (conditional) uses `PillSearchField` with the same composing/committed logic, under the Back/Menu row.
8. **`BrowserControls`** (browser mode): swap its buttons to `MediaKey` / `MediaIconButton`. No structural change.

Tests: `CompanionScreensTest`, `PhoneUsabilityTest` and any test that clicks "Queue", "Menu", "Back" or "Playback" (texts and descriptions are unchanged). Add tests:
- Queue appears once when a session exists, and zero times when idle with an empty queue.
- Back and Menu each appear once.

### Task 8 — Mediacenter Settings overview

**Files:** `ui/mediacenter/SettingsNavigation.kt` (`SettingsOverview`), `ui/mediacenter/TargetSettings.kt`, `ui/mediacenter/VpnSettingsScreen.kt` (extract `vpnStateLabel()`).

Reference: `IMG/after/settings-dark.png`, `IMG/after/settings-light.png`, `IMG/before/mc-settings.png`.

1. `SettingsOverview`: one `ListGroup` with six `ListRow`s, in `SettingsSection.entries` order, each keeping `testTag("settings-section-${section.name}")`.
   - Leading: the existing `section.icon` in the tonal circle. **All six rows look the same**: remove the `subdued` special case for Advanced.
   - Trailing: the chevron. Delete the `HorizontalDivider`s.
2. Supporting text: add `internal data class SettingsSummaries(val sound: String?, val vpn: String?, val media: String?, val devices: String?, val appearance: String?, val advanced: String?)` and build it in `TargetSettings` from data already in `MediaTargetState`. Null means a one-line row. Never show placeholders.

   | Row | Value (single value, no separators) | Source |
   | --- | --- | --- |
   | Sound | selected output label, e.g. "TV (HDMI)" | `soundOutputLabels(...)` for the selected output in `model.sound` |
   | VPN | "Connected" / "Connecting…" / "Disconnected" / "Couldn't connect" | new `vpnStateLabel(state.settings.vpn?.state)` extracted from `VpnSettings` (returns null for unknown) |
   | Media | "1 folder" / "N folders" | folder or source state count, if present in `model` |
   | Devices | "1 phone" / "N phones" | `settings.devices?.items?.size` |
   | Appearance | "Dark" / "Light" (computer theme) | `settings.native?.theme` with the same label transform as `ComputerAppearance` |
   | Advanced | "Starts automatically" only when auto-start is on | the flag `LifetimeSettings` reads |

3. **Page-level refresh → heading `actions`** (`RefreshAction`, same labels):
   - Library: `library::refresh` ("Refresh library")
   - Sound: `audio.retry(true)`
   - Advanced: `media::refreshSettings`

   Remove the in-content `RefreshAction` for those three (`LibraryScreen` chip row, `SoundOptions`, `SettingsScreen` Advanced branch). **List-level** refresh (Media: Folders and USB drives) stays with its list, in the `SectionLabel` trailing slot.

Tests: the simple-settings tests (six rows, back, offline navigation, large text). Add an assertion that no overview text contains "·" or "•".

### Task 9 — Mediacenter Settings sub-pages

Reference: `IMG/after/vpn-dark.png`, `IMG/after/vpn-light.png`, and every `IMG/before/mc-settings-*.png`.

General rules:
- No floating orange `TextButton`s. Convert each one to a `ListRow` (navigation or "add" actions) or a proper button tier (commands).
- Group content in `ListGroup`s, each with a `SectionLabel` above it.

**9.1 Sound** (`SoundOptions.kt`, `AudioSettings.kt`; `IMG/before/mc-settings-sound.png`)
- Outputs: `ListGroup` rows with a trailing `RadioButton` (`primary`). The whole row is clickable and calls the same `choose`.
- **Test sound**: a filled `Button` (primary) with `ic_volume_up`, left-aligned under the group. Same callback and confirmation flow.

**9.2 VPN** (`VpnSettingsScreen.kt`, `SettingsScreen.VpnServicePreferences`; `IMG/after/vpn-*.png`)
- **Status card**: `Surface(surfaceContainerHigh, RoundedCornerShape(26.dp))`, 16 dp padding, `spacedBy(14.dp)`.
  - Row: a 44 dp `secondaryContainer` circle with a globe icon (new `ic_public.xml`), then `vpnStateLabel()` (fall back to "Unavailable" as today) in `titleLarge` and `state.vpn?.country` in `bodyMedium` `onSurfaceVariant`.
  - **Country**: replace the `TextButton` + `DropdownMenu` with `ExposedDropdownMenuBox` + `OutlinedTextField(readOnly = true, label = { Text("Country") }, shape = RoundedCornerShape(14.dp))`.
    - Value: `countries.firstOrNull { it.id == selected }?.label ?: "Automatic"`.
    - Same items (Automatic first), same `selected` state, same `enabled && state.countries.isNotEmpty()`.
  - Buttons (`AdaptivePair`), both `CircleShape`, 48 dp, with the same enabled rules and existing confirmation:
    - **Connect**: filled `Button`, sends exactly `VpnPrepareChange(CONNECT, selected)`.
    - **Disconnect**: `OutlinedButton`, sends `VpnPrepareChange(DISCONNECT)`.
- **VPN for apps**: `SectionLabel("VPN for apps")`, then a `ListGroup` of 56 dp rows.
  - Leading: the service logo via `serviceIcon(id)` (make it `internal` in `StreamingServices.kt`), 36 dp, 10 dp corners.
  - Title: `service.label`. Trailing: the existing `Switch` with the same semantics and command.

**9.3 Media** (`SourcesScreen.kt`, `DrivesScreen.kt`, `FolderBrowser.kt`; `IMG/before/mc-settings-media.png`)
- `SectionLabel("Folders", trailing = RefreshAction)`, then a `ListGroup`:
  - First row **Add folder** (leading `ic_add`), same callback and enabled rule.
  - Then one row per folder: title = folder name; supporting "Offline" only when offline today; trailing the existing `ItemActions`.
- `SectionLabel("USB drives", trailing = RefreshAction)`, then a `ListGroup` of drive rows. Empty state: a single row "No USB drive" in `bodyMedium` `onSurfaceVariant` with no leading icon.
- `FolderBrowser` keeps its bounded, scrollable behavior. Restyle only its rows and buttons.

**9.4 Devices** (`SettingsScreen.PairedPhones`, `PhoneSettingsEntry.kt`, `PairingDisplay.kt`; `IMG/before/mc-settings-devices.png`)
- A `ListGroup`:
  - One row per paired phone: leading phone icon (new `ic_smartphone.xml`), title `device.label`, trailing the existing `ItemActions` ("Remove" in `error` color inside the menu).
  - Then **Add phone** (leading `ic_add`), same command.
- A second `ListGroup` with **Connection** (leading `ic_settings`, chevron), same `onConnection` and contentDescription "Mediacenter connection".

**9.5 Appearance** (`SettingsScreen.ComputerAppearance`, `PhoneSettingsEntry.kt`; `IMG/before/mc-settings-appearance.png`)
- `SectionLabel("Computer")`, then a full-width `SingleChoiceSegmentedButtonRow` with one `SegmentedButton` per `Theme.entries`, using the existing labels and the same "send only if different" logic and `enabled`.
- `SectionLabel("Phone")`, then a `ListGroup` with the row "Phone appearance" (chevron). Supporting = the current phone theme label only if it is available in scope. It calls the same `onAppearance` and keeps contentDescription "Phone appearance".

**9.6 Advanced** (`LifetimeSettings.kt`; `IMG/before/mc-settings-advanced.png`)
- A `ListGroup`:
  - Row "Start automatically" with a trailing `Switch` (same semantics and command).
  - Row "Stop playback" with leading `ic_stop` and title and icon in `error`, with the same command and enabled rule and no chevron.

### Task 10 — Mediacenter Library

**Files:** `ui/mediacenter/LibraryScreen.kt`, `ui/mediacenter/LibraryItemRow.kt`, `ui/mediacenter/PlaylistScreen.kt`.

Reference: `IMG/after/library-photos-dark.png`, `IMG/after/library-photos-light.png`, `IMG/annotated/library-photos.png`, `IMG/before/mc-library-photos.png`, `IMG/before/mc-library-photos-scrolled.png`.

1. **Order:** search field first, kind chips second. `PillSearchField` (placeholder "Search") replaces the `OutlinedTextField`, with the same `actions.search(it, state.kind)` callback. Use `PillSearchField` in `PlaylistScreen` too.
2. **Kind chips:** keep the `FilterChip`s and their callback. Selected: `secondaryContainer` with a leading 18 dp `ic_check`. Unselected: 1 dp `outline` border. `spacedBy(8.dp)`. The `RefreshAction` leaves this row (Task 8.3).
3. **Playlists** (Music): a `ListGroup` with one row "Playlists" (leading `ic_format_list_bulleted`, chevron), same callback and enabled rule.
4. **`LibraryNotice`:** keep the `LinearProgressIndicator`. Same strings, in `bodyMedium` `onSurfaceVariant`.
5. **Video and music rows** (`LibraryItemRow`):
   - Thumbnail 96×54 dp, 12 dp corners, `surfaceContainerHigh` fallback with a 20 dp icon.
   - Title `titleMedium`, up to two lines. "Offline" in `bodyMedium` `onSurfaceVariant`.
   - Padding 12 dp vertical, gap 16 dp. The music overflow is unchanged.
6. **Photo grid:**
   - `chunked(2)` → `chunked(3)`, `spacedBy(3.dp)` in both directions, inside a container clipped to 16 dp. Tiles have no individual corners and keep `aspectRatio(1f)`. Keep `photoTitle()` for semantics.
   - `MediaThumbnail` fallback: `surfaceContainer`, a 22 dp `ic_image` at 70% alpha centered, and the title in `labelSmall`, max 2 lines, **8 dp inner padding**, bottom-start aligned.
7. **Pagination** (`LibraryPages`): an `AdaptivePair` of `MediaKey`s.
   - "Previous" (`ic_keyboard_arrow_left`) when `hasPrevious`, "Next" (`ic_keyboard_arrow_right`) when `cursor != null`.
   - Same enabled rules and callbacks. contentDescriptions "Previous page" / "Next page".

Tests: library tests in `CompanionScreensTest` and `PhoneUsabilityTest`. Add a test that a fallback photo title has a nonzero start inset inside its tile.

### Task 11 — LG Remote fixes (keep LG style)

**Files:** `ui/RemoteScreen.kt` (`RemoteHeader`, `RemoteControls`, `TransportShortcuts`, `ExpandedTransport`), `ui/RemoteButtons.kt` (`RemoteAction`, `Rocker`), `ui/TvAppsSheet.kt` (`TvAppTile`, `TvAppsStrip`), `ui/ThemeSettings.kt` (`SettingsButton`). New drawables: `ic_open_in_new.xml`, `ic_contrast.xml` (Material Symbols).

Reference: `IMG/after/remote-lg-tv-dark.png`, `IMG/after/remote-lg-tv-light.png`, `IMG/annotated/remote-lg-tv.png`, `IMG/before/lg-remote.png`, `IMG/before/lg-remote-apps-scrolled.png`.

**Keep exactly:**
- The outlined "Change device" selector.
- The bordered 16 dp `RemoteAction` shape (container `surfaceContainerHigh` after Task 1).
- Bordered rockers and app tiles.
- The order of all controls.
- `RemoteAppShell` navigation.

Changes:

1. **Header** (`RemoteHeader`):
   - `SettingsButton` becomes a 48 dp `OutlinedIconButton` with `BorderStroke(1.dp, outlineVariant)` (same as the power button) and the icon `ic_contrast` in `onSurface`. Keep contentDescription "Settings" and the callback.
   - The power button is unchanged.
   - Fallback branch (no `targetSelector`): delete the `Canvas` status dot and both color constants. Keep "Connected" / "TV unavailable" in `bodyMedium` `onSurfaceVariant`.
2. **Color Dock button** (`RemoteControls`): icon `ic_grid_view` → `ic_open_in_new`. Same label and `onOpenDock`.
3. **`RemoteAction` vertical tiles** (annotated finding 4):
   - When `vertical = true`, the button has a **fixed** `height(RemoteDimens.TileHeight)` (64 dp) instead of `heightIn(min = 56.dp)`.
   - The artwork slot is a `Box(Modifier.height(24.dp), contentAlignment = Center)`.
   - "123" uses `titleMedium` instead of `titleLarge`.
   - Labels use `labelMedium`, max 1 line, ellipsis.
   - When `LocalDensity.current.fontScale > 1.4f`, drop the fixed height back to `heightIn(min = 64.dp)` so large text is not clipped.
4. **Horizontal `RemoteAction`:** `labelLarge`, height `heightIn(min = RemoteDimens.KeyHeight)`. Keep the border.
5. **`Rocker`** (annotated finding 5): add a `label: String` parameter ("VOL" for TV volume, "CH" for channel). Replace the centre `DockIcon(icon)` with a `Column` holding the icon (20 dp, `onSurfaceVariant`) over the label (`labelMedium`, `onSurfaceVariant`, letterSpacing 0.08.em). Keep `title` for semantics and the border.
6. **`TvAppsStrip`** (annotated finding 6):
   - Keep the "TV apps" `Text` (`titleMedium`, `onBackground`, as today).
   - `TvAppTile(iconOnly = true)`: keep the border and 16 dp shape. Inner padding 8 dp → 4 dp. Clip the artwork to `RoundedCornerShape(12.dp)` so full-bleed logos fill the tile.
   - Add the same right-edge fade as Task 7.3 to the `LazyRow`.
   - `RemoteControls` bottom padding 24 dp → 32 dp so the navigation bar never overlaps the strip.
7. `ExpandedTransport` (large text / narrow) keeps its stacked layout and gets the same component changes.

Tests: `RemoteScreenTest`, `ExistingTvRegressionTest`, `TvAppsScreenTest`, `TvAppsTransportTest`. Add tests:
- The four transport tiles (Mute TV, Numbers, Live TV, All apps) have equal heights at font scale 1.0 and 1.15.
- The rockers expose "VOL" and "CH" text.

### Task 12 — LG Wallpapers and Color Dock tabs (theme only)

**Files:** `ui/WallpaperScreen.kt`, `ui/WallpaperGallery.kt`, `ui/DockScreen.kt`.

Reference: `IMG/before/lg-wallpapers.png`, `IMG/before/lg-color-dock.png`, `IMG/before/lg-appearance-dialog.png`.

- No layout or component changes (D1). These screens inherit Task 1–2 colors and type automatically.
- The header's settings icon picks up the Task 11.1 change automatically.
- Check these screens in both themes and record that no container disappeared after the `surface` sweep.

### Task 13 — Remaining sheets and dialogs

- **Mediacenter sheets** (style pass with Task 3 components): `ConfirmationSheet.kt`, `QueueSheet.kt`, `PlaylistScreen.kt`, `PairingDisplay.kt`.
  - Rows → `ListRow`. Primary confirmation → filled `Button`. Cancel → `TextButton`. Destructive → `error`.
  - Keep `queue-options-<id>` and `shuffle-settings`.
- **LG sheets** (theme only, no structural or component change): `NumberPadSheet.kt`, `TvAppsSheet.kt` (`TvAppsSheet`, picker), `AppShelfScreen.kt`, `WallpaperDetail.kt` (`WallpaperDeleteDialog`: confirm text in `error`).
  - Keep `app-picker-list`, `tv-app-sheet` and `app-shelf-entry`.

### Task 14 — Verification, device QA and evidence

1. Format, then run all gates:
   - `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon`
   - plus the Detekt and ktlint gates used in `docs/superpowers/plans/2026-11-16-simple-settings.md`.
2. Run the rendered APK tests (`:app:connectedDebugAndroidTest`), including `DesignCaptureTest`, on the Pixel 9.
3. Update the installed original app **in place** with the same signer. Verify the credential hashes are unchanged (same procedure as `design-qa.md`).
4. On the unlocked Pixel 9 at **font scale 1.15**, in **dark and light**, for **both targets**, capture:
   - Mediacenter: Remote, Library (all three kinds), Settings overview, every section page, device picker.
   - LG: Remote, apps strip, device picker dialog, TV connection dialog, Wallpapers, Color Dock.

   Do not change the user's font scale.
5. Compare each capture side by side with its `IMG/after/*` mockup and its `IMG/before/*` original, and list P0/P1/P2 differences.
   - P0: lost function or unreadable text.
   - P1: wrong hierarchy, color role or spacing, **or LG and Mediacenter accidentally made to look the same**.
   - P2: polish.
   - Fix all P0 and P1 issues. Go through all four `IMG/annotated/*` images and confirm every finding marked **Fix** in 2.1 is gone, and every finding marked **Keep** is unchanged.
6. In a test (not on the user's phone), check font scale 1.4: `AdaptivePair` and `ExpandedTransport` stack, nothing clips, and all six settings rows fit.
7. Save private captures under `Mediacenter/.superpowers/sdd/2026-11-16-remote-redesign/installed/`. Publish only non-private crops (no media titles or filenames) to `Mediacenter/docs/engineering/evidence/remote-control/visual-redesign-2026-11-16/`.
8. Update `design-qa.md` (new section "Visual redesign QA"), the Roboto Flex license note and the engineering checkpoint. Commit only task-owned files.

---

## 5. Review focus (whole-diff review)

- Every callback from the "before" UI still exists and is reachable. Queue, Back, Menu and Playback **moved** on Mediacenter; each must exist exactly once.
- Confirmation, uncertainty and pending guards are untouched. `MediaRecoveryStatus` is visible on every Mediacenter page.
- **LG keeps its own identity:** outlined three-line selector, bordered 16 dp keys and tiles, vermillion nav indicator. No Mediacenter component (`MediacenterDesign.kt`) is imported by any LG file. Verify with `grep -rn "MediaKey\|ListRow\|DeviceSelector\|MediaIconButton" app/src/main/java/com/emmah/colordock/remote/ui/*.kt` (top-level LG package): only `TargetSelector.kt` may reference `DeviceSelector`.
- No `colorScheme.surface` is left as a raised-container color.
- No middle dots, no colored status dots, no captions under app icons.
- Contrast test is green in both schemes. Roboto Flex is bundled, licensed and actually applied (check a capture, not just the code).
- Large text (1.4×) and narrow width (< 300 dp) still stack correctly. LG tiles grow instead of clipping above 1.4×.
- Offline state: overview navigation works, mutating controls stay disabled, and disabled keys are visibly disabled.
