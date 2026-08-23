# GAIT — Privacy Policy

_Last updated: 2026-08-21_

GAIT is a training app with a fictional corporate frame. This is the honest version of what it does with data.

## What GAIT collects
- **Location (foreground only).** While you have an outdoor session running, GAIT reads GPS to compute distance, pace and moving time. Recording runs as a foreground service with a visible notification. GAIT does not request background location.
- **Motion/health sensors.** The indoor-session foreground service is declared with the `health` type so it can run as a timer without location; GAIT does not read heart rate or any health-platform data today.
- **What you type.** The opponent's name, your activity choice, rest-day and vacation settings, planned days off, manual session entries.

## What GAIT stores
Everything lives in a local database on your device: sessions (date, distance, duration, pace, forecast), the opponent profile (name, persona, Fidelity, generation, stakes), messages, planned days off. Notification preferences are stored in local app preferences.

## What leaves your device
**Nothing.** GAIT has no server, no account, no analytics, no advertising SDK and makes no network requests. The "division", the roster of 1,300 assets and their names are a deterministic simulation computed on your device; they are not real people.

## Notifications
GAIT can post local notifications (opponent messages, stakes, the ongoing recording notification). You can mute them in Settings or when closing the app; Android's notification settings also apply.

## Deleting your data
Settings → "Erase all data" removes every session, message, profile and planned day. Uninstalling the app removes everything.

## Contact
Open an issue at https://github.com/eVersor-HN/GAIT.
