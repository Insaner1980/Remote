# Remote Visual Polish Plan (post-redesign audit)

> **For agentic workers:** Use superpowers:executing-plans. Work task by task, in order. This is a **visual-only polish pass** on top of the finished 2026-11-16 redesign (`2026-11-16-remote-visual-redesign.md`, built at `975c48c`, last commit `64e4788`). Every callback, command, confirmation guard, pairing, preference, content description and test tag must keep working exactly as today. **Do not change the palette:** no new colors and no changed ARGB values in `AppTheme.kt`. Using an existing color role in a new place is allowed only where a task says so.

**Source of findings:** a critical review of 196 screenshots taken on 2026-11-17 (`/home/emma/Pictures/Remote-2026-11-17/`, Pixel 9, 1080 × 2424, 420 dpi, font scale 1.15, both themes, plus test-fixture captures at 320/400/411 dp and font scales 1.0/1.15/2.0).

**Annotated images (private, git-ignored):** `/home/emma/Dev/Mediacenter/.superpowers/sdd/2026-11-17-remote-polish/annotated/`. Relative from this file: `../../../../Mediacenter/.superpowers/sdd/2026-11-17-remote-polish/annotated/`. The numbers on the images are the finding numbers below. **Open the image for a task before you edit that task's files.**

| Image | Findings shown |
| --- | --- |
| `A-lg-main.png` | 3, 4, 5, 12, 22 |
| `B-lg-large-text-and-sheets.png` | 1, 19, 20, 21 |
| `C-mc-main.png` | 7, 8, 15, 18, 27, 28, 30 |
| `D-mc-components.png` | 2, 6, 9, 24 |

**Note on fixture captures:** some `Testiaineisto` images render a single component without its host's padding or insets (for example `folder-offline.png`, `setup-*.png`, `color-dock-connecting.png`). Edge-to-edge content in those images is **not** a finding. Spacing *inside* those components is.

---

## 0. Constraints (unchanged from earlier plans)

1. Original app only: `com.emmah.colordock.remote`, named Remote. Install in place with the same signer. Never uninstall or clear data. Preserve both encrypted pairings and all preferences.
2. All rules in `AGENTS.md`: no middle-dot or bullet separators, icon-only app strips, concise labels, secondary actions in overflow menus, compact one-line Mediacenter selector, six-row Settings overview.
3. **LG and Mediacenter keep separate visual styles** (decision D1 of the 2026-11-16 plan). LG: outlined three-line selector, bordered 16 dp keys and tiles, vermillion navigation indicator with a top border line. Mediacenter: pill-shaped, borderless, tonal components from `MediacenterDesign.kt`. Do not import Mediacenter components into LG files.
4. Keep every existing `testTag` (full list in the 2026-11-16 plan, section 1).
5. Respect the user's font scale. Keep the existing large-text fallbacks (`LARGE_TEXT_FONT_SCALE`, `APP_ROW_FONT_SCALE`, `STACKED_CHOICE_FONT_SCALE`).
6. **No copy changes** unless a task names the exact string. Copy issues found during the audit are listed in section 5 for the owner and are out of scope.
7. Visually inspect both targets on the actual phone before reporting any task complete.

---

## 1. Decisions

**Confirmed by the owner on 2026-11-17: use the default for OD1, OD2 and OD3.** Start without further approval questions.

- **OD1 (Task 9, number pad):** digit keys use LG's neutral bordered key style, and the primary color is reserved for "Open Live TV and tune". *Default: yes.* Alternative: keep the ten filled salmon/rust keys and only fix digit size.
- **OD2 (Task 6, LG primary buttons):** unify every LG filled call-to-action to the pill shape. Each button keeps its current color. *Default: shape only.* Alternative: also move "Add from phone" and "Start clock mode" from vermillion to `primary`, so LG has one filled-button color.
- **OD3 (Task 7, Mediacenter header):** the device selector sits on the left on the Remote tab and on the right on Library and Settings, so it jumps when you switch tabs. *Default: keep the positions and make the selector look identical on all three tabs.* Alternative: give the Remote tab a "Remote" heading on the left and move the selector to the right, as on the other tabs.

---

## 2. Findings

Priority **A** = visible defect. **B** = system inconsistency. **C** = component polish.

### A. Defects

1. **Bottom navigation at large text (both targets).** At font scale 2.0, labels break mid-word ("Wallpap / ers", "Wallpaper / s"), "Color Dock" is clipped at the screen edge, and the icons sit at different heights because the labels wrap differently. Files: `RemoteTab.kt` (`RemoteAppShell`), `mediacenter/MediacenterPageChrome.kt`.
2. **Browser keyboard dialog.** Keys are invisible: `MediaKey` uses `surfaceContainerHigh`, which is also the `AlertDialog` container color, so only bare words remain. The labels "Space", "Down" and "Backspace" break mid-word. The rows have 3, 2, 3, 2 and 1 items, so the grid is ragged. File: `mediacenter/BrowserControls.kt` (`browserKeyRows` and the keyboard dialog).
3. **LG navigation bar border on all four sides.** `Surface(border = BorderStroke(1.dp, outlineVariant))` draws visible vertical lines at the left and right screen edges, plus a bottom line. The intended look is a top line only. File: `RemoteTab.kt`.
4. **LG header on the Wallpapers and Color Dock tabs.** The theme button is top-aligned with the three-line selector instead of centered. It nearly touches the selector (about 4 dp, against 12 dp on the Remote tab). The selector's width and top edge jump between tabs. The Color Dock tab shows a divider under the header but the Wallpapers tab does not. Files: `RemoteScreen.kt` (`RemoteHeader`), `WallpaperScreen.kt`, `DockScreen.kt`.
5. **LG Remote resting state.** On the Pixel at font scale 1.15, the unscrolled Remote tab ends with the TV-app strip cut through by the navigation bar's top line. It looks like a layout error, not like a scrollable page. File: `RemoteScreen.kt` (`RemoteControls`).
6. **Playlists screen.** Its `Column` has no spacing, so "Back", the search field, the "Playlists" label, the list and "Refresh" touch each other. "Playlists" / "Choose playlist" is an unstyled `Text`. Navigation is a bare "Back" `TextButton`, while every other Mediacenter sub-page uses the chevron header. The playlist details view has the same problems, and its refresh icon sits orphaned under the list. File: `mediacenter/PlaylistScreen.kt`.
7. **Queue dialog.** It contains a lone refresh icon with no alignment partner, a `FilterChip` "Shuffle" next to a text-button "Repeat off" (two control types for two toggles), a text-button "Save playlist", and a **filled** "Close". Everywhere else, a dismiss-only button is a text button. File: `mediacenter/QueueSheet.kt`.
8. **Mediacenter Remote tab: empty void.** About 250 dp of nothing lies between the app strip and the direction pad. The pad stays at its LG size (about 220 dp) although the screen has room. File: `mediacenter/MediacenterTargetPage.kt` (`TargetRemote`), `RendererControls.kt` / `DirectionPad` sizing.
9. **Transport icons mix styles.** `ic_pause.xml` (and the stop square) are the *outlined* Material Symbols, while play, previous and next are *filled*. Pause therefore shows as two hollow bars. The transport row is left-aligned in the playback card. Files: `res/drawable/ic_pause.xml`, `ic_stop.xml`, `mediacenter/NowPlayingControls.kt`, `mediacenter/PhotoControls.kt`.

### B. System consistency

10. **Corner radii.** UI code uses 12 different radii: 4, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26 and 28 dp. On top of that, text fields and dropdown menus fall back to the theme's 4 dp `extraSmall`, so they look boxy next to pills and 16–22 dp cards (TV IP address, City or place, Channel number, Name, Port, Code, the VPN country menu, every overflow menu).
11. **Dialog rules are inconsistent.**
    - Some dismiss-only dialogs use a text "Close" (Control device, TV connection). Others use a filled "Close" (Queue, Add phone QR, QR unavailable).
    - Content left edges do not line up with the title: the Control device rows are inset about 14 dp, and the TextButtons inside the content (Reconnect, Forget TV, Find computer, Remove computer) are offset by their own 12 dp padding.
    - Widths vary: the Mediacenter Connection dialog is narrower than the others.
    - Large empty bands sit above the bottom button (Control device, Appearance, Shuffle options).
12. **Destructive actions are indistinguishable from normal actions.** In this palette `error` is almost the same hue as `primary` (dark: `#FFB4AB` vs `#FF9B75`; light: `#B3261E` vs `#A63C12`). In addition, "Forget TV", "Remove computer" and "Turn off" use `primary`. Destructive actions need a non-color cue.
13. **Back navigation has four patterns.**
    - Mediacenter settings: chevron + title + subtitle (good).
    - LG wallpapers and Color Dock sub-pages: a coral "↶ Wallpapers" / "↶ Color Dock" link above the title. It uses `ic_undo`, the same glyph as the TV remote's **Back key**.
    - Playlists: a bare "Back" text button.
    - Edit app shelf: no back affordance at all.
14. **Page title styles.**
    - "Connect your TV" uses `headlineMedium` (28 sp, regular, normal width). Every other page title uses `headlineSmall` / `titleLarge` (semibold, wide).
    - The "TV apps" sheet title is regular weight, while "Channel number" is semibold.
    - The LG Color Dock tab section headings ("Clock mode", "Weather", "Apps") are `titleMedium` with the same small gap above and below. They do not read as section breaks.
15. **Section spacing.** The space above a section label and the space below it are often equal, or inverted:
    - LG Color Dock tab: about 12 dp above and 12 dp below.
    - Mediacenter Media page: "Folders" to the first row is about 40 dp, more than between sections.
    - Mediacenter Devices: the gap between groups is about 36 dp instead of `SectionGap` (24).
16. **LG filled buttons have two shapes.** Pills: "Open Color Dock", "Use wallpaper", "Send to TV". 12 dp rectangles: "Add from phone", "Start clock mode". See OD2.
17. **Selected states.** The LG segmented control "My pictures | Built-in" uses a light beige thumb (`ChromeWhite`-like) on dark. It is the only light-on-dark selected element in the app and the brightest object on the Wallpapers page.
18. **Mediacenter device selector has two looks.**
    - Remote tab: the device icon in a filled circle, plus a Wi-Fi status icon.
    - Library and Settings: a smaller icon, no circle, no Wi-Fi icon.

    See OD3 for position.

### C. Component polish

19. **LG number pad.** Ten filled salmon (dark) / rust (light) digit pills form a wall of the strongest color, with no hierarchy. Digits are `labelLarge` (15 sp), which is small for a keypad. When "Delete" is disabled, its outline is almost invisible.
20. **LG Edit app shelf.**
    - There is no back affordance (finding 13).
    - "Preview: YouTube, Netflix, …" is a comma-separated sentence.
    - Each app is a 140 dp tall card with a switch and two text buttons, so only about five apps fit on screen.
    - No app artwork is shown, although `TvAppArtwork` exists.
21. **LG TV apps sheet.**
    - "Refresh" and "Close" float as two centered text buttons in a row under the title.
    - An app without artwork renders its long name as a 4-line paragraph that fills the tile, and the tile is shorter than its neighbours.
    - "Opening Netflix…" and "Could not open Netflix…" are bare body text with no icon or progress indicator.
22. **LG Control device and TV connection dialogs.**
    - Rows are indented relative to the title.
    - "LG TV connection" is coral text that reads like a third device row.
    - "Reconnect" and "Forget TV" are left-offset text buttons.

    The Mediacenter sheet version ("Mediacenter connection" row with leading icon and chevron after a divider) is the better pattern.
23. **Mediacenter Connection dialog.**
    - "Wi-Fi" appears twice (section label and "✓ Wi-Fi").
    - "✓" is a typed character, not the check icon used everywhere else.
    - The left edges zig-zag (label, check line, filled "Reconnect" pill, text buttons).
24. **Confirmation dialogs.** The value ("TV (HDMI)", "Example phone", "example.invalid") sits in a tonal rounded box that looks like an editable text field. The same box style appears in "Scan with the other phone" and "QR code unavailable". File: `mediacenter/ConfirmationSheet.kt`, `PairingDisplay.kt`.
25. **Pairing QR.** The caption pill butts against the QR image (about 2 dp gap). The QR has square corners inside a 28 dp dialog. File: `mediacenter/PairingDisplay.kt`.
26. **Browser remote action row.** Two round icon buttons and a "Keyboard" pill wrap onto two lines, with "Close tab" alone on the second line. File: `mediacenter/BrowserControls.kt`.
27. **Media settings rows.** "Add folder" has a leading icon circle but the folder rows do not, so the title text starts at two different x positions inside one group. File: `mediacenter/SourcesScreen.kt`.
28. **Advanced: "Stop playback".** A 12 dp stop square sits in a leading slot, so the label is indented about 24 dp relative to "Start automatically" in the same group. File: `mediacenter/LifetimeSettings.kt`.
29. **Library rows and tiles.** File: `mediacenter/LibraryItemRow.kt`, `LibraryScreen.kt`.
    - Music rows use a 16:9 placeholder, which suits video but not music.
    - The placeholder icon is about 16 dp inside a 96 × 54 dp box.
    - Photo fallback tiles place the icon in the top third, not centered in the space above the title.
    - The "Next" page button has its chevron *before* the label.
30. **Low-contrast artwork.**
    - The Mediacenter "Apps unavailable" strip fades artwork with alpha. White tiles (Netflix, YouTube) turn into grey slabs, and dark tiles almost vanish. In light theme, the white tiles vanish into cream.
    - LG light theme: the TV-supplied "APPS" and "Photo & Video" icons are white-on-transparent and nearly invisible on the cream tile.

    Files: `mediacenter/StreamingServices.kt`, `TvAppArtwork.kt`.
31. **Spacing inside setup and wallpaper flows.**
    - `MediacenterSetupScreen` stacks "Scan code", "Camera unavailable", "Enter address" and the fields with zero gaps.
    - LG "Choose location" (weather) and "Preview pictures / Complete" constrain the field, the buttons and the progress bar to about 70 % width for no reason.
    - "Weather data: Open-Meteo" is set in body-large, the same weight as the content.

    Files: `mediacenter/MediacenterSetupScreen.kt`, `MediacenterSetupDialog.kt`, the weather location screen in `DockScreen.kt` or its route, `WallpaperUploadPreview.kt`.

---

## 3. Design tokens for this pass

### 3.1 Shapes (one scale)

| Token | Value | Use |
| --- | --- | --- |
| `extraSmall` (theme) | **8 dp** (was 4) | Text fields, dropdown menus, tooltips |
| `small` (theme) | 8 dp | Filter chips |
| 12 dp | `RemoteShapes.Inner` (new constant) | Images inside cards, QR, artwork clip, wallpaper thumbnails |
| `medium` (theme) | 16 dp | LG keys, LG tiles, LG cards, LG selector |
| `large` (theme) | 22 dp | Mediacenter list groups, Mediacenter cards, app tiles (MC) |
| `extraLarge` (theme) | 28 dp | Dialogs, sheets |
| `CircleShape` | — | Pills, icon buttons, Mediacenter keys |

Replace every literal `RoundedCornerShape(10|14|18|20|24|26.dp)` with the nearest token above. Keep the two asymmetric list-row shapes (22/4) because they belong to the Mediacenter list group. Add an object, for example in `RemoteDimens.kt`:

```kotlin
internal object RemoteShapes {
    val Inner = RoundedCornerShape(12.dp)
}
```

(If `RemoteShapes` clashes with the private `RemoteShapes` in `AppTheme.kt`, rename the private one to `ThemeShapes`.)

### 3.2 Spacing

Use the existing `RemoteDimens`. Rules:
- Above a section label: `SectionGap` (24 dp). Below a section label: `ItemGap` (8 dp).
- Between controls inside one block: `ItemGap` (8 dp). Between blocks: `BlockGap` (12 dp).
- Dialog content uses one left edge: the title's. Rows inside dialogs use `contentPadding` with horizontal 0, or a `ListRow`-style row with the same start padding as the title.

### 3.3 Buttons in dialogs and sheets

| Role | Style |
| --- | --- |
| Affirmative action (Confirm, Save, Turn off) | Filled `Button` (pill) on the right |
| Dismiss only (Close, Done, Cancel) | `TextButton` |
| Destructive affirmative (Delete, Forget TV, Remove computer, Turn off, Remove, Eject, Stop playback) | Text or filled in the `error` role **plus a leading icon** (`ic_delete`, `ic_power`, `ic_close`, `ic_eject`, or the existing stop icon) |
| Secondary actions inside content | Full-width rows (icon + label + chevron), not loose TextButtons |

A dialog never has two filled buttons. A dismiss-only dialog has no filled button.

### 3.4 Icons

Use existing drawables where they exist (checked 2026-11-17). Short names used in this plan map to them as follows:

| Name in this plan | Use this drawable |
| --- | --- |
| `ic_power` | `ic_power_settings_new` |
| `ic_play` | `ic_play_arrow` |
| `ic_chevron_right` | `ic_keyboard_arrow_right` |
| `ic_tune` (connection row) | the icon the Mediacenter "Mediacenter connection" sheet row already uses (`ic_settings`) |
| `ic_phone` | `ic_smartphone` |
| `ic_language` | `ic_public` |
| `ic_vpn` | `ic_shield` |
| `ic_playlist_add` | `ic_queue_music` |
| `ic_refresh`, `ic_delete`, `ic_close`, `ic_wifi`, `ic_error`, `ic_search`, `ic_volume_up`, `ic_keyboard_arrow_*` | already exist |

**New** Material Symbols drawables (24 dp, the same vector format as the existing `ic_*.xml`; Rounded or Outlined to match their neighbours, except transport icons, which are **Filled**): `ic_arrow_back` (`android:autoMirrored="true"`), `ic_backspace`, `ic_keyboard`, `ic_eject`, `ic_folder`, `ic_folder_off`, `ic_music_note`. Replace the paths in `ic_pause.xml` and `ic_stop.xml` with the Filled variants (Task 8.2).

---

## 4. Tasks

### Task 1 — Navigation bars (findings 1, 3)

Files: `ui/RemoteTab.kt`, `ui/mediacenter/MediacenterPageChrome.kt`.

1. LG `RemoteAppShell`:
   - Remove `border` from the `Surface`. Draw only a top divider: `HorizontalDivider(thickness = 1.dp, color = outlineVariant)` above the `NavigationBar` inside a `Column`.
   - Keep the `bottom-navigation` tag on the outer container.
2. Both shells, `NavigationBarItem` labels: `Text(tab.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, style = labelMedium)`.
3. Both shells: cap the label's font scale so that "Wallpapers" and "Color Dock" fit at 320 dp. Wrap only the label in `CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = density.fontScale.coerceAtMost(NAV_LABEL_MAX_FONT_SCALE)))` with `NAV_LABEL_MAX_FONT_SCALE = 1.3f`. Icons and the indicator are unaffected. The label stays readable, and the system text-size setting still applies up to 1.3.
4. Tests: a Compose test at 320 dp, font scale 2.0 asserts that every nav label has `lineCount == 1` and that every item's icon top is equal. Keep `bottom-navigation`, `mediacenter-bottom-navigation` and `media-nav-<Destination>`.

Acceptance: `remote-320dp-2.0-top` and `remote-400dp-2.0` fixtures show one-line labels with no clipping, and no vertical lines at the screen edges in both themes.

### Task 2 — LG header and Remote resting state (findings 4, 5)

Files: `ui/RemoteScreen.kt` (`RemoteHeader`, `RemoteControls`), `ui/WallpaperScreen.kt`, `ui/DockScreen.kt`, `ui/ThemeSettings.kt`.

1. One header composable for all three LG tabs. The selector box has the same width, height and top inset on every tab. The trailing buttons are vertically **centered** on the selector, with a 12 dp gap. When the power button is absent (Wallpapers, Color Dock), its slot is **not** reserved: the selector grows. That is acceptable, as long as the top edge, height and center line do not change.
2. Use the same divider rule on every tab: either all three tabs show a 1 dp `outlineVariant` divider under the header, or none do. **Choose none.** The divider is only visible on Color Dock today and adds nothing.
3. Remote resting state. On a 411 dp × font 1.15 viewport (the Pixel), the unscrolled Remote tab must show the TV-app strip completely with at least 12 dp above the nav divider. Do this by tightening vertical gaps, not by shrinking controls:
   - Gap between header and LG Home row: 16 → 12 dp.
   - Gap between pad and Back/TV Guide row: 16 → 12 dp.
   - Bottom padding: keep 32 dp only when the content scrolls (`scrollState.maxValue > 0`). Otherwise use 16 dp.

   If it still does not fit, add a 24 dp bottom fade (`Modifier.drawWithContent` gradient to `background`) so the cut reads as scrollable.
4. Tests: header bounds (top, height, center Y of the trailing button) are equal across the three tabs at 411 dp / 1.15.

### Task 3 — Shapes sweep (finding 10)

Files: `ui/AppTheme.kt` (`extraSmall` 4 → 8 dp), all files with literal `RoundedCornerShape`.

1. Apply the table in 3.1. Text fields and menus pick up 8 dp from the theme. Do not set shapes per call site.
2. `grep -rn "RoundedCornerShape(" app/src/main/java` afterwards should list only: the theme, `RemoteShapes.Inner`, and the two asymmetric list-row shapes.
3. Screenshot both themes. Dropdown menus (VPN country, overflow menus) and every outlined field must show 8 dp corners.

### Task 4 — Dialog and sheet rules (findings 7, 11, 12, 22, 23, 24, 25)

Files: `ui/TargetSelector.kt`, `ui/MainScreenRoutes.kt` (TV connection dialog), `ui/RemoteOverlays.kt` (power confirmation), `ui/mediacenter/QueueSheet.kt`, `MediacenterSetupDialog.kt` (Connection dialog), `ConfirmationSheet.kt`, `PairingDisplay.kt`, `WallpaperDetail.kt` (delete dialog), `SourcesScreen.kt` / `DriveActions.kt` (menus with Remove/Eject).

1. Apply table 3.3 everywhere.
2. **Control device (LG dialog):**
   - Rows start at the title's left edge.
   - Replace the "LG TV connection" text link with a full-width row: leading `ic_tune` (or whichever icon the Mediacenter sheet uses), the label "LG TV connection", and a trailing chevron. Separate it from the device rows with a `HorizontalDivider`.
   - Remove the empty band above "Close" (the content's bottom padding should be 8 dp).
3. **TV connection (LG):**
   - "Connected" stays as body text.
   - "Reconnect" becomes a row with leading `ic_refresh`.
   - "Forget TV" becomes a row with leading `ic_delete`, label and icon in `error`.
   - Both rows align with the title's left edge. "Close" stays a TextButton.
4. **Connection (Mediacenter):**
   - Remove the duplicate: show one "Wi-Fi" line with a leading `ic_wifi` icon and "Connected" as supporting text.
   - "Reconnect", "Find computer" and "Remove computer" become rows (refresh, search, delete icons).
   - "Remove computer" uses `error`.
   - The dialog width matches the other dialogs (default `AlertDialog` width).
5. **Queue:**
   - Item list as today.
   - Below it, one row of controls with equal height: `FilterChip("Shuffle")` and `FilterChip("Repeat")`. The repeat chip shows "Repeat off / Repeat all / Repeat one" exactly as today's label text, so no copy change. **If the repeat callback cycles modes, keep it as a chip whose label shows the current mode.**
   - The refresh icon moves into the dialog title row, right-aligned (`IconButton`, same content description).
   - "Save playlist" becomes a full-width row with `ic_playlist_add` (or the existing playlist icon) and a chevron. The name field and Save appear below it as today.
   - "Close" becomes a `TextButton`.
   - Keep `queue-options-<id>` and `shuffle-settings`.
6. **Confirmation sheet (finding 24):** show the value as plain `bodyLarge` in `onSurface` under the title, with no container. If the value is an address or a device name, a leading 20 dp icon is allowed (`ic_language`, `ic_phone`, `ic_volume_up`, `ic_vpn`), but only if the sheet already knows its kind. Do not add data plumbing.
7. **Pairing QR (finding 25):**
   - Caption as plain `bodyMedium` `onSurfaceVariant` above the QR, with a 12 dp gap.
   - QR clipped to `RemoteShapes.Inner`, with a 16 dp white quiet zone, centered.
   - "Close" becomes a `TextButton`.
   - "QR code unavailable" uses the same plain text style.
8. **Power confirmation:** "Turn off" in `error` with `ic_power`.
9. Tests: existing dialog tests keep passing. Add one assertion per destructive action that its node has an icon child.

### Task 5 — Back navigation and page titles (findings 6, 13, 14, 15, 20 back)

Files: `ui/mediacenter/PlaylistScreen.kt`, `ui/WallpaperDetail.kt`, `ui/WallpaperUploadPreview.kt`, `ui/DockScreen.kt` (weather location), `ui/AppShelfScreen.kt`, `ui/ConnectionScreen.kt`, `ui/TvAppsSheet.kt`, `ui/NumberPadSheet.kt`.

1. **LG sub-page header** (one private composable in the LG package, for example `LgSubPageHeader(title, onBack)`):
   - 48 dp `IconButton` with `ic_arrow_back` (new Material Symbols drawable, 24 dp, auto-mirrored) and content description "Back".
   - Then the title in `headlineSmall` on the same row.
   - Replace the "↶ Wallpapers" and "↶ Color Dock" links, and add it to Edit app shelf.
   - **Never use `ic_undo` for navigation.** It stays the TV Back key icon.
2. **Mediacenter Playlists:** use the existing Mediacenter page header (chevron + title + "Mediacenter" subtitle, as on the Settings sub-pages):
   - Title "Playlists" or "Choose playlist", depending on the existing `append` condition. These are today's strings.
   - The refresh `IconButton` goes in the header's trailing slot, as on Sound and Advanced.
   - Body: `Column(verticalArrangement = spacedBy(BlockGap))`, then the search field, then the list group.
   - "More" stays a `TextButton`, right-aligned under the list.
   - Details view: the same header with the playlist name as title, its overflow menu in the trailing slot, and refresh next to it.
   - "Play" becomes a filled pill with `ic_play`. "Add to playlist" becomes a filled pill only when `append != null`; otherwise "Play" is the filled one. Never show both filled: when `append != null`, "Play" becomes a `TextButton`.
3. **Titles:**
   - `ConnectionScreen` "Connect your TV": `headlineMedium` → `headlineSmall`.
   - TV apps sheet title and Number pad sheet title: `titleLarge`.
   - LG Color Dock tab section headings: keep `titleMedium`, with 24 dp above and 8 dp below (finding 15).
4. **Section gaps** (finding 15): Mediacenter Media page "Folders" and "USB drives" label → first row 8 dp. Devices page gap between groups = `SectionGap`.

### Task 6 — LG components (findings 16, 17, 19, 20, 21, 30-LG)

Files: `ui/WallpaperGallery.kt`, `ui/DockScreen.kt`, `ui/NumberPadSheet.kt`, `ui/AppShelfScreen.kt`, `ui/TvAppsSheet.kt`, `ui/TvAppArtwork.kt`.

1. **Primary buttons (OD2):** "Add from phone" and "Start clock mode" become `CircleShape` pills, 56 dp tall, with color unchanged. If OD2 = alternative, also set their container to `primary` / `onPrimary`.
2. **Segmented "My pictures | Built-in":** selected thumb `secondaryContainer` / `onSecondaryContainer` (existing roles, already used for Mediacenter selection), plus a leading 18 dp check icon in the selected segment, as in the Mediacenter Appearance segmented control. The track stays as today.
3. **Number pad (OD1 default):**
   - Digit keys use the LG key style: `surfaceContainerHigh`, 1 dp `outlineVariant` border, 16 dp radius, 64 dp tall.
   - Digits in `headlineSmall` (24 sp) using the normal-width family, so they read like a dialer.
   - "0" keeps its full width.
   - "Delete" becomes an `OutlinedButton` with `ic_backspace`, and its disabled border uses `outlineVariant` at full alpha so its outline stays visible.
   - "Open Live TV and tune" stays the filled primary pill, the only filled element.
   - Channel choices ("12: News") stay outlined pills.
4. **Edit app shelf (finding 20):**
   - Header from Task 5.1.
   - Replace the "Preview: …" sentence with a single icon-only row of the enabled apps' artwork (32 dp, 8 dp gap, horizontally scrollable). Same order and same information, no text list. Keep the string only as the row's content description: `"Preview: " + names`.
   - Each app becomes one 64 dp row:
     - 40 dp artwork (`TvAppArtwork`, iconOnly).
     - Name in `titleMedium`.
     - Two 48 dp `IconButton`s, `ic_keyboard_arrow_up` and `ic_keyboard_arrow_down`, with content descriptions "Move {name} up" / "Move {name} down". Reuse whatever the existing buttons expose to tests. If tests look up the text "Move up", keep a `semantics { contentDescription = "Move up" }` that matches.
     - The switch.
   - Rows sit in one bordered 16 dp LG card with 1 dp dividers.
   - Disabled arrows keep `onSurfaceVariant` at 0.38 alpha.
   - Keep `app-shelf-entry`.
5. **TV apps sheet (finding 21):**
   - Title row: title left. On the right, a refresh `IconButton` (content description "Refresh") and a close `IconButton` (`ic_close`, "Close"). Remove the two centered text buttons.
   - Tiles: a fixed height equal to the artwork tiles. When artwork is missing, show a 48 dp `secondaryContainer` rounded square with the app name's first letter in `titleLarge`, and the name below in `labelMedium`, max 2 lines, ellipsis. Never show a 4-line paragraph.
   - "Opening {app}…": a row with an 18 dp `CircularProgressIndicator` and the text.
   - "Could not open {app}…": a row with `ic_error` in `error` and the text. "Retry" is a `TextButton` aligned to the end of that row.
   - Keep `tv-app-sheet` and `app-picker-list`.
6. **Light-theme system icons (finding 30, LG):** in `TvAppArtwork`, when the artwork is a TV-supplied system icon (the "APPS" grid and "Photo & Video"; the existing code already distinguishes these from bundled `tv_app_*` drawables), draw it on a fixed `Masala` (`#403D39`) backplate in **both** themes, clipped to `RemoteShapes.Inner`. Do not alter the artwork pixels. If the code cannot tell system icons apart without new plumbing, skip this item and record that.

### Task 7 — Mediacenter Remote tab (findings 8, 18, 26, 30-MC)

Files: `ui/mediacenter/MediacenterTargetPage.kt` (`TargetRemote`), `MediacenterRemoteContent.kt`, `RendererControls.kt`, `ui/RemoteButtons.kt` or wherever `DirectionPad` takes its size, `StreamingServices.kt`, `BrowserControls.kt`, `TargetSelector.kt` / `MediacenterDesign.kt` (`DeviceSelector`).

1. **Pad size (finding 8):** in Mediacenter only, the `DirectionPad` diameter becomes `min(maxWidth * 0.78f, 300.dp)`. `DirectionPad` already serves both targets, so add a `diameter: Dp` parameter whose default keeps LG's current size. The pad block stays bottom-anchored for thumb reach. The remaining free space sits **between** the app strip and the pad (`Spacer(Modifier.weight(1f))`), which already happens. The point is that the larger pad halves the void. Scale the OK button with the pad (same ratio as today). Arrow glyphs: 28 dp.
2. **Selector (finding 18, OD3 default):** one `DeviceSelector` look on all three Mediacenter tabs:
   - Same height (48 dp).
   - Leading device icon in the filled `secondaryContainer` circle.
   - Name in `titleMedium`, chevron.
   - Wi-Fi status icon on all three tabs, or on none. **Choose all three**, because the status is useful everywhere.

   Position stays as today unless OD3 = alternative.
3. **Disabled streaming tiles (finding 30):**
   - Keep each tile's own container (the artwork's backplate) at full opacity.
   - Apply `ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })` to the artwork, then alpha 0.6 on the artwork only.
   - Add a 1 dp `outlineVariant` border to each disabled tile so white tiles keep their silhouette on cream.
   - The "Apps unavailable" line stays.
4. **Browser remote row (finding 26):** one row of four equal-weight keys: Back (`ic_undo` is correct here, since it is the browser Back key), Reload, Keyboard, Close tab. Each key is a `MediaKey` with icon and label. At font scale > 1.3, wrap into a 2 × 2 grid, never 3 + 1.

### Task 8 — Mediacenter components (findings 2, 9, 27, 28, 29, 31-MC)

Files: `BrowserControls.kt`, `NowPlayingControls.kt`, `PhotoControls.kt`, `res/drawable/ic_pause.xml`, `res/drawable/ic_stop.xml`, `SourcesScreen.kt`, `LifetimeSettings.kt`, `LibraryItemRow.kt`, `LibraryScreen.kt`, `MediacenterSetupScreen.kt`, `MediacenterSetupDialog.kt`, `SoundOptions.kt`.

1. **Browser keyboard (finding 2):**
   - Inside the dialog, keys use `surfaceContainerHighest` (visible against the dialog's `surfaceContainerHigh`).
   - Layout: a fixed 3-column grid, 8 dp gaps, 56 dp keys.
     - Row 1: Esc, Tab, Shift+Tab.
     - Row 2: an empty cell, Up, an empty cell.
     - Row 3: Left, Down, Right.
     - Row 4: Space (spans 2), Backspace.
     - Row 5: Enter (spans 3).
     - Then "Type text" as a full-width `OutlinedButton` with `ic_keyboard`.
   - Arrow keys show `ic_keyboard_arrow_*` icons with content descriptions "Up", "Down", "Left" and "Right" (same as today's labels, so tests can still find them). Backspace shows `ic_backspace` with content description "Backspace".
   - Every text label: `maxLines = 1`, `softWrap = false`.
   - The same `BrowserKey` values and callbacks.
2. **Transport (finding 9):**
   - Replace `ic_pause.xml` and `ic_stop.xml` with the **filled** Material Symbols paths.
   - Center the transport row horizontally.
   - Play/pause becomes 64 dp. Previous, next and stop become 48 dp, with 12 dp gaps.
   - The time label "0:10 / 3:00" moves **below** the seek slider and becomes two labels at the slider's ends ("0:10" left, "3:00" right, `labelMedium`, `onSurfaceVariant`). It is the same string split at " / ". If the string is produced as one value elsewhere and splitting needs new plumbing, leave it as one label below the slider.
   - Apply to Song, Video and the photo slideshow controls.
3. **Media folder rows (finding 27):** give every folder row the same leading slot as "Add folder": a 40 dp `secondaryContainer` circle with `ic_folder` (new Material Symbols drawable). Offline folders use `ic_folder_off`. All titles in the group then start at the same x.
4. **Stop playback (finding 28):** a full row like "Start automatically" (same start padding, no indent). The title uses `error`. Put a trailing 24 dp filled `ic_stop` icon in `error` where the switch would be, so both rows share one left edge.
5. **Library rows (finding 29):**
   - Music: 56 × 56 dp placeholder, 12 dp radius, 24 dp `ic_music_note` icon.
   - Videos: keep 16:9 (96 × 54 dp), with a 24 dp icon.
   - Photo fallback tiles: center the icon in the area above the title. The title stays `labelSmall`, 2 lines, 8 dp inset (2026-11-16 decision).
   - "Next" page button: chevron after the label (`ic_chevron_right` trailing). "Previous", if present, gets the chevron before its label.
6. **Setup screens (finding 31):**
   - `Column(verticalArrangement = spacedBy(BlockGap))` everywhere in `MediacenterSetupScreen` and the setup dialog content.
   - "Camera unavailable" in `bodyMedium` `onSurfaceVariant`, 4 dp below "Scan code".
   - The six verification words keep `titleLarge` with 8 dp above.
   - Fields are full width.
7. **Sound page:** "Test sound" stays where it is. Add `BlockGap` above it so it reads as attached to the group, not floating.

### Task 9 — LG flows (finding 31-LG)

Files: the weather location screen (find "City or place"), `WallpaperUploadPreview.kt`.

1. Field, Search button and progress bar: `fillMaxWidth()` inside the 16 dp gutter.
2. "Weather data: Open-Meteo": `bodySmall`, `onSurfaceVariant`, at the bottom of the content with `SectionGap` above it.
3. Upload preview: title `headlineSmall`, then status text, then a full-width progress bar.

### Task 10 — Verification

1. `./gradlew` full gate: assemble both APKs, unit and Compose tests, lint, Detekt and ktlint. All must pass. Fix tests that assert old *styling*, but never relax a test that asserts behavior, callbacks, tags or copy.
2. Re-render the fixture set at 320/400/411 dp × font 1.0/1.15/2.0, both themes, and compare against `/home/emma/Pictures/Remote-2026-11-17/Testiaineisto/`. Specifically check:
   - Nav labels are one line (1).
   - Keyboard keys are visible and no word is split (2).
   - Dialogs have no filled dismiss button (7, 11).
   - Destructive actions have icons (12).
3. Install in place on the Pixel (same signer, never uninstall). Verify that pairings and preferences are byte-identical before and after, as in earlier QA.
4. Capture the same Pixel screens as `Pixel/Tumma` and `Pixel/Vaalea` (27 + 23). Put each one side by side with its 2026-11-17 counterpart in `/home/emma/Dev/Mediacenter/.superpowers/sdd/2026-11-17-remote-polish/after/` (private). Look at both targets on the phone itself before reporting done.
5. Append a short "Visual polish QA" section to `design-qa.md`. List the finding numbers fixed, any skipped (with the reason), and the OD choices used.

---

## 5. Out of scope: noted for the owner (copy, state or behavior)

These were seen in the screenshots but are not visual polish. Do **not** change them in this pass.

- Wallpaper upload shows "Complete" **and** still offers "Cancel upload" and "Cancel".
- Color Dock tab (light capture): "Open Color Dock to manage wallpapers" followed by "Open Color Dock to continue." and an "Open Color Dock" button. The same instruction appears three times.
- App launch error says "Try again or refresh TV apps" while the button says "Retry".
- Toggle labels that describe state instead of the control: "Shuffle off", "Repeat off".
- Confirmation dialogs all use the generic "Confirm". A verb would be clearer ("Add", "Change", "Allow").
- VPN status card: "Connect" is the filled (primary) button while the status already says Connected.
- Appearance: the computer theme is an inline segmented control, while the phone theme opens a separate dialog with radio buttons.
- Sound page: a single "Computer audio" option shown with an unselected radio button.
- The power-confirmation fixture shows "Mute TV sound" where the live app shows "Mute TV".
