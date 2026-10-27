# Streaming App Shortcuts Implementation Plan

> **For agentic workers:** Implement this plan task by task using `superpowers:executing-plans`. Use subagents only if subsequently authorized by the user or applicable project instructions. The user's Git, scope, and approval rules override any skill defaults about commits, pushes, reviews, or pull requests. Checkboxes now record implementation evidence; unchecked physical-TV and spoken TalkBack checks remain outstanding. Implementation was authorized separately by the user.

**Goal:** Add authentic, TV-discovered application shortcuts to Remote so the user can open installed streaming services directly, with only the remote-control layout changes needed to accommodate them.

**Architecture:** Extend the existing paired LG SSAP client for application discovery, application launch, and trusted icon retrieval. Keep the existing RemoteViewModel and Compose screen flow; isolate the small application state holder for deterministic coroutine tests, following the existing `RepeatCommands` pattern. Reuse the current themes, connection ownership, and native Material 3 bottom-sheet pattern.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, coroutines/StateFlow, OkHttp, Android framework image decoding, JUnit, Compose instrumentation tests. Keep the existing SDK levels (min/target/compile 37), Java toolchain 17, and dependency versions unchanged.

**Spec:** The user's revised request of 2026-10-26 and the consolidated requirements in Sections 1–5 of this document. The earlier suggestion of voice commands is explicitly superseded. This is one self-contained implementation plan; no separate design document is required.

**Status:** Implemented on 2026-10-26, with 68 passing JVM tests, successful debug builds/lint, and 47 passing instrumentation tests in each theme. Committed and submitted for review in [PR #1](https://github.com/Insaner1980/Remote/pull/1) on 2026-10-27. Physical-TV compatibility, saved-pairing permission, real launches/Back behavior and spoken TalkBack remain unverified. See [validation and limitations](../../validation/streaming-app-shortcuts.md).

## Global Constraints

- No voice commands, microphone button, speech recognition, microphone permission, voice-related settings, or reserved space for a future microphone control.
- No bullet, middle-dot, or other round glyph used as a separator between words or pieces of UI text anywhere in the app. This includes headers, status text, metadata, errors, loading states, sheets, and accessibility labels. Use separate lines, spacing, commas, or parentheses as appropriate.
- No cards, tiles, banners, or list items with a decorative colored stripe on the left, right, or another single edge. Do not introduce accent rails for selection, loading, or errors. Existing uniform outlines remain allowed.
- The existing green connection-status indicator is a status symbol, not a separator between words. Preserve it unless a separate request changes it. Markdown list bullets in this planning document are not product UI.
- Preserve Warm light and Soft dark, current typography, the orange OK control, the circular D-pad, and the three bottom destinations: Remote, Wallpapers, Color Dock.
- Preserve ordinary TV control, repeat-on-hold behavior, power-off confirmation, pairing, saved TV credentials, wallpaper transfers, and Color Dock settings.
- Do not add touchpad, keyboard input, playback controls, casting, screen mirroring, HDMI management, new navigation destinations, or onboarding.
- Do not add an SDK, image-loading library, architecture framework, database, background worker, or polling service for this feature.
- Do not add favorites editing, drag reordering, usage tracking, or preference storage in this first implementation. Automatic discovery and the complete application picker satisfy the current request without those optional features.
- Treat TV-provided names, IDs, URLs, and image bytes as untrusted data. Preserve the existing paired-TV identity and certificate checks.
- Do not edit `C:/Dev/LG WebOS`. It is a read-only source of existing assets and protocol evidence for this task. Changes to its TV application or companion protocol are outside scope.
- Preserve all existing uncommitted work. Commit and push only if separately requested. Never create or merge a pull request.
- Before any implementation edits, inspect the current branch and remote default branch. At planning time, **`origin/HEAD` resolves to `codex/initial-import`**, despite its feature-like name. Treat that branch as protected. This plan was written on `codex/streaming-app-plan` without resetting or stashing existing changes.
- Apply the user's minimal-sufficient-work rule throughout. Add only what the behavior and its meaningful verification require.

## Review Focus

1. **Connection changes during discovery, image loading, or launch:** a late result must not update another TV's UI, and queued commands must never transfer to a new session. Covered by Tasks 2–4.
2. **Denied or malformed application discovery:** ordinary remote control must remain usable; failure must not be displayed as a successful empty application list. Covered by Tasks 1, 2, and 5.
3. **Inaccessible or hostile icon data:** authentic fallbacks or honest text remain usable without bypassing TLS checks, following redirects, or decoding unbounded images. Covered by Task 3.
4. **Unknown services and stale installation state:** the feature must not be limited to eight hardcoded services; refresh and launch failures must accurately represent what is known. Covered by Tasks 2, 4, and 5.
5. **Small screens and large text:** all old and new actions must remain reachable at 320 dp, 200% font scale, and their combination, in both themes, without prohibited decoration or text separators. Covered by Tasks 5 and 6.

## 1. Product contract and boundaries

### 1.1 Required behavior

- After a successful TV connection, discover applications from that TV automatically.
- Display actual application artwork supplied by the TV. Use the existing original assets as fallbacks for their exact known IDs.
- A single tap on a visible Netflix, YouTube, or other application shortcut sends one launch request for that application.
- Launch works independently of the Color Dock companion connection. A user starting from LG Home must not first open Color Dock.
- Starting from Color Dock should launch the same installed TV application directly, without an intermediate Home command or launcher-selection dialog.
- Keep Remote open on the phone after launch. If launched from the application picker, dismiss that picker only after the TV accepts the request.
- Provide a complete application picker so services beyond the currently visible shortcuts remain accessible.
- Keep application discovery failure and icon failure local to the new feature. Do not replace a functioning remote with a connection error because the application list or one image failed.

### 1.2 Meaning of automatic matching

The TV is the source of application availability, IDs, titles, icon addresses, and available launcher metadata. The eight existing services are **not** an installation allowlist.

Use the same dynamically discovered, user-visible, launchable application collection for the horizontal shortcut strip and the complete picker. This may include user-facing TV applications other than video streaming services. Use the honest label **TV apps** rather than claiming the protocol provides a reliable streaming-service category. Do not guess service categories from names, and do not hide an unfamiliar streaming service because its ID is unknown locally.

Use returned launch-point order when available. This is the TV response order, not a promise to reproduce every LG Home customization. Do not infer exact Home order from installation timestamps or arbitrary metadata. If launch-point metadata is unavailable but installed-app discovery succeeds, sort eligible entries by display title, with app ID as a stable tie-breaker.

The list refreshes on a new ready connection, explicit Refresh, and reopening the picker. No continuous polling is required. An app installed while the screen is already open appears after refresh; do not advertise instantaneous synchronization.

### 1.3 Launching versus return navigation

Netflix opens as the TV's Netflix application. It is not embedded inside Color Dock. Color Dock currently uses webOS Application Manager to launch applications, while Remote already uses the SSAP launcher endpoint for Color Dock and Live TV.

Use direct SSAP launch for the new buttons. Do not route the action through simulated D-pad movements, a browser URL, a streaming website, a Home command, or a new companion message.

Back behavior after Netflix opens is controlled by webOS and the target application. Test and document it from both starting surfaces. Do not promise automatic return to Color Dock or implement launcher restoration unless a later request explicitly includes it. Keep the existing LG Home and Color Dock actions available on the phone.

### 1.4 Explicit non-goals

No voice feature, no streaming account login, no subscriptions or content catalogs, no film search, no playback in Remote, no installation/uninstallation of TV applications, no changes to the Color Dock shelf, no physical-TV settings changes unrelated to app discovery/launch, and no migration of existing app data.

## 2. Verified starting point

Paths beginning with `app/` are relative to `C:/Dev/Remote`. In this evidence table only, `ui/`, `tv/`, `images/`, and `MainActivity.kt` abbreviate paths below `app/src/main/java/com/emmah/colordock/remote/`. Line numbers describe the inspected working tree and can move during implementation.

| Existing source | Verified responsibility and consequence |
| --- | --- |
| `app/src/main/java/com/emmah/colordock/remote/ui/RemoteScreen.kt:56` | Scrollable screen, 16 dp horizontal padding, 12 dp primary gaps; preserve the scrollable fallback. |
| `ui/RemoteScreen.kt:72` | LG Home/Color Dock row, separate Live TV row, D-pad, Back/Guide, wide rockers, then Mute/Numbers. These are the specific groups to rearrange. |
| `ui/RemoteScreen.kt:106` | `AdaptivePair` stacks at content width below 300 dp or font scale above 1.4. Reuse this policy for the narrow fallback. |
| `ui/RemoteScreen.kt:129` | D-pad is 220 dp; center is 88 dp; existing custom directional hit areas and repeat callbacks must remain intact. |
| `ui/ColorDockRemoteApp.kt` | Three bottom navigation destinations and shared Scaffold insets. No fourth destination is necessary. |
| `ui/Theme.kt` | Shared color roles and typography. Use these directly; no palette redesign. |
| `MainActivity.kt:86` | Lifecycle-aware state collection and screen callbacks. Add the new feature alongside the existing Remote bindings. |
| `ui/RemoteViewModel.kt` | Existing TV connection lifecycle, command dispatch, repeat cancellation, errors, foreground/background behavior. Integrate here without replacing the connection architecture. |
| `tv/TvModels.kt` | `TvControl`, `TvResult`, `TvTarget`, and command types. No application-catalog or general application-launch API exists yet. |
| `tv/LgTvClient.kt:134` | Pairing requests `LAUNCH`, but not `READ_INSTALLED_APPS`. No new Android runtime permission is needed for the app catalog. |
| `tv/LgTvClient.kt:213` | Existing request correlation and session ownership can support app discovery. |
| `tv/LgTvClient.kt:281` | Command mutex and captured session guard existing control operations. General app launch must use the same ownership principle. |
| `tv/LgTvClient.kt:356` | Existing Color Dock launch uses `system.launcher/launch`. |
| `tv/LgTvClient.kt:497` | SSAP failures currently become a generic IOException. Do not claim a precise permission-denied diagnosis from this generic result. |
| `images/ThumbnailCache.kt` | Wallpaper thumbnails use the Color Dock chunk protocol. Reuse applicable file-cache conventions, not that protocol or its cache namespace. |
| `ui/DockScreen.kt:29` | Color Dock shelf editor has eight known IDs. Do not reuse it as the list of installed TV applications. |
| `app/src/androidTest/.../ui/RemoteScreenTest.kt:65` | Existing tests explicitly include 320 dp, 200% font scale, and their combination. Extend them. |
| `app/src/androidTest/.../ui/DesignCaptureTest.kt` | Existing screen capture checks currently assume Mute/Numbers occupy the last row. Update positional expectations to the new last content row while preserving their clearance intent. |

External project evidence, inspected read-only:

- `C:/Dev/LG WebOS/app/app.js:155`: application launch through `luna://com.webos.applicationManager`.
- `C:/Dev/LG WebOS/icon-reference/tv-apps.json`: stored successful installed-application response, containing 148 entries at capture time.
- `C:/Dev/LG WebOS/icon-reference/tv-launchpoints.json`: stored launch-point response containing 24 entries at capture time, including HTTPS icon URLs served by the TV.
- `C:/Dev/LG WebOS/icon-reference/originals/`: original artwork used by the TV application.
- `C:/Dev/LG WebOS/scripts/build.mjs:10`: copies eight original PNG assets into the TV app.

These files are historical evidence, not a live inventory or proof that every endpoint remains available today. Do not copy raw stored TV replies into committed fixtures; they contain device-specific data.

## 3. Detailed UI specification

### 3.1 Normal-width Remote screen

Use the following arrangement when available content width is at least 328 dp and font scale is at most 1.4. Available content width excludes the existing horizontal screen padding.

```text
LG TV                              Settings   Power
Connected

[             LG Home          ] [        Color Dock         ]

                     Existing circular D-pad
                                OK

[              Back            ] [          TV Guide         ]

[  +  ]     [       Mute       ] [      Numbers      ]    [  +  ]
[ VOL ]                                                   [ CH  ]
[  -  ]     [      Live TV     ] [      All apps     ]    [  -  ]

TV apps
[ original icon ] [ original icon ] [ original icon ] ...
     Netflix          YouTube          Yle Areena

               Remote       Wallpapers       Color Dock
```

The ASCII ellipsis illustrates overflow; do not add a pagination-dot widget. Actual application order comes from discovery, not the example above.

Exact changes:

1. Keep header, connection state, Settings, and Power in their current roles and order.
2. Keep the existing LG Home/Color Dock row. These remain commands, not selected-mode toggles.
3. Remove the standalone Live TV action row and move its action into the central group.
4. Preserve the current channel label when present. Keep it near the D-pad; use the existing number/colon/name presentation without adding dot separators.
5. Preserve the 220 dp D-pad and 88 dp OK center.
6. Keep Back/TV Guide below the D-pad, including the existing unsupported-Guide explanation.
7. Replace the two full-width rockers and separate Mute/Numbers row with two narrow rockers surrounding a 2 by 2 group.
8. Add the application shortcut strip as the final remote content section, above existing bottom padding and navigation.

### 3.2 Control dimensions and appearance

- Retain 16 dp outer horizontal padding and 12 dp gaps between main groups.
- Normal-width rocker width: 56 dp each. Keep plus and minus touch targets at least 48 dp high and preserve press/release semantics.
- Use 8 dp between each rocker and the central area, and 8 dp between central columns and rows. The center takes the remaining width.
- Central buttons have a minimum height of 56 dp and grow for their content. Let rockers and center align to the taller group; do not clip labels to force identical heights.
- Put a 24 dp existing interface icon above the short label when horizontal icon/text content would be cramped. Numbers may use the existing `123` treatment. Use `ic_grid_view` for All apps and the existing Live TV icon.
- Preserve the 16 dp action-button corners and current uniform 1 dp outlines; preserve rocker corner treatment where practical.
- Use the existing Mute/Unmute/unknown-state logic and labels unchanged.
- Keep Settings and Power styling unchanged. Application artwork must not compete with the orange OK action by making every whole tile orange.
- Opening All apps must cancel any active repeat gesture, as opening Settings already does.

### 3.3 Narrow-width and large-text fallback

When content width is below 328 dp or font scale exceeds 1.4:

- Keep the two existing wide rockers in their own row.
- Follow with Mute/Numbers through `AdaptivePair`.
- Follow with Live TV/All apps through `AdaptivePair`.
- Keep the shortcut strip below these groups.
- Let `AdaptivePair` stack according to its current rules. Do not force the four central controls into a compressed grid.
- Keep vertical scrolling, the 220 dp D-pad, and minimum touch areas. Large-text layouts need not fit without scrolling.
- Every action, every app title, and the sheet close/refresh actions must remain reachable without horizontal page scrolling.

### 3.4 Shortcut strip

- Section label: `TV apps`, using existing `titleMedium` typography.
- Use one horizontally scrollable row with stable app-ID keys and 8 dp item spacing.
- Each item is 80 dp wide at normal text size, with a 64 dp square artwork area and text below. Preserve the source image aspect ratio using fit, not crop.
- Keep the whole item clickable with a minimum 48 dp hit area. The image and title form one accessible button, not duplicate focus stops.
- Use current neutral surfaces or the icon's original background; no colored edge strips, category colors, status dots between text, or invented substitute logos.
- Use `labelMedium` for names. Allow wrapping and grow item height; do not abbreviate to strings such as `Amazon Pr...`.
- At large font scale, increase item width to 112 dp and let labels wrap. Full titles must also be visible in the adaptive picker.
- Render artwork in full color with `Image`; the existing `DockIcon` uses tinted `Icon` and is unsuitable for brand artwork.
- Unknown artwork falls back to the genuine full application name on the same neutral tile. Do not use a generic film icon and present it as a service logo.
- Missing artwork never disables an otherwise available application.

### 3.5 All apps picker

- Open a Material 3 `ModalBottomSheet`, initially expanded, following the existing NumberPadSheet interaction pattern.
- Title: `TV apps`. Provide labelled Refresh and Close actions with at least 48 dp touch targets.
- Normal content uses a lazy adaptive grid with minimum cell width 104 dp and 12 dp spacing.
- At font scale above 1.4, use full-width rows with a 48 dp image and wrapping title, rather than shrinking a grid.
- Reuse the exact same app list, artwork resolver, launch callback, and request state as the shortcut strip.
- Do not add installed/uninstalled promotional catalogs, categories, search, pinning, favorite stars, or editing controls in this scope.
- A tap sends one launch. While it is in flight, disable application-launch buttons and show `Opening %1$s...` as plain status text. Keep ordinary volume/navigation controls available.
- Success dismisses the picker. Failure keeps it open and shows an inline error with Retry. Never mark an application as visibly open merely because a request was accepted.
- Phone Back dismisses this local sheet and sends no TV Back command. Restore focus to All apps after dismissal.

### 3.6 States and exact feature copy

Store new reusable text in `app/src/main/res/values/strings.xml`. Leave unrelated existing hardcoded strings alone.

| State | Text | Action and behavior |
| --- | --- | --- |
| Initial loading | `Loading TV apps...` | Small progress indicator and plain text. Old controls remain available. |
| Successful empty list | `No launchable apps were returned by this TV.` | `Refresh`. Only use after a valid successful response. |
| First load failed | `TV apps could not be loaded. Try again.` | `Retry`, rerunning catalog discovery. |
| Refresh failed with existing same-session data | `TV apps could not be refreshed. Showing the previous list.` | Retain previous list and show `Retry`. |
| Launch in progress | `Opening %1$s...` | Disable other app launches until completion. |
| Launch failed | `Could not open %1$s. Try again or refresh TV apps.` | Allow Retry and Refresh. |
| App removed since this UI selection | `This app is no longer available in the current TV app list.` | Refresh; do not send an obsolete ID after a successful catalog refresh removed it. |
| TV connection lost | `Reconnect to load TV apps.` | Existing connection flow owns reconnect. Never launch from stale data. |

Use explicit separate states for list loading/error and launch progress/error. Do not show successful empty state during loading. Do not turn a failed refresh into a claim that all apps were uninstalled.

The feature may explain re-pairing only when supported by observed behavior or a specific error. The current generic SSAP IOException is not evidence of missing permissions.

## 4. Discovery, launching, and artwork contracts

### 4.1 Catalog discovery

Add `READ_INSTALLED_APPS` to the existing SSAP registration permissions. Do not add `READ_RUNNING_APPS`, microphone, media-control, or unrelated permissions.

The implementation contract is:

1. Capture the current session before any request.
2. Read `ssap://com.webos.applicationManager/listApps` as the installed-app source.
3. Read `ssap://com.webos.applicationManager/listLaunchPoints` for user-facing visibility, artwork, and order, using the same captured session.
4. Match launch-point IDs to installed-app IDs. Launch-point-only placeholders must not become installed-app buttons.
5. Exclude explicitly hidden entries and installed records with `visible == false` or `class.hidden == true`. Do not exclude applications solely because they are `native` or `systemApp`; the stored Netflix and YouTube records are native apps.
6. Use one entry per app ID. Prefer a visible default launch point where duplicate launch points exist; use only the app ID for this release, not unverified deep-link `params`.
7. If installed-app discovery succeeded but launch points are unavailable, use the installed response's explicitly user-visible records (`visible == true`, not hidden) and their own title/icon metadata. Do not expose all background/system services because visibility information is absent.
8. If installed-app discovery fails or its top-level response is malformed, report catalog failure. Do not turn launch points alone into a verified installation inventory.
9. Publish only if the originating session is still current and Ready.

Parsing rules: nonblank string ID of at most 512 characters; nonblank display title of at most 200 characters; optional string icon URL of at most 2048 characters. Skip individual malformed entries. Reject a missing/non-array top-level `apps` member. Reject catalog responses above 512 entries instead of silently presenting a truncated catalog as complete. Handle a valid empty array as successful empty state. All current numerical limits are implementation choices, not claims about LG's protocol maxima.

Resolve artwork from a validated launch point's `largeIcon`, then `icon`, then the matching installed record's `icon`. Keep the source URL optional. Never use an unvalidated URL simply because it came from the TV.

### 4.2 Launch dispatch

- Add `launchApp(appId: String)` to `TvControl` and `LgTvClient`.
- Accept only an ID from the current session's last successfully discovered catalog. A fixed fallback icon is not evidence that an application is installed.
- Capture the session before waiting for `commandLock`. Recheck session ownership and Ready state inside the lock, as existing commands do.
- Send `ssap://system.launcher/launch` with JSON payload `{ "id": appId }` using the captured session.
- Do not supply undocumented parameters, simulate navigation, launch Color Dock first, or change the phone's selected tab.
- Do not automatically retry a timed-out launch: the TV may already have received it. Offer an explicit retry after failure.
- Parent cancellation propagates. All in-flight requests retain the existing bounded request timeout.
- A successful SSAP response means the request was accepted. Verify actual screen transitions on the physical TV before claiming end-to-end success.

### 4.3 Trusted icon fetch

Use the already paired, network-bound OkHttp client created by `LgTvClient`, with the current certificate trust and redirect policy. Do not create a trust-all image client or use the phone's default route for TV resources.

Add a narrow endpoint predicate to `TvTarget`: accept only HTTPS, the current TV host, port 3001, and a normalized path below `/resources/`, with no user information or fragment. Validate parsed/normalized URLs; encoded traversal must not escape the allowed path. Do not follow redirects. Unsupported URLs, local filesystem paths, external CDNs, and HTTP URLs fall through to local authentic artwork or text. Expand this policy only if actual target-TV evidence demonstrates a necessary safe endpoint.

Bound each response to 1 MiB while reading, whether or not Content-Length is present. Close responses and streams and cancel HTTP calls on coroutine cancellation. Capture and recheck session ownership before returning bytes. Never send a pairing key in an image URL, query string, or log.

Decode bounds first. Reject invalid images, dimensions above 4096 on either axis, or more than 16 million pixels. Sample decoding to an output no larger than 256 by 256. Treat these as feature-specific limits and use a named constant for each.

### 4.4 Artwork cache and fallback assets

- Use a small separate cache directory, `cacheDir/tv-app-icons`, with a 5 MiB total size limit. Do not change the wallpaper thumbnail cache.
- Key files by a hash of TV identity, app ID, and source URL. Never use remote IDs as filesystem paths.
- Write verified images atomically through a temporary file and remove incomplete temporary files.
- Reuse decoded/cached artwork during the session. On successful explicit refresh or a new ready session, revalidate visible icons even if the URL is unchanged so refreshed branding can appear.
- Limit concurrent icon fetches to two; request visible items lazily. A failed icon request must not trigger a recomposition retry loop.
- Clear the active UI image map on connection loss or identity change. Cached bytes must not make another TV's application appear installed.
- Fallback priority: current validated TV artwork, same-TV cached authentic artwork while revalidating, bundled original image for exact app ID, then plain application name.
- No runtime dependency on files in the sibling repository. Copy the approved originals into Remote resources during implementation and record their source and checksums in a small asset note.

| Exact app ID | Original source filename | Proposed Android drawable name |
| --- | --- | --- |
| `youtube.leanback.v4` | `youtube.leanback.v4.png` | `tv_app_youtube.png` |
| `netflix` | `netflix.png` | `tv_app_netflix.png` |
| `com.wbd.stream` | `com.wbd.stream.png` | `tv_app_hbo_max.png` |
| `viaplay-lg` | `viaplay-lg.png` | `tv_app_viaplay.png` |
| `fi.yle.areena` | `fi.yle.areena.png` | `tv_app_yle_areena.png` |
| `mtvkatsomo` | `mtvkatsomo.png` | `tv_app_mtv_katsomo.png` |
| `fi.nelonenmedia.ruutu` | `fi.nelonenmedia.ruutu.png` | `tv_app_ruutu.png` |
| `cdp-30` | `cdp-30.png` | `tv_app_plex.png` |

Source directory: `C:/Dev/LG WebOS/icon-reference/originals/`. Destination: `app/src/main/res/drawable-nodpi/`. Use the original PNGs, not the photographic `contact-sheet.jpg` or cropped screenshot references. Do not recolor or redraw the artwork. Recording provenance does not establish a redistribution license; verify applicable brand-asset terms before distribution without expanding this implementation into a general legal audit.

## 5. State ownership and interfaces

These are proposed interfaces, not declarations already present in the project. Keep naming consistent across tasks.

### 5.1 TV layer

Add to `tv/TvModels.kt`:

```kotlin
data class TvApp(val id: String, val title: String, val iconUrl: String?)

// New members of TvControl:
suspend fun apps(): TvResult<List<TvApp>>
suspend fun launchApp(appId: String): TvResult<Unit>

// New member of TvTarget:
fun allowsAppIcon(url: String): Boolean
```

In a small `tv/TvApps.kt`, place the pure parsing/merge helpers used by `LgTvClient.apps()`:

```kotlin
internal fun parseTvApps(
    installed: JSONObject,
    launchPoints: JSONObject?,
): List<TvApp>
```

Transport and current-session validation remain in `LgTvClient`, including the stored current-session app-ID set. Add `suspend fun appIcon(url: String): TvResult<ByteArray>` there for its paired HTTP client. Keep the method internal if no external production caller needs public visibility.

### 5.2 Feature state

Create `ui/TvAppsController.kt`, a small scoped state holder analogous to the existing `RepeatCommands`, not a repository or application-wide service:

```kotlin
data class TvAppsUiState(
    val apps: List<TvApp> = emptyList(),
    val hasLoaded: Boolean = false,
    val loading: Boolean = false,
    val loadError: Boolean = false,
    val launchingId: String? = null,
    val launchErrorId: String? = null,
    val launchCompletedId: String? = null,
)

internal class TvAppsController(
    scope: CoroutineScope,
    load: suspend () -> TvResult<List<TvApp>>,
    launch: suspend (String) -> TvResult<Unit>,
) {
    val state: StateFlow<TvAppsUiState>
    fun connectionChanged(token: Any?)
    fun refresh()
    fun open(appId: String)
    fun acknowledgeLaunchCompletion()
    fun clearLaunchError()
}
```

The state holder owns catalog/launch jobs and generation checks. `connectionChanged` is idempotent for the same token; a new non-null token starts one initial load; null cancels work and clears state. Refresh cancels/replaces an earlier list request and only its latest generation may publish. `open` ignores duplicate taps while another launch is pending and validates membership. Refresh completion must preserve a concurrent launch's state rather than overwriting the entire state with an old copy.

`launchCompletedId` is consumed and cleared when the current UI handles success. A stale success must not close a picker opened in a different connection or cause a later unrelated sheet to close. Do not replay network actions on recreation.

RemoteViewModel owns this state holder using `viewModelScope`, delegates to `client.apps`/`client.launchApp`, and passes `client.connectionToken` only while Ready. Observe connection transitions as well as new connection tokens. Public screen bindings expose state and `refreshApps`, `openApp`, and completion/error acknowledgement without exposing a mutable controller.

Opening a sheet or app stops active repeat gestures. Existing background disconnect remains the connection lifecycle owner. No new timer or background process is introduced.

### 5.3 Image ownership and UI binding

Create `images/AppIconCache.kt` with:

```kotlin
suspend fun get(
    identity: String,
    app: TvApp,
    refresh: Boolean = false,
): File?
```

Construct it with application context and a suspend fetch callback to the paired client. Null means no remote image is available, allowing the UI's static exact-ID fallback mapping. I/O and image decoding occur off the main thread.

RemoteViewModel owns active icon jobs and exposes a read-only app-ID-to-file map plus `loadAppIcon(appId: String)`. Resolve the full current `TvApp` internally; do not accept arbitrary URLs from UI callbacks. Capture identity, connection token, app ID, and icon URL for each load; publish only if all still match. Cancel jobs and clear the map on connection loss. Successful refresh invalidates the visible-image request generation without relaunching apps.

Put `TvAppTile` and the exact-ID fallback mapping in `ui/TvAppsSheet.kt` alongside the picker, or in the same file as a shared small composable. Both strip and sheet must use the same tile logic. Avoid introducing a general design-system component library.

Extend RemoteScreen with app state, image map, and callbacks. Supply empty defaults for existing previews/tests where appropriate, but explicitly populate application fixtures in tests for the new feature. MainActivity collects feature/image state through `collectAsStateWithLifecycle` and binds callbacks to RemoteViewModel.

## 6. File-change map

| Action | Path relative to repository root | Responsibility |
| --- | --- | --- |
| Modify | `app/src/main/java/com/emmah/colordock/remote/tv/TvModels.kt` | TvApp, catalog/launch methods, icon URL policy. |
| Create | `app/src/main/java/com/emmah/colordock/remote/tv/TvApps.kt` | Pure installed-app/launch-point parsing and merge. |
| Modify | `app/src/main/java/com/emmah/colordock/remote/tv/LgTvClient.kt` | Discovery permission, session-bound discovery/launch/icon fetch. |
| Create | `app/src/main/java/com/emmah/colordock/remote/images/AppIconCache.kt` | Bounded authentic-image cache and decoding. |
| Create | `app/src/main/java/com/emmah/colordock/remote/ui/TvAppsController.kt` | Catalog and launch state with cancellation/ownership guards. |
| Modify | `app/src/main/java/com/emmah/colordock/remote/ui/RemoteViewModel.kt` | Lifecycle integration and active icon state. |
| Create | `app/src/main/java/com/emmah/colordock/remote/ui/TvAppsSheet.kt` | Complete picker and shared application tile/artwork rendering. |
| Modify | `app/src/main/java/com/emmah/colordock/remote/ui/RemoteScreen.kt` | Compact controls, adaptive fallback, app strip, local sheet state. |
| Modify | `app/src/main/java/com/emmah/colordock/remote/MainActivity.kt` | Lifecycle-aware feature binding. |
| Modify | `app/src/main/res/values/strings.xml` | New feature copy and accessibility text. |
| Add | Eight files under `app/src/main/res/drawable-nodpi/` listed in Section 4.4 | Original fallback artwork. |
| Create | `docs/tv-app-icon-sources.md` | Asset origin, checksums, and verified usage/source information. |
| Create | `app/src/test/java/com/emmah/colordock/remote/tv/TvAppsTest.kt` | Pure catalog parsing/merge cases using sanitized inline fixtures. |
| Modify | `app/src/test/java/com/emmah/colordock/remote/tv/TvEndpointPolicyTest.kt` | Trusted icon URL boundary cases. |
| Modify | `app/src/test/java/com/emmah/colordock/remote/tv/LgTvClientTest.kt` | Relevant request rejection/correlation regression coverage. |
| Create | `app/src/test/java/com/emmah/colordock/remote/ui/TvAppsControllerTest.kt` | Deterministic async state, duplicate taps, stale results. |
| Create | `app/src/androidTest/java/com/emmah/colordock/remote/images/AppIconCacheTest.kt` | Android decoder, bounded cache, real-image fallback cases. |
| Create | `app/src/androidTest/java/com/emmah/colordock/remote/ui/TvAppsScreenTest.kt` | Strip, picker, states, accessibility, adaptive layout. |
| Modify | `app/src/androidTest/java/com/emmah/colordock/remote/ui/RemoteScreenTest.kt` | Existing controls and geometry after rearrangement. |
| Modify | `app/src/androidTest/java/com/emmah/colordock/remote/ui/DesignCaptureTest.kt` | New last-row clearance checks and populated light/dark captures. |
| Create | `app/src/androidTest/java/com/emmah/colordock/remote/tv/TvAppsTransportTest.kt` | Production-client checks with in-memory WebSocket/HTTP factories and Android context. No real TV traffic. |

Do not change Theme.kt, ColorDockRemoteApp.kt, Gradle files, AndroidManifest.xml, wallpaper/companion files, or the TV project unless a concrete in-scope requirement proves it necessary. AppContainer does not require a new global service for this design. Update existing test fakes implementing TvControl only where the new methods require it.

## 7. Implementation tasks

### Task 1: Establish the catalog compatibility evidence

**Files:** Read existing client, stored TV replies, current tests, and the original icons. Add only sanitized fixtures to `TvAppsTest.kt` when Task 2 begins.

**Interfaces:** Consumes the existing SSAP connection and historical evidence; produces the concrete response fields and failure examples used by Tasks 2–3.

- [x] Recheck repository instructions, dirty changes, actual remote default branch, and toolchain. Continue on a permitted task branch; never write to `codex/initial-import` while it remains the default.
- [x] Confirm original asset paths and pixel dimensions. Compare PNG checksums before and after any later copy.
- [x] Review installed and launch-point records for the eight known services, native applications, hidden entries, duplicate points, and missing artwork.
- [ ] When an authorized physical-TV test window is available, verify both discovery endpoints through the paired client path and inspect icon availability. Record response shape, endpoint support, model, and webOS version without storing credentials or raw device identifiers in the repository.
- [ ] Verify the added discovery permission with existing saved pairing. If re-pairing is required, preserve credentials until the user deliberately invokes the existing connection workflow. Do not auto-forget the TV or silently replace its trust identity.
- [x] If live access is unavailable, proceed with the established protocol implementation and sanitized fixtures, but retain physical verification as outstanding. Do not mark discovery or launch on the user's TV as passed.

**Acceptance:** There is evidence for the parser inputs and icon source policy, with historical and current observations clearly distinguished.

### Task 2: Implement catalog parsing and session-safe launch

**Files:** TvModels.kt, TvApps.kt, LgTvClient.kt, TvAppsTest.kt, TvEndpointPolicyTest.kt as needed for the shared URL policy, LgTvClientTest.kt, TvAppsTransportTest.kt.

**Interfaces:** Produces `TvApp`, `TvControl.apps`, `TvControl.launchApp`, and `parseTvApps` exactly as defined in Section 5.

- [x] Add focused failing tests for the parser: `visibleInstalledNativeAppsAreIncluded`, `launchPointOnlyPlaceholdersAreExcluded`, `hiddenEntriesAndDuplicateIdsAreHandled`, `unknownVisibleAppIsNotRejectedByKnownLogoList`, `missingAppsArrayFailsButEmptyAppsSucceeds`, `fallbackUsesOnlyExplicitlyVisibleInstalledApps`, and `oversizedCatalogIsNotSilentlyTruncated`.
- [x] Assert the expected app-ID lists, titles, ordering, and optional icon URLs directly. Fixtures must include an unknown service with a valid ID and authentic-image URL, not only Netflix.
- [x] Run `./gradlew.bat :app:testDebugUnitTest --tests "com.emmah.colordock.remote.tv.TvAppsTest" --no-daemon`; confirm the new tests fail for the intended missing behavior before implementation.
- [x] Implement the data type, parser/merge rules, current-session catalog storage, and discovery calls. Add only `READ_INSTALLED_APPS` to the TV permission request.
- [x] Implement `launchApp` with current-catalog membership and session capture before `commandLock`. Invalidate stored catalog on disconnect.
- [x] For production-client instrumentation, add a narrow internal constructor overload accepting optional `WebSocket.Factory` and `okhttp3.Call.Factory` overrides. Keep the existing public Context-only construction path unchanged. Production always uses the paired client; tests use in-memory factories patterned after PointerInputClientTest, with responses controlled through WebSocketListener and HTTP callbacks. Apply session and URL checks before invoking an override. This seam is only for exercising actual request code, not a new transport abstraction or permissive network mode.
- [x] Add transport assertions that a valid tap sends exactly one launcher request with the selected ID; unavailable/removed IDs send none; a queued launch cannot switch to a replacement session; failure does not disconnect ordinary controls.
- [x] Test missing/denied launch-point metadata separately from failed installed-app discovery. Preserve successful installed data only under the specified visibility fallback.
- [x] Run the relevant JVM tests and Android transport tests. Distinguish parser/session-unit coverage from actual production-client transport coverage in results.

**Acceptance:** The catalog is dynamic, honest about failures, and launching cannot operate on another session or an unverified fallback-only ID.

### Task 3: Implement authentic artwork without weakening TV trust

**Files:** TvModels.kt, LgTvClient.kt, AppIconCache.kt, fallback PNGs, tv-app-icon-sources.md, TvEndpointPolicyTest.kt, AppIconCacheTest.kt, TvAppsTransportTest.kt.

**Interfaces:** Consumes TvApp and the existing paired HTTP connection; produces `TvTarget.allowsAppIcon`, `LgTvClient.appIcon`, and `AppIconCache.get`.

- [x] Add failing URL-policy tests for matching HTTPS resources, different host, wrong port, HTTP, embedded credentials, fragment, path traversal, external redirects, and local filesystem paths.
- [x] Add bounded-fetch tests for oversized Content-Length, oversized streamed bodies without a length, cancellation, and a session changing before completion.
- [x] Implement paired-client retrieval with no redirects and the limits in Section 4.3. Ensure every response closes on success, failure, and cancellation.
- [x] Add Android image tests for valid PNG, invalid bytes, oversized dimensions, sample size, incomplete cache write cleanup, identity-separated cache keys, and unchanged-URL refresh replacing old artwork.
- [x] Implement the cache with named size limits, atomic writes, lazy demand, and concurrency limit two. Do not reuse Color Dock's wallpaper protocol.
- [x] Copy the eight original PNGs with the destination names in Section 4.4; record SHA-256 values and sources. Verify the copies are byte-identical.
- [x] Verify exact-ID fallback resolution and a plain-name fallback for unknown artwork. Neither fallback may invent installation state.
- [x] Run endpoint-policy JVM tests, image-cache instrumentation tests, and icon transport tests. No live request to another device or an external icon host is needed to test rejection.

**Acceptance:** The UI can display genuine artwork even when one remote image is unavailable, and the feature introduces no TLS, origin, redirect, or unbounded-decoding bypass.

### Task 4: Integrate application state into the current lifecycle

**Files:** TvAppsController.kt, RemoteViewModel.kt, TvAppsControllerTest.kt, MainActivity.kt only for binding after Task 5 is ready.

**Interfaces:** Consumes the TV and image APIs from Tasks 2–3; produces the screen state/callbacks described in Section 5.

- [x] Add coroutine tests named `readySessionLoadsOnce`, `lateCatalogFromOldSessionIsIgnored`, `refreshFailureKeepsSameSessionList`, `failedFirstLoadIsNotEmptySuccess`, `doubleTapSendsOneLaunch`, `unknownIdDoesNotLaunch`, `disconnectCancelsPendingLaunch`, `concurrentRefreshDoesNotEraseLaunchProgress`, and `consumedSuccessDoesNotDismissLaterPicker`.
- [x] Use a test dispatcher and controllable deferred responses. Assert exact call counts and full relevant state before and after each transition; do not use wall-clock sleeps.
- [x] Run `./gradlew.bat :app:testDebugUnitTest --tests "com.emmah.colordock.remote.ui.TvAppsControllerTest" --no-daemon` and confirm the intended failures.
- [x] Implement the small controller and connect it to existing Ready/disconnect transitions. Do not make catalog loading wait for a channel-list failure or delay normal remote availability.
- [x] Implement the active icon map and request ownership in RemoteViewModel. Verify old-session image completion cannot update a new list, using a controlled fetch callback in the image/state tests.
- [x] Preserve the existing 5-second background-disconnect policy; do not add a second connection manager or continuous refresh loop.
- [x] Run the controller tests and existing RemoteViewModelTest repeat tests.

**Acceptance:** Automatic loading and explicit refresh work, all operations are scoped to the active TV, and ordinary remote actions remain independently usable.

### Task 5: Add the app UI and perform the minimal rearrangement

**Files:** RemoteScreen.kt, TvAppsSheet.kt, MainActivity.kt, strings.xml, RemoteScreenTest.kt, TvAppsScreenTest.kt, DesignCaptureTest.kt.

**Interfaces:** Consumes read-only app/image state and callbacks from Task 4; produces the strip, picker, compact group, and adaptive fallback.

- [x] Add focused UI tests for visible authentic tiles, full titles, tile click callback, All apps opening, refresh callback, plain text fallback, loading, empty, refresh failure with retained data, and launch failure with picker retained.
- [x] Add tests proving Phone Back closes the sheet without a TV command, launch completion is consumed once, and opening the picker releases a held rocker.
- [x] Implement the normal and fallback arrangements exactly as Section 3 specifies. Reuse existing D-pad and action semantics.
- [x] Implement the horizontal strip and modal picker using shared tile logic. Observe images lazily; do not fetch repeatedly during recomposition.
- [x] Bind state in MainActivity. Keep existing remote error handling and feature-local errors distinct so one failure is not announced twice.
- [x] Add accessible button names such as `Open Netflix on TV`, while retaining the complete visible title. Decorative artwork has no independent content description. Give the sheet a pane title and status changes an appropriate live-region announcement.
- [x] Update capture fixtures to contain real fallback images and realistic long service names. Replace old last-row assumptions with assertions about the final app content clearing bottom navigation by the intended padding.
- [x] Inspect the rendered screen in both themes. Verify artwork is untinted, white-backed logos stay white-backed, outlines are uniform, and no microphone/empty microphone slot exists.
- [x] Search app-authored UI text for forbidden separator glyphs and inspect new surfaces for colored edge rails. Also inspect dialogs/error states, not only the successful main screen.

**Acceptance:** All existing functions remain reachable, app launch is a direct visible action, and the interface still reads as the current Remote application.

### Task 6: Complete targeted validation and record limitations

**Files:** Existing/new tests above; add `docs/validation/streaming-app-shortcuts.md` only when actual implementation results exist.

**Interfaces:** Consumes the complete implementation; produces reproducible validation evidence and an honest completion report.

- [x] Run the JVM suite, debug assembly, and debug lint once after the targeted tests pass. Correct failures introduced by this work without weakening checks or cleaning unrelated code.
- [x] Run the new UI tests plus RemoteScreenTest and DesignCaptureTest in both themes for each width/font combination listed in Section 8.
- [ ] Perform TalkBack checks for the strip, picker, refresh, loading/error announcements, and focus restoration. Automated semantics assertions do not replace spoken testing.
- [ ] In an authorized physical-TV window, test every currently installed one of the eight known services, plus an unfamiliar installed app if available. Do not install a new application solely to create this case without authorization.
- [ ] Test Netflix/another available service from LG Home and Color Dock independently. Record accepted request versus visible application opening and observed Back behavior separately.
- [ ] Test reconnect, explicit refresh, saved-pairing permission behavior, a missing icon, and unavailable application handling without deleting user content or deliberately changing unrelated TV settings.
- [x] Recheck app-wide UI copy and new surfaces for the two visual prohibitions and verify no speech permission/code/assets were added.
- [x] Stop only temporary processes started for this work. Do not kill unrelated Java, Gradle, Node, emulator, IDE, or browser processes.
- [x] Inspect the final diff against the initial dirty-tree baseline. Report changed files, checks actually run, blocked cases, and material limitations. Do not commit, push, create a PR, or modify the TV application.

**Acceptance:** All required local checks pass or have a clearly identified pre-existing/blocking cause. Physical behavior is claimed only to the extent actually observed.

## 8. Validation commands and evidence matrix

Run from `C:/Dev/Remote`. Commands below are future instructions, not completed checks. Do not write a green result into the report merely because a test file exists.

### 8.1 JVM, build, and lint

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.emmah.colordock.remote.tv.TvAppsTest" --tests "com.emmah.colordock.remote.tv.TvEndpointPolicyTest" --tests "com.emmah.colordock.remote.tv.LgTvClientTest" --tests "com.emmah.colordock.remote.ui.TvAppsControllerTest" --tests "com.emmah.colordock.remote.ui.RemoteViewModelTest" --no-daemon
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon
```

Expected: Gradle succeeds, targeted/new test reports have zero failures, and lint has no new errors attributable to the change. Do not broaden testing repeatedly after this succeeds without a new change or unresolved concern.

### 8.2 Targeted Android checks

Use an explicitly selected emulator or test device. Do not indiscriminately run all connected-device tests: existing physical-TV test classes include separate opt-in requirements.

Run fake-transport instrumentation only in a disposable emulator/test installation with test-owned pairing storage. Its in-memory factories must answer all SSAP/HTTP traffic and must not contact a physical TV or read/write the user's existing production pairing. Grant the local-network runtime permission to that test installation as needed for the unchanged production precondition. Do not add MockWebServer to androidTest dependencies: the existing version is available only to JVM tests, and no dependency change is needed for the in-memory factories. Fake responses prove framing, ordering, cancellation, and bounded reads; they do not prove a TLS handshake. Existing certificate-policy tests and the separately authorized physical-TV checks cover that distinction.

```powershell
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.emmah.colordock.remote.ui.RemoteScreenTest,com.emmah.colordock.remote.ui.TvAppsScreenTest,com.emmah.colordock.remote.ui.DesignCaptureTest,com.emmah.colordock.remote.images.AppIconCacheTest,com.emmah.colordock.remote.tv.TvAppsTransportTest" "-Pandroid.testInstrumentationRunnerArguments.theme=WarmLight" --no-daemon
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.emmah.colordock.remote.ui.RemoteScreenTest,com.emmah.colordock.remote.ui.TvAppsScreenTest,com.emmah.colordock.remote.ui.DesignCaptureTest,com.emmah.colordock.remote.images.AppIconCacheTest,com.emmah.colordock.remote.tv.TvAppsTransportTest" "-Pandroid.testInstrumentationRunnerArguments.theme=SoftDark" --no-daemon
```

New layout tests must exercise these configurations internally through Compose constraints and LocalDensity, following the existing test approach. Do not change the user's phone-wide display settings to simulate them.

| Layout width | Font scale | Required observations in both themes |
| --- | --- | --- |
| 400 dp | 100% | Compact control group, 220 dp D-pad, visible first app shortcuts, no navigation overlap on the screenshot-equivalent portrait height. |
| 320 dp | 100% | Fallback arrangement, all actions reachable, no clipped app names or targets below 48 dp. |
| 400 dp | 200% | Stacked/adapted actions and picker rows, complete labels, predictable vertical scrolling. |
| 320 dp | 200% | Combined worst-case layout, Close/Refresh reachable, no trapped scroll area, no clipped controls. |

At normal scale, also inspect the approximate 411 dp-wide, 923 dp-tall portrait window corresponding to the supplied screenshots, with actual system and navigation insets. This is a planning approximation, not a verified device-density measurement. Standard-size fitting must be visually demonstrated rather than assumed from a width-only test.

### 8.3 Physical-TV matrix

| Case | Evidence required |
| --- | --- |
| Existing saved pairing plus new permission | Read succeeds or a specific reproducible limitation is recorded; credentials remain preserved. |
| Catalog | Entries agree with the TV's installed/user-facing applications; hidden services and launch-point-only placeholders are absent. |
| Authentic icons | TV-provided artwork displays for available resources; original local fallback displays when a known icon is unavailable. |
| Launch from LG Home | One tap visibly opens the selected service without opening Color Dock first. |
| Launch from Color Dock | One tap visibly opens the same service without first navigating to LG Home. |
| Back after each launch | Record observed destination; do not infer it from launch success. |
| Reconnect and refresh | Current list reloads, old-session requests do not affect the active connection. |
| Discovery denied or unavailable | Remote controls still work; app list shows a recoverable feature error. |
| Changed or missing app | Refresh removes genuinely absent entries; launch failure has useful recovery and no false success. |

Use existing opt-in physical-test safeguards where applicable. `PhysicalTvReviewTest` requires an explicit TV address and isolated installation; its mutation tests additionally require authorization. Do not bypass these gates, silently clear pairing data, or invoke its wallpaper/settings tests as part of this app-launch feature.

### 8.4 Visual-constraint checks

```powershell
rg -n '[•·∙●▪‧]' app/src/main -g '*.kt' -g '*.xml'
rg -n 'RECORD_AUDIO|SpeechRecognizer|RecognizerIntent|ic_mic' app/src/main
```

No matches is the expected result for newly introduced separator/speech content. Review any existing matches in context rather than deleting unrelated code or altering genuine logo pixels. The current source inspection found no literal separator matches in Kotlin/XML. These searches do not detect arbitrary Canvas/CSS-style drawing or generated strings: rendered inspection remains required for colored accent rails and all UI states.

## 9. Completion checklist

- [x] Installed services appear automatically from the active TV's catalog rather than from a hardcoded availability list.
- [x] Unknown services are supported by their real IDs, names, and available artwork.
- [x] Original logos are displayed without tint, cropping, reconstruction, or screenshot-derived substitutes.
- [x] Tap-to-launch is one action and remains independent of Color Dock companion availability.
- [x] Both LG Home and Color Dock starting cases have explicit physical-test results or an outstanding limitation.
- [x] The compact layout and the narrow/large-text fallback preserve existing controls and current themes.
- [x] No voice feature or microphone placeholder exists.
- [x] No word-separator dots or decorative single-edge colored card stripes exist in the introduced UI, including errors and accessibility copy.
- [x] Loading, empty, stale-refresh, icon failure, disconnected, and launch-failure states are distinct and recoverable.
- [x] Session changes cannot redirect a queued launch or publish another TV's catalog/artwork.
- [x] 320 dp, 200% font scale, their combination, both themes, and TalkBack are verified or explicitly reported unverified.
- [x] Existing user changes and the sibling TV repository remain preserved.
- [x] No commit, push, pull request, or unrelated background process is left by default.

## 10. Reference sources

- [LG Connect SDK WebOSTVService API](https://connectsdk.com/en/latest/apis-and/and-webostvservice.html): app discovery/launch capabilities and SSAP permissions. Documentation is a protocol reference, not proof of support on the user's current firmware.
- [LG Connect SDK WebOSTVService implementation](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/WebOSTVService.java): `listApps`, `listLaunchPoints`, and launcher request examples. Consult the implementation; do not add Connect SDK as a dependency.
- [LG webOS Application Manager](https://webostv.developer.lge.com/develop/references/application-manager): launching a separate TV app by ID, as used by Color Dock. Do not confuse in-TV Luna APIs with Android's SSAP transport.
- Local owning sources and historical captures are listed in Section 2. Recheck them during implementation if the working tree changes.

## 11. Planning self-review

- Revised user scope is covered: streaming-service launching, authentic icons, automatic discovery, necessary rearrangement, and complete removal of the voice proposal.
- Both explicit visual prohibitions apply globally to app-authored UI, including error and loading paths.
- Optional features from the earlier discussion have been removed instead of becoming implementation dependencies.
- Proposed interfaces are labelled as future additions and use consistent names across tasks.
- Current facts, historical TV evidence, design decisions, and unverified runtime behavior are separated.
- The five review-focus risks each have a task and test requirement.
- No full application implementation is embedded in the plan; code blocks define interfaces and verification commands only.
- Implementation was completed against this plan; see the validation report for results and remaining limits.
