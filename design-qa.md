# Settings design QA

final result: passed

## Visual truth and implementation

- Source: `/home/emma/Dev/Mediacenter/.superpowers/sdd/2026-11-16-simple-settings/selected-reference.png`, the exact quiet-list concept attached and selected by the user.
- Implementation: original native Android Remote, application ID `com.emmah.colordock.remote`, Mediacenter selected, Settings overview, soft dark theme, connected and idle.
- Final installed screenshot: `/tmp/remote-simple-settings-device/settings-final.png` (also preserved in the private task evidence).
- Final APK SHA-256: `164b6620fa80d4a56dd68785f445188c87176b649f11155a24682918e0cd1545`.

## Viewport and comparison evidence

Reference: 852 × 1846 pixels. Pixel 9 capture: 1080 × 2424 pixels, density 420 dpi (2.625 px/dp), existing font scale 1.15. Actual Android status bar is 173px and navigation bar 126px; cropped app content is 1080 × 2125px (about 411.43 × 809.52dp). Native app verification has no CSS viewport/deviceScaleFactor.

Combined comparisons are in Mediacenter `.superpowers/sdd/2026-11-16-simple-settings/`:

- `comparison-final-with-chrome.png`: complete reference and complete installed phone at equal 426px width; real Android bars are explicitly identified.
- `comparison-final.png`: full reference beside app content with only Android-owned bars removed, both at 426px width, preserving aspect ratios and documenting the different viewport heights.
- `comparison-final-focus.png`: reference device bar/six rows `(36,243)-(816,1117)` beside actual `(53,363)-(1027,1461)` at equal width. This focused comparison checks readable row text, icons, dividers and spacing without confusing platform chrome with app layout.

Both source and final rendered implementation were opened together in these comparison images. The stable final capture contains no transient pressed-state ripple.

## Comparison history and fixes

1. Initial deployed version `b9c4d37`, `settings-first.png`, `comparison-first.png`: [P2] sliders/palette/stacked-video icons differed from the selected gear/brush/play-frame; [P2] overview rows and text were larger than the quiet reference. Result remained blocked.
2. Replaced those assets with official Material Symbols (`settings`, `brush`, `smart_display`), limited to Mediacenter. Row minimum changed 64→56dp, vertical padding 16→12dp, text 20→18sp. Captured the revised installed app and compared it again in the final combined full/focused evidence. Earlier P2 findings are resolved. No remaining actionable P0/P1/P2 differences.

The source review separately found a constrained recovery dialog and pending work without visible feedback. Both received failing regression tests, scrolling/progress fixes and passing verification.

## Required fidelity surfaces

- Typography: native Android system sans/Material 3. Bold 32sp heading and simple 18sp row labels; the user's 1.15 text scale remains intact. All six labels stay on one line on the real phone; no clipping or overlap. The mock's optical rendering differs slightly from native text, as expected.
- Spacing/layout: single-line compact device bar, six aligned rows with dividers and persistent three-item bottom navigation. Actual rows are 147px high (56dp), fully visible and enabled; last row ends at y1461, well above the navigation bar at y2088. Native system bars and reference viewport differences are preserved rather than recreated or stretched.
- Colors: existing warm charcoal/cream/coral theme tokens retained. Flat native surfaces intentionally replace the mock's generated grain; selected navigation uses coral.
- Assets: sharp existing/official Material vector icons, including matching gear/brush/play-frame. No rasterized screen, generated filler or handcrafted icon drawing. Existing Apache 2 license remains bundled.
- Content: exact six selected section names. No technical paragraphs or switches on overview, no dot separators, device name once. Recovery attention appears only when status requires it. LG streaming shortcuts remain icon-only with accessible launch descriptions.

## Interactions and actual phone checks

- Latest APK: 29 safe rendered Pixel tests passed in 37.336s; 447 unit/Compose tests pass, zero failures/errors/skips. Detekt, ktlint, Android lint and both APK builds pass. The final unit gate revalidated the fresh completed results as up-to-date with successful exit; it did not rerun unchanged tests.
- All six sections opened in the original app. Visible back and Android Back return to the overview. Devices opens connection setup; Appearance opens the existing phone-theme dialog and Done returns without changing theme. Remote/Library/Settings navigation works.
- LG and Mediacenter screens inspected on the real phone. LG connected; Mediacenter reconnected with saved credentials and remains connected. No real media, test tone, VPN switch, source/drive mutation or pairing revocation was triggered during UI verification.
- Updated in place with matching installed signer and UID10450. Both encrypted credential files and both original preference files matched byte-for-byte before first launch. Test runner removed; only original Remote remains.

## Accepted minor differences and limits

[P3] Native Remote/Library glyph variants differ from the generated pill/leaning-books shapes, but retain the existing icon language and clear meanings. Real Android bars, native typography at user text scale, flat theme surfaces and viewport height differ intentionally from the generated concept. These are not clipping, navigation or readability defects.

This acceptance covers the selected Settings redesign and target UI preservation. Full provider playback and Task21/22 release acceptance remain separate.
