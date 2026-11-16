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

**Physical acceptance remains pending:** the phone is locked. The prior version's physical results do not certify this redesign. See [design QA](../../design-qa.md). No changes to desktop, HDMI, sound volume or VPN settings were needed.

New icon assets are official Google Material Symbols outlined Android vectors (`devices`, `error`, `library_books`, `palette`, `shield`, `video_library`, `wifi`, `wifi_off`) from `google/material-design-icons`, `symbols/android/<name>/materialsymbolsoutlined/<name>_24px.xml`. The existing bundled Apache 2 license covers Material Symbols.
