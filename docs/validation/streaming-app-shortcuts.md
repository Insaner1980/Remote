# Streaming app shortcuts validation

Date: 2026-10-26. Branch: `codex/streaming-app-plan`. Changes are local and uncommitted. The remote default branch was checked before editing and remained `codex/initial-import`.

## Implemented behavior

The Ready TV session loads installed applications and optional launch-point metadata. Hidden records and uninstalled placeholders are excluded; unknown services are supported. The same collection supplies the horizontal TV apps strip and All apps picker. Refresh also revalidates visible artwork when its URL is unchanged.

Selecting an application sends one direct SSAP launch with its discovered ID. The client checks catalog membership and session ownership before sending, including after waiting for the command mutex. Discovery, image, and launch errors remain local to TV apps. The picker closes after an accepted launch, while failure leaves an explicit Retry. Phone Back closes the picker without sending TV Back and restores focus to All apps.

Artwork uses the paired TV client and restricted HTTPS resource URLs, bounded response reads and decoding, a separate 5 MiB atomic file cache, and at most two concurrent downloads. Eight byte-identical original PNGs provide exact-ID fallbacks; unknown artwork uses the complete app name. See [sources and checksums](../tv-app-icon-sources.md).

The normal remote layout has 56 dp rockers around Mute, Numbers, Live TV and All apps. Narrow widths and large fonts use the existing wide-rocker/stacking behavior. D-pad/OK dimensions, existing actions, themes and bottom navigation are retained. No dependencies, SDK levels, permissions in the Android manifest, companion protocol, or sibling TV-project files changed.

## Checks actually run

| Check | Result and scope |
| --- | --- |
| Complete `testDebugUnitTest` | 68 tests passed, zero failures/errors/skips. Includes 8 catalog parser, 11 controller, 4 endpoint-policy, and existing repeat/session/certificate/companion tests. |
| `assembleDebug`, `assembleDebugAndroidTest` | Passed with the existing toolchain. |
| `lintDebug` | Passed, zero errors and 7 warnings. Two TypographyEllipsis warnings concern the plan's literal `Loading TV apps...` and `Opening %1$s...` copy; five warnings concern unchanged code. No checks were disabled. |
| Warm light instrumentation | 47 tests passed: 12 `TvAppsScreenTest`, 8 existing `RemoteScreenTest`, 14 `DesignCaptureTest`, 7 `AppIconCacheTest`, 6 `TvAppsTransportTest`. |
| Soft dark instrumentation | The same 47 tests passed, zero failures. The capture class itself renders both themes, while the screen test classes use the selected theme argument. |
| Final cache write-failure regression | After extending the existing failure test to force atomic replacement to fail after creating/writing a temporary file, rebuilt the test APK and reran all 7 cache tests: passed. Production code was unchanged. |
| Render inspection | Normal remote in both themes, 320 dp with 200% font, large-font picker in both themes, and inline launch failure inspected. Original image backgrounds/colors, neutral outlines, complete wrapping titles and reachable controls were observed. |
| Asset integrity | All eight copied PNGs match the original SHA-256 and 130 × 130 dimensions. |
| Scope/copy inspection | Compared current files with the initial dirty-tree snapshot. Unrelated existing files were preserved. Search of `app/src/main` found no prohibited separator glyphs or speech/microphone additions. |

The test device was an explicitly selected disposable `Small_Phone` API 37.1 emulator, `emulator-5580`, started read-only without snapshots. Its display was 1233 × 2769 at density 480 (411 × 923 dp). Width/font fixtures additionally exercised 400 dp / 100%, 320 dp / 100%, 400 dp / 200%, and 320 dp / 200%, with both themes. The picker explicitly inherits the fixture density/font scale. The capture tests check final content clearance from navigation in physical pixels using Compose-equivalent rounding; scroll layouts are checked at their actual bottom.

UI assertions cover complete long names, fallback artwork, clicks, refresh, loading, empty, retained-data refresh failure, launch failure/success, Phone Back, focus restoration, repeat release, and ordinary volume availability during launch. A 200-character launch-error title remains scrollable with Refresh/Close reachable. The app ID `status` is included to exercise lazy-list key isolation.

Transport tests execute the real client request code through in-memory WebSocket/HTTP factories, with test-owned credential directories on the disposable installation. They verify registration permission, exact launch payload/count, invalid/removed IDs, queued-session replacement, discovery failure/fallback, normal controls after failure, URL/redirect rejection, response limits and closing, cancellation and old-session rejection. An injected real RemoteViewModel exercises icon ownership and same-URL refresh. These tests do not contact a physical TV and do not prove a real TLS handshake.

Cache tests include invalid/oversized images, bounded output dimensions, isolated identities and unambiguous keys, cache size, unchanged-URL replacement, failed writes, cancellation, temporary-file cleanup and the two-request concurrency limit.

## Reproduction and local evidence

From the repository root:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon
$adb = 'C:/Users/EmmaH/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$device = 'emulator-5580' # Select a disposable emulator explicitly.
& $adb -s $device install -r app/build/outputs/apk/debug/app-debug.apk
& $adb -s $device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$classes = 'com.emmah.colordock.remote.ui.TvAppsScreenTest,com.emmah.colordock.remote.ui.RemoteScreenTest,com.emmah.colordock.remote.ui.DesignCaptureTest,com.emmah.colordock.remote.images.AppIconCacheTest,com.emmah.colordock.remote.tv.TvAppsTransportTest'
& $adb -s $device shell am instrument -w -e class $classes -e theme WarmLight com.emmah.colordock.remote.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s $device shell am instrument -w -e class $classes -e theme SoftDark com.emmah.colordock.remote.test/androidx.test.runner.AndroidJUnitRunner
```

Transport tests grant the existing local-network runtime permission on the disposable emulator through instrumentation, retaining the production precondition. Do not substitute an existing personal installation or run all connected-device classes indiscriminately.

Local logs and captured images are retained under the ignored `.superpowers/sdd/2026-10-26-streaming-app-shortcuts/` directory (`final-checks.log`, `verified-light.log`, `verified-dark.log`, `screens/`). JVM XML reports are under `app/build/test-results/testDebugUnitTest/`; lint reports are under `app/build/reports/`. These are generated local evidence, not committed fixtures. The initial baseline run was mostly up-to-date; the final JVM run above executed the suite afresh.

Focused regression tests first exposed stale completion acknowledgement, focus restoration, ambiguous cache key composition, long error content hiding actions, and a lazy-list header key colliding with an app ID. These were corrected before the final runs. Initial missing-API red runs failed compilation; later regression red runs reached behavioral assertions (or the key-collision exception). Test-fixture storage/density issues and dp-to-pixel rounding were corrected without relaxing the production contracts.

## Review and remaining limits

Changed existing implementation files: `MainActivity.kt`, `TvModels.kt`, `LgTvClient.kt`, `RemoteViewModel.kt`, `RemoteScreen.kt`, and `strings.xml`. Added implementation files: `TvApps.kt`, `TvAppsController.kt`, `TvAppsSheet.kt`, and `AppIconCache.kt`, plus eight `drawable-nodpi/tv_app_*.png` assets. Changed existing tests: `TvEndpointPolicyTest.kt` and `DesignCaptureTest.kt`; added tests: `TvAppsTest.kt`, `TvAppsControllerTest.kt`, `TvAppsTransportTest.kt`, `TvAppsScreenTest.kt`, and `AppIconCacheTest.kt`. Documentation changes are limited to the plan, this report, the icon-source note and the relevant `PROJECT.md` descriptions.

Self-review covered the feature diff against the preserved starting files and the plan's session, discovery, image, unknown-service and accessibility risks. No independent reviewer/subagent was requested. The only additional test seam is an internal RemoteViewModel constructor for client/cache injection; the public Application-only constructor retains the existing production construction path. Icon URLs with query strings are rejected because the inspected historical resources do not need them.

- Physical-TV discovery, current firmware compatibility, model/webOS version and the added permission with saved pairing are **unverified**. No pairing credentials were cleared or replaced.
- Actual opening of the currently installed services from LG Home and Color Dock, visible screen transitions, reconnect behavior on the real TV, and each service's Back behavior are **unverified**. Accepted fake SSAP responses are not evidence that a service opened on the user's TV. No automatic return to Color Dock is promised.
- Spoken TalkBack traversal, announcements and accessibility-focus restoration are **unverified**. Automated semantics/input-focus assertions and screenshots do not replace listening to TalkBack.
- Original-image provenance is recorded, but brand-asset redistribution terms have not been established. Release/signing/distribution validation was outside this implementation.

No commits, pushes or pull requests were made. The sibling `C:/Dev/LG WebOS` project was read only for protocol evidence and original artwork. The temporary emulator and its owned console helper were stopped after testing; no task-owned server or watcher remains. All Gradle commands used `--no-daemon` and finished. The configured Git whitespace check passed.
