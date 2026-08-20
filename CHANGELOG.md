# Changelog

All notable changes to this concept project are tracked here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added
- App name **GAIT** decided.
- Focus decision: **Asset Twin** becomes the core mechanic, since it enables competition without depending on other people's schedules.
- Twin naming feature: users name their own Twin at setup (presets "Hated Person," "Better Self," "Just Twin-7").
- Six-phase Asset Twin loop designed: Setup → Forecast → Live Divergence → Fidelity Debrief → Decommission Trial → Generational Handoff.
- Interactive HTML demo (`demo/asset-twin-demo.html`) with phone mockups of all six phases.
- Original idea collection with five independent core mechanics documented (`docs/concept.md`): Data Broker Loop, Asset Twin, Overwatch Zones, Cyberware Profile, Hostile Takeover Offers.
- App's default content language set to English.
- 14 additional Twin personas designed (17 total), each with its own voice, documented in `docs/twin-personas.md`.
- Dialogue variation system specified: data-grounded templates instead of fixed text, state-conditioned tone, anti-repetition with deliberate callbacks, recommended implementation via grounded generation instead of a static text bank.
- Activities and competitive dimensions concept documented (`docs/activities-and-dimensions.md`): activity types (human-powered, motor-assisted, casual/utility), matching competitive dimensions beyond pace, and independent Twin profiles per activity including an optional cross-modal Overseer Twin.
- Composure system documented (`docs/composure-system.md`): dominance-reactive tone separate from Fidelity — the Twin goes quiet ("Cowed") as you outperform it, and turns immediately hostile ("Predatory") the moment it detects you slipping, including a same-day notification exception, live-ghost and forecast-confidence behavior changes, and a user-facing intensity setting.
- Telemetry and forecasting design documented (`docs/telemetry-and-forecasting.md`): sensor inputs and their reliability caveats, activity auto-classification, a k-nearest-analog forecasting approach suited to small per-user datasets, an EWMA-based Fidelity formula, route-novelty via polyline similarity, and z-score-based personalized Composure thresholds.
- All project documentation translated to English (default project language, including on GitHub).
- Support/funding links added (Ko-fi, PayPal, Bitcoin) to README, plus `.github/FUNDING.yml` for GitHub's native Sponsor button.
- Demo extended with a Setup Step 1 activity-selection screen (activity-aware dimension defaults) and a Composure section showing the same Twin's message log across one week.
- Demo naming screen's persona presets expanded to reflect the full 17-persona roster.
- Rest day handling specified in `docs/telemetry-and-forecasting.md`: declared rest days, inferred rest days via the cold-start forecast behavior, anti-gaming commentary past ~2–3 declared rest days/week, and a 24–48h post-Trial calibration window for new Twin generations.
- Live audio callouts designed (`docs/live-audio.md`): event-triggered (gap-threshold crossings, route events, session phase, Composure state) rather than timer-based, with a hard cooldown and per-session cap, reusing the existing persona/Composure content system as a new delivery channel.
- Platform, stack, and MVP scope decided (`docs/scope-and-stack.md`): native Android (Kotlin + Jetpack Compose), fully on-device architecture with no backend, free-with-donations monetization, and a phased scope (v1: single activity + 5 personas + Composure + rest days; v1.1: live audio, notifications, remaining personas; v1.2+: additional activities, Overseer Twin).
- Android app scaffolded and running on a real device: Room database (`SessionEntity`, `TwinProfileEntity`), the k-nearest-analog `ForecastEngine`, the z-score-based `ComposureEngine`, 5 MVP personas with forecast/cowed/predatory/idle line banks, and Setup/Naming → Forecast → manual session logging → Debrief working end to end.
- True fullscreen immersive UI (system bars hidden, swipe to reveal).
- Notifications implemented (`docs/notifications.md`): a shared "Twin messages" channel, the same-day Predatory exception wired into the Debrief flow, and `IdleTauntWorker` — a WorkManager job posting sparse, randomized (2–4 day jitter) idle taunts instead of a fixed timer.
- Real GPS tracking: `LocationTrackingService`, a foreground service (type `location`) that records live distance/pace/elapsed time via FusedLocationProvider, filtering fixes worse than 25m accuracy. Runs without `ACCESS_BACKGROUND_LOCATION` — Android exempts foreground services with a visible notification from that requirement, which also skips the Play Store prominent-disclosure flow entirely.
- `SessionFinalizer` extracted so GPS-tracked and manually-logged sessions share one path into Forecast/Fidelity/Composure/notifications instead of duplicating the logic.
- A confirmation dialog intercepts the back gesture while a session is actively recording ("Stop tracking" vs. "Keep tracking"), so it can't be lost by an accidental back-press.
- Manual session entry kept as a fallback, secondary to "Start Activity" on the Forecast screen.
- Verified live on device: foreground service confirmed running (`isForeground=true`, type `location`), permission flow, live stats, and the back-confirm dialog (both "Keep Tracking" and "Stop Tracking") all worked correctly.
- Fixed a real bug found during that on-device test: near-zero distance from GPS jitter while stationary produced nonsense pace values (e.g. "953:50/km") and could have saved a garbage session on stop. Added minimum-distance guards to both the live display and the save path.
- Indoor tracking added: a mode chooser (Outdoor/Indoor) on the Track screen, an indoor path that skips the location permission entirely and times the session without GPS, and a distance-entry prompt on stop for what the machine's console showed.
- `SessionEntity.dataSource` (`gps` | `manual`) added — every session is now tagged verified or self-reported, surfaced on the Debrief screen ("Self-reported · Not GPS-verified"). Room DB bumped to version 2 with destructive migration (pre-release, no installed base to preserve).
- Verified the indoor flow live on device end to end: mode chooser → permission-free timer → stop → distance entry → Debrief showing the unverified tag → session persisted correctly.

## [0.1.0] - 2026-08-20
### Added
- Project initialized. First brainstorming round for a cyberpunk-corpo themed GPS movement app without generic missions/achievements.
