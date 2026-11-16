# Settings design QA

final result: blocked

- Source visual truth: `/home/emma/Dev/Mediacenter/.superpowers/sdd/2026-11-16-simple-settings/selected-reference.png`, the exact image attached and selected by the user.
- Implementation: native Android Compose in the existing original Remote app, not a web prototype.
- Required state: Mediacenter selected, Settings overview, soft dark theme, no active operation.
- Device: Pixel 9, 1080 × 2424 physical pixels, density 420 dpi, existing font scale 1.15. Android owns system bars.
- Implementation screenshot: pending phone unlock. Do not infer visual acceptance from source tests or builds.
- Comparison normalization: crop only Android-owned system bars, preserve app content, normalize source and app-content width to the same logical viewport; record actual dimensions with the final capture.
- Full-view and focused comparisons: pending. They must combine source and implementation in one image before acceptance.

## Fidelity checklist

- Typography: existing native font, bold Settings heading, simple single-line row labels; actual wrapping and optical sizes await capture.
- Spacing: compact device bar, six evenly spaced rows and dividers, persistent bottom navigation; physical geometry awaits capture.
- Colors: existing warm charcoal, cream and coral theme tokens; visual comparison awaits capture.
- Assets: existing native icons plus official Google Material Symbols outlined vectors; Apache 2 license already bundled in app assets. No screenshot rasterization or decorative texture added.
- Copy: selected six section names; diagnostics and help moved under Advanced. No visual dot separators or streaming shortcut captions introduced.
- Interaction: 447 JVM/unit/Compose tests pass, including all six sections, callbacks, offline/double-text navigation, uncertainty detail scrolling and pending progress. Detekt, ktlint, Android lint and both APK builds pass. Physical touch/back verification remains pending.
- Installation: updated original Remote in place with the same signer. Both encrypted credentials and both original preference files matched byte-for-byte before first launch. APK SHA-256: `9554178699156afe19e0c04f01b6b08e31ab5a03864254cce858106341acd8ec`.

## Review history

The independent source review found two material feedback issues: an unscrollable recovery dialog and pending durable actions without visible progress. Both were reproduced with failing regression tests and corrected. The final 447-test run passes. This source review does not substitute for the required visual comparison.

## Remaining gate

Unlock the connected phone, inspect LG and Mediacenter, run the 29 focused physical tests, capture Settings and compare it with the selected image. Installation is already complete. Do not label the visual result passed until that work is complete.
