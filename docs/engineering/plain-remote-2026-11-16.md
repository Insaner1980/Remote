# Plain Remote interface

The latest user screenshots exposed a gap in the previous Settings redesign: its overview was quiet, but detail pages and dialogs still contained diagnostic dumps, setup instructions, duplicated action captions and raw server messages. This pass removes that text throughout the original Remote application.

- Sound uses short output names, distinct HDMI choices and one test button. Saved selection and confirmed native state remain separate internally. Unknown outcomes remain unknown.
- VPN keeps connection/country controls and per-app switches. Advanced keeps ordinary controls; command-line help and network diagnostics are removed from the product entirely.
- Folder, drive, phone, queue and playlist rows use secondary icon menus. Row keys preserve identity while lists change; opening a menu never performs its action.
- Library titles omit file extensions and repeated captions. Photos use previews or short numbered fallbacks. Oversized selections retain a short search-recovery action.
- Remote has a stationary heading/device selector and persistent bottom navigation. Streaming shortcuts are icons only, placed before the directional/browser controls.
- Dialogs retain essential identities, verification words and action confirmations. Typed short notices replace rendered backend text while preserving working, stopping, unknown, approval and safe-eject states.

No protocol, desktop installation, native library data or target ownership behavior changes. The original package, signer, saved pairings and preferences must survive the in-place update. Physical and source validation is recorded in the Mediacenter plain-remote evidence directory after completion. The prior overview-only QA result does not establish this broader acceptance.

## Live connection correction

Actual Pixel navigation exposed a pre-existing client reconciliation error: opening Queue could retire the connected state when a contiguous event repeated a topic revision already covered by its snapshot. The native `RevisionCursor` and `remote-identity-pairing.md` require consuming that sequence while retaining the newer topic data. Android now follows the same rule. Sequence gaps, reordered stream positions, changed epochs, invalid counters and identity changes still invalidate readiness. Two added regressions and the corrected contradictory test cover this behavior; the failing cases were recorded before the fix.

Optional `RemoteProtocol` Android log-tag tracing records only controlled stages, exception classes and source frames. It is disabled by default, never logs payloads or error messages and adds no product text. Temporary device log-tag overrides are restored after verification.

The real library currently has missing original photo files. The UI uses short fallback tiles; this text cleanup does not restore missing files. Browser enrollment/provider/lifecycle release acceptance remains separate from this UI pass.

Final installed APK: `fd3066c7ea2d4753efa6eb61b2a26a89d5c3a97ee54a999b117b2109e24b7438`. Final source gates464/0; final rendered Pixel65/0 (76.240s). Actual final folder dialog, generated-photo names, both connected targets and live queue reads inspected. See root design-qa.md and Mediacenter engineering evidence for acceptance and limits.
