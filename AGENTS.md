# Remote project requirements

## User interface requirements
- Never use middle-dot or bullet characters as visual separators in user-facing copy.
- The TV/streaming app shortcut strip shows icons only. Do not add captions beneath them. Preserve meaningful accessible content descriptions.
- Mediacenter may have a different visual style from the LG Remote view; do not force those styles to match.
- Target selection must look actionable, show the current device name once and keep text safely inside its rounded bounds.
- Visually inspect both target screens on the actual phone before reporting UI layout fixes complete.

## Installation
- All features belong in the original `com.emmah.colordock.remote` application named Remote. Do not create a separate Mediacenter Remote companion.
- Preserve saved TV/Mediacenter pairings and preferences. Update the existing signed installation in place; never uninstall or clear it for an ordinary update.
