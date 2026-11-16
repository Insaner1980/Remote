# Plain Remote design QA

final result: passed

Scope: whole-app text cleanup and original target layouts, including detail screens and dialogs. Full provider/lifecycle release acceptance remains partial.

The prior overview-only acceptance did not satisfy the user's broader request. This pass uses all17 fresh user screenshots, preserved privately in Mediacenter .superpowers/sdd/2026-11-16-plain-remote/contact-1.png through contact-5.png, plus the previously selected quiet Settings overview. Diagnostic/help paragraphs are removed entirely, including Advanced. Only short ordinary controls, content identities, necessary confirmations/verification and actionable status remain. Icon strips have no captions or dot separators.

Final installed original package: com.emmah.colordock.remote, label Remote, UID10450. APK SHA-256: fd3066c7ea2d4753efa6eb61b2a26a89d5c3a97ee54a999b117b2109e24b7438.

Actual Pixel9:1080x2424,420dpi,font1.15. Final installed screenshots in /tmp/remote-plain-device/: settings-final.png, sound-final.png, advanced-final.png, folder-chooser-final.png, photos-final.png, lg-remote-final.png, mc-remote-final.png, queue-final.png and targets-connected-final.png. Settings/Sound/Advanced were opened together in final-controls.png. Other actual inspected pages include VPN top/bottom, Media, Devices, both appearance views, connection and all library kinds. Public nonprivate controls comparison: ../Mediacenter/docs/engineering/evidence/remote-control/plain-remote-2026-11-16/final-controls.png.

Physical findings fixed before acceptance: long camera filenames, inline folder chooser buried below the source list and the live queue connection failure. The final folder chooser is bounded/scrollable and shows only root/folder choices and actions; Home/Refresh icons replace repeated navigation captions. Back/Close and unchanged native confirmation guards retain normal behavior. No remaining actionable text/layout finding in the reviewed scope. Existing warm charcoal/coral Material3 typography, native system bars, user text scale and the separate LG style are preserved.

Final validation:464 unit/Compose tests and all static/build gates pass;65 final APK rendered Pixel tests pass in76.240s. Selected fixtures verify actual callbacks and IDs without executing real media, test sounds, source/drive changes, revocation or VPN/theme changes. Actual native queue open/refresh/close now stays usable after correcting covered-topic sequence reconciliation. Same signer, both encrypted pairings and both original preferences preserved in-place. Both targets connected; test runner removed; normal sleep0 and diagnostic log tags restored; font1.15 unchanged.

Limits:117 of118 recorded photo originals were absent at their catalogued locations, so photo cards use readable fallbacks where previews cannot load. This task did not restore those files. Streaming enrollment/provider and full lifecycle acceptance remain separate. Raw screenshots with private filenames/content are not published.
