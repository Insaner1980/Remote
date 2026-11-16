# Remote project requirements

## User interface requirements
- Never use middle-dot or bullet characters as visual separators in user-facing copy.
- The TV/streaming app shortcut strip shows icons only. Do not add captions beneath them. Preserve meaningful accessible content descriptions.
- Mediacenter may have a different visual style from the LG Remote view; do not force those styles to match.
- Target selection must look actionable, show the current device name once and keep text safely inside its rounded bounds.
- Mediacenter Settings opens with only Sound, VPN, Media, Devices, Appearance and Advanced rows. Put controls on dedicated pages. Do not put diagnostic dumps, CLI help or explanatory paragraphs on any page, including Advanced.
- Mediacenter uses a compact one-line device selector and Remote / Library / Settings bottom navigation.
- Visually inspect both target screens on the actual phone before reporting UI layout fixes complete.

- Every screen must use concise everyday labels. Do not expose requested/confirmed internals, LAN/discovery/listener state, UUIDs, epochs, metadata or raw backend messages in the product UI. Keep only short actionable errors and essential confirmation/verification information.
- Secondary library/folder actions belong in icon menus; do not repeat filenames in visible action labels.

## Installation
- All features belong in the original `com.emmah.colordock.remote` application named Remote. Do not create a separate Mediacenter Remote companion.
- Preserve saved TV/Mediacenter pairings and preferences. Update the existing signed installation in place; never uninstall or clear it for an ordinary update.
