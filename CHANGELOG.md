# Changelog

All notable changes to this concept project are tracked here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [0.4.0] - 2026-08-20

A hardening pass driven by a full code review of v0.3.0: one shipped crash fixed, several "built" features made actually true, the UX holes a real user would hit first, and the plumbing a real release needs (tests, signing, R8, icons).

### Fixed
- **Crash: Indoor mode on a fresh install** (`LocationTrackingService`). The service started as a `location`-typed foreground service for Indoor too; on Android 14+ that throws `SecurityException` unless a location runtime permission is held — and Indoor deliberately never asks for one. Reproduced live on device (permission `granted=false` → FATAL). Indoor now runs as a `health`-typed FGS (new `FOREGROUND_SERVICE_HEALTH` + install-time `HIGH_SAMPLING_RATE_SENSORS` permissions, no runtime prompt); Outdoor stays `location` and is gated on the permission before `startForeground`. Both start paths are also wrapped so a failure surfaces as an on-screen error instead of a dead timer. Verified live: fresh install, no location permission, Indoor start → service foreground with `types=0x100` (health), no exception.
- **Rest days & vacation were cosmetic.** `SessionFinalizer` now actually freezes Fidelity/Proximity, keeps Composure neutral, and suppresses notifications on a declared rest day or during vacation (the session is still saved — training is training). `IdleTauntWorker` goes silent during vacation. The Debrief shows an explicit rest-day note. Verified live: Forecast 5:00 vs Actual 6:00 on a rest day left Fidelity at 50% with the note shown.
- **Dead code made live:** `ComposureEngine.isGapPredatory()` is now called from `IdleTauntWorker` — going quiet for far longer than your own rhythm triggers a Predatory line (the "three days and you're already negotiating with yourself" behavior from the concept), once per day, never during vacation. `SessionEntity.isRestDay` is now written and shown as a REST DAY tag in Statistics.
- **Silent too-short stop.** Stopping an outdoor session with no GPS fix / under 20 m / no moving time now says exactly why nothing was saved instead of silently returning to "Ready".
- **Pace math.** Elapsed time uses `SystemClock.elapsedRealtime()` (monotonic; a clock change mid-run can't corrupt the session). Pace is computed over *moving* time only: the wait for the first GPS fix and auto-paused stretches (interval speed < 0.5 m/s) no longer inflate it. Live screen shows AUTO-PAUSED and a separate moving-time readout.
- **Process death mid-session no longer loses the run.** The service is `START_STICKY` and persists the in-progress session every 10 s (`ActiveSessionStore`); if the system restarts the service it resumes from that snapshot, and if only the app relaunches it offers an "Interrupted session found — Save / Discard" recovery. Verified live by force-stopping mid-session: recovery dialog → save → distance prompt → Debrief.
- Notifications (opponent messages and the tracking notification) now open the app on tap.
- Horde Forecast shows the projected pace/finish alongside the atmospheric caption instead of hiding the numbers.
- Persona forecast templates use correct singular/plural ("1 session", "Once"); UI counts use `plurals` resources.
- The notification-permission prompt moved from app launch to the Forecast screen, after setup.
- HUD battery readout refreshes every 30 s instead of reading once.

### Added
- **Settings screen**: rename the Twin, change its voice / the horde's intensity, switch opponent type (Twin ↔ Horde — a new opponent, so Fidelity/Proximity and Generation/Wave reset; session history stays), and a confirmed "Erase all data" that returns to setup. Verified live.
- **Delete a session** from Statistics, with confirmation. Verified live.
- **Watchful line banks** for every persona, so the Twin is never silent in the first few sessions before Composure has enough history to judge.
- **Launcher icons** (adaptive, vector): three ascending brass strides on ink for the app; the same in cyan with a dashed scanline for the demo. No more default Android robot.
- **Unit tests** for `ForecastEngine`, `ComposureEngine`, `RestDayPolicy` — 26 tests covering recency weighting, day-of-week weighting, confidence vs. cluster tightness, z-score Composure (incl. noisy-baseline and flat-baseline cases), gap-predatory, rest-day toggles, and the vacation bank. All green.
- **Release build pipeline**: optional signing from an untracked `keystore.properties` (`keystore.properties.example` documents the setup), R8 + resource shrinking enabled for release (app APK 12.9 MB → 1.5 MB), keep rules for the reflective ViewModel factory / Room entities / persisted enums. Verified: a locally-signed release APK installs and runs through setup → Forecast alongside the debug build. Debug builds now carry an `applicationIdSuffix` (`.debug`) so both can coexist.

### Changed
- `TwinProfileEntity` no longer overloads `personaKey` for horde intensity: separate nullable `personaKey` / `hordeIntensity` columns plus `isHorde`. DB bumped to version 5 (destructive, pre-release).
- Removed the unused Health Connect dependency (heart-rate input is v1.1+).
- Statistics' trend line is labeled "forecast accuracy per session" — it was never the Fidelity EWMA and shouldn't have implied it.

## [0.3.0] - 2026-08-20

Zombie Horde as a full alternate opponent mode, Simulation moved out into its own standalone APK, and a reduced-motion fix.

### Added
- **Zombie Horde opponent mode**: a new Setup Step 1 (Opponent Type: Rival Twin vs. Zombie Horde). Horde path skips naming entirely — a lore screen (decommissioned Twins get recycled into the horde) plus an intensity picker (Calm/Standard/Relentless), no name given. See `docs/zombie-mode.md`.
- `HordeSoundCues`: bracketed, non-verbal captions (`[snarling, just behind you]`) for Fallen Back / Tracking / Swarming states plus ambient/idle captions — never comprehensible words, per the brief. Intensity only varies the Swarming bank.
- `TwinProfileEntity` gains `opponentType` (`"twin"` | `"horde"`) rather than a parallel table; `fidelity`/`generation`/`personaKey` are reinterpreted as Proximity/Wave/intensity-key for a Horde profile. DB bumped to version 4.
- `SessionFinalizer`, `ForecastViewModel`, and `DebriefContent` all branch on `opponentType` to relabel Fidelity→Proximity, Generation→Wave, Composure→Aggression (Cowed/Watchful/Predatory → Fallen Back/Tracking/Swarming), and swap persona lines for Horde captions — same math, same notification pipeline, different fiction.
- Fixed two latent crash bugs found while wiring this up: `IdleTauntWorker` and Simulation mode both called `Personas.byKey()` unconditionally, which would throw for a Horde profile. Both now branch correctly.
- Verified live on device end to end: Opponent Type → Horde setup (Relentless) → Forecast (`Relentless · Wave 1 · Proximity 50%`, `[no signal yet]` cold-start caption) → a logged session producing `Proximity now 50% · Aggression: TRACKING` and a real bracketed caption in the Debrief. No crashes.
- **Simulation mode moved out of the main app** into a new standalone Gradle module, `:simdemo` (applicationId `dev.eversorhn.gait.simdemo`) — the real app now has zero demo/dummy content by design, not just a warning label. See `docs/simulation-mode.md`.
- `:simdemo` is a single self-contained screen: no Room, no navigation graph, no permissions, a fixed demo session (5 km / 25:00 vs. a fixed "Markus K." opponent), duplicated (not shared) theme/chrome for full independence, installs side by side with the main app.
- Fixed a reduced-motion bug found via user testing: Android's animator-duration-scale=0 setting (common in Developer Options) collapses Compose's `animateTo` to instant, which broke Simulation's entire premise. Added `withFullMotion` (`MotionDurationScale` override) scoped only to Simulation's ramp — every other animation in both apps continues to correctly respect the system's reduced-motion setting, since that's the right default for decorative motion.
- Verified `:simdemo` live on device: installs alongside the main app, the animated ramp progresses in real time with correct distance/gap math, immersive fullscreen matches the main app, no crashes.

## [0.2.0] - 2026-08-20

The first real build: a working Android app, not just design docs. Setup → Forecast → GPS or indoor tracking → Debrief runs end to end on a physical device, with Composure, Rest Days/Vacation, Statistics, a Simulation mode, and full cyberpunk-corpo visual chrome.

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
- Real cyberpunk-corpo visual chrome, not just a dark palette: a condensed display face for headlines and monospace for every label/data readout, small technical shape rounding app-wide, a static grid+scanline background texture, a persistent HUD statusbar (live clock, screen label, actual device battery %), and a bordered `CorpoPanel` replacing flat filled cards. Verified on device: real battery level renders correctly.
- Rest Days & Vacation: declared weekly rest days (toggle any of the 7 ISO days) plus a 30-day/year vacation bank spent as a contiguous block, with an anti-gaming line past 3 declared rest days/week. Forecast shows a calmer rest-day/vacation message without blocking training if the user goes anyway. Verified live on device including the anti-gaming trigger and a 7-day vacation period.
- Simulation mode: an animated, clearly-labeled demo session ("SIMULATION — NOTHING IS SAVED") that never touches SessionDao or TwinProfileDao, reusing the real Twin's name/persona/cowed-lines for flavor. Verified correct math and no DB writes on device.
- Statistics screen: 7D/30D/ALL period filter, an aggregate summary (session count, distance, avg pace, Fidelity, a simple trend line), and a per-session history list with forecast deltas and a MANUAL tag for self-reported entries. Verified live on device with real logged data.
- Incident note: a stray blind UI-test tap briefly navigated out of the app into another app's private conversation. No action was taken there; recovered immediately; logged here as a reminder to always screenshot-verify before chaining taps.

## [0.1.0] - 2026-08-20
### Added
- Project initialized. First brainstorming round for a cyberpunk-corpo themed GPS movement app without generic missions/achievements.
