# Plain Remote design QA

final result: passed

Scope: whole-app text cleanup and original target layouts, including detail screens and dialogs. Full provider/lifecycle release acceptance remains partial.

The prior overview-only acceptance did not satisfy the user's broader request. This pass uses all17 fresh user screenshots, preserved privately in Mediacenter .superpowers/sdd/2026-11-16-plain-remote/contact-1.png through contact-5.png, plus the previously selected quiet Settings overview. Diagnostic/help paragraphs are removed entirely, including Advanced. Only short ordinary controls, content identities, necessary confirmations/verification and actionable status remain. Icon strips have no captions or dot separators.

Final installed original package: com.emmah.colordock.remote, label Remote, UID10450. APK SHA-256: fd3066c7ea2d4753efa6eb61b2a26a89d5c3a97ee54a999b117b2109e24b7438.

Actual Pixel9:1080x2424,420dpi,font1.15. Final installed screenshots in /tmp/remote-plain-device/: settings-final.png, sound-final.png, advanced-final.png, folder-chooser-final.png, photos-final.png, lg-remote-final.png, mc-remote-final.png, queue-final.png and targets-connected-final.png. Settings/Sound/Advanced were opened together in final-controls.png. Other actual inspected pages include VPN top/bottom, Media, Devices, both appearance views, connection and all library kinds. Public nonprivate controls comparison: ../Mediacenter/docs/engineering/evidence/remote-control/plain-remote-2026-11-16/final-controls.png.

Physical findings fixed before acceptance: long camera filenames, inline folder chooser buried below the source list and the live queue connection failure. The final folder chooser is bounded/scrollable and shows only root/folder choices and actions; Home/Refresh icons replace repeated navigation captions. Back/Close and unchanged native confirmation guards retain normal behavior. No remaining actionable text/layout finding in the reviewed scope. Existing warm charcoal/coral Material3 typography, native system bars, user text scale and the separate LG style are preserved.

Final validation:464 unit/Compose tests and all static/build gates pass;65 final APK rendered Pixel tests pass in76.240s. Selected fixtures verify actual callbacks and IDs without executing real media, test sounds, source/drive changes, revocation or VPN/theme changes. Actual native queue open/refresh/close now stays usable after correcting covered-topic sequence reconciliation. Same signer, both encrypted pairings and both original preferences preserved in-place. Both targets connected; test runner removed; normal sleep0 and diagnostic log tags restored; font1.15 unchanged.

Limits:117 of118 recorded photo originals were absent at their catalogued locations, so photo cards use readable fallbacks where previews cannot load. This task did not restore those files. Streaming enrollment/provider and full lifecycle acceptance remain separate. Raw screenshots with private filenames/content are not published.

## Visual redesign QA

Verified 2026-11-17 using the original Remote installation on Pixel 9 (1080 × 2424, 420 dpi, font scale 1.15). The 46 live screenshots cover both themes, both Remote views, the device pickers, the LG connection and appearance dialogs and tab surfaces, all three Mediacenter library kinds, and the Settings overview and its six sections. Compared the captures with the 12 proposals, 18 originals, and four annotated references. The engineering evidence publishes the two Remote view comparisons with the system bars cropped out.

LG retains the outlined three-line device selector, bordered keys and app tiles, and vermillion navigation indicator. The LG app strip clears the navigation after scrolling. Mediacenter uses the compact selector, tonal tab selection, fixed page headings, a heading Queue action, and the paired Back and Menu keys under the seam-free pad. VPN shortcut rows include the service artwork. The photo grid has three columns, inset fallback titles and named paging controls.

Videos, Music and Photos loaded in both themes. Music used the existing Refresh action after its first dark-theme attempt reported “Try again.” Missing photo originals show the existing fallback icon and a readable title. Two TV-supplied system logos have low contrast in light mode; the delivered artwork stays intact.

The runtime APK was built from `975c48c`. Its SHA-256 was `4f6d29ab13395d4342d7c702421b77f64c3025db904168eb89c276ed92482075`. The installer verified the original `com.emmah.colordock.remote` package, label Remote, signing certificate and UID 10450. Both encrypted pairings and both preference files were byte-identical across the in-place update.

The full Gradle gate passed both APK assemblies, 486 unit and Compose tests, Android lint, Detekt and ktlint. The tests cover the exact 1.4 font-scale stacking threshold and all six settings rows at 288 dp. Android lint reports zero errors and three warnings, including the Mediacenter scroll-position read and two bundled BIP39 license resources.

On the Pixel, all 57 allowlisted instrumentation tests passed in 70.521 seconds. All four pairing and preference files matched before and after instrumentation. In the installed app, Wallpapers and Color Dock showed their waiting or launch states. The rendered ready-state fixtures showed the gallery, preview cards and control groups in dark and light.

The independent source review of `86c5043..900d278` found zero critical or important issues. Two minor findings remain recorded. Disabled ListRow actions retain their normal title and icon colors, and the Mediacenter heading observes the raw scroll offset even though it uses only the transition away from the top.

### Roboto Flex

`app/src/main/res/font/roboto_flex.ttf` is the upstream variable font. Its SHA-256 is `9b523f7d82593df0107173849ebb8c817471a1df4b4fb2c3cbf40cfd810c8281`. The unmodified SIL Open Font License 1.1 is bundled as `app/src/main/assets/licenses/roboto_flex_OFL.txt`. Headlines use the wider width axis through Compose font variation settings. [Upstream font and license](https://github.com/google/fonts/tree/main/ofl/robotoflex)

## Visual polish QA

Fixed findings 1–31 in the visual polish plan. No findings were skipped. OD1 uses neutral bordered digit keys and reserves the primary color for “Open Live TV and tune.” OD2 gives filled LG actions a pill shape and retains their colors. OD3 uses the same compact selector on all three Mediacenter tabs, on the left in Remote and the right in Library and Settings. At narrow widths or large font scales it can move to a second line while staying right-aligned.

On Pixel 9 at 1080 × 2424, 420 dpi and font scale 1.15, visually inspected both connected Remote views in both themes. The LG app strip clears the navigation divider at rest. Pixel samples confirmed the Masala backplates on both LG system icons and the opaque white backgrounds behind the disabled Mediacenter Netflix and YouTube artwork. The full JVM suite intermittently returned window-gray pixels for these artwork checks, so the artwork color checks use the Pixel captures. The live comparisons cover 27 dark and 23 light screens, including Queue, the country picker, and photo pagination. The private raw captures and side-by-side comparisons are in `../Mediacenter/.superpowers/sdd/2026-11-17-remote-polish/after/`.

The LG weather field, search action, upload preview and progress controls use the available content width. The weather fixture places its content in a 320 dp test frame. Compared the weather attribution and upload progress within their respective content frames.

On the Android emulator, regenerated all 146 original fixtures and rendered both Remote views at 320, 400 and 411 dp with font scales 1.0, 1.15 and 2.0 in both themes. Verified single-line navigation labels, visible keyboard keys with unbroken text at 320 dp and font scale 2.0, text dismiss actions, and icons on destructive actions. The QR uses at least 16 dp of white margin and retains the four-module quiet zone. All 157 instrumented test executions passed across the fixture and regression runs.

The original Remote package `com.emmah.colordock.remote` was updated in place with the same signer and UID 10450. The runtime APK was built from `c33fa91`. Its SHA-256 was `cf527d507183b745b204d58829c4918699bd13ebe5ff7ce4f9b66709baa2d7a0`. Both encrypted pairings and both preference files were byte-identical immediately before and after installation. The existing LG reconnect flow re-encrypted its saved pairing during live verification. Both preference files and the Mediacenter pairing matched their original bytes after capture. The original dark theme, font scale and screen wake policy were restored.

The full Gradle gate passed both APK assemblies, all 494 unit and Compose tests, Android lint, Detekt and ktlint. Lint reported zero errors and five warnings.
