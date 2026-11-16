# Selected Settings redesign

Implemented the exact quiet-list option selected by the user on 16 November 2026 in the original Remote app.

- Overview: Sound, VPN, Media, Devices, Appearance, Advanced.
- Compact Mediacenter device selector and Remote / Library / Settings bottom navigation.
- Dedicated detail pages retain sound, VPN service requirements, sources/drives, paired devices, computer/phone appearance and lifecycle controls.
- Network diagnostics and NordVPN help are under Advanced.
- Recovery details remain available through an accessible attention action. The dialog scrolls at large text sizes; pending durable work shows an indeterminate progress bar. No uncertainty flags are reset and no commands are replayed.
- Existing TV shortcut captions remain absent and accessible launch labels remain intact.

Verification: 447 unit/Compose tests, zero failures/errors/skips. Detekt, ktlint, Android lint (zero errors, four nonblocking warnings) and app/instrumentation APK builds pass. The two material source-review findings were reproduced with failing regression tests and fixed before the passing suite.

Updated `com.emmah.colordock.remote` in place with the installed signer. Both encrypted credential files and both original preference files matched byte-for-byte before first launch. App UID remained 10450. Phone font scale remains 1.15; temporary USB stay-awake restored to 0.

**Physical acceptance passed:** 29 focused tests on the latest installed APK, all six settings sections, visible/Android back, connection and appearance callbacks, bottom navigation and both target screens. The selected image and actual final screenshot were compared together; icon/row-size differences were fixed and the revised comparison passed. See [design QA](../../design-qa.md). No changes to desktop, HDMI, sound volume or VPN settings were needed.

New icon assets are official Google Material Symbols outlined Android vectors (`brush`, `devices`, `error`, `library_books`, `settings`, `shield`, `smart_display`, `wifi`, `wifi_off`) from `google/material-design-icons`, `symbols/android/<name>/materialsymbolsoutlined/<name>_24px.xml`. The existing bundled Apache 2 license covers Material Symbols.

Final visual polish uses 18sp row labels and 56dp minimum row height; user font scale remains 1.15. Mediacenter uses `ic_settings_gear` mapped from the official `settings` symbol, preserving the existing LG sliders icon. Final APK SHA-256: `164b6620fa80d4a56dd68785f445188c87176b649f11155a24682918e0cd1545`.
