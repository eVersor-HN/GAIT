# Platform, Stack & MVP Scope

## Decisions

- **Platform:** Android only (native).
- **Ambition:** a real product, aiming for eventual release — not just a design document.
- **Monetization:** free, donation-supported (Ko-fi/PayPal/Bitcoin, already in the README). No payment or subscription infrastructure needed.

These three compound: Android-only removes the cross-platform tax, donation-only removes payment infra, and together they point at a fully **on-device architecture with no backend server** — the single biggest scope-reducing decision available here, and the one everything below follows from.

**Modules:** `:app` is the real product. `:simdemo` is a separate, standalone demo APK (different `applicationId`, no shared data, no dependency on `:app`) — see [`simulation-mode.md`](simulation-mode.md) for why it's a second app rather than a mode inside the first.

## Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose | Current standard for native Android; older View/XML would be a step down for something aiming at real release. |
| Background GPS | `FusedLocationProviderClient` in a foreground service | Required for reliable tracking with the screen off; needs `ACCESS_BACKGROUND_LOCATION` plus Android's mandatory runtime disclosure screen before requesting it. |
| Motion sensors | `SensorManager` (accelerometer, gyroscope, barometer where present) | Feeds activity classification and elevation, as specced in [`telemetry-and-forecasting.md`](telemetry-and-forecasting.md). |
| Health data (optional HR) | Health Connect | Google's current unified health API (successor to the deprecated Google Fit API) — the correct integration point for a paired wearable's heart rate. |
| Local storage | Room (SQLite) | Holds the full session history the k-nearest-analog Forecast engine queries — entirely on-device. |
| Forecasting | Plain Kotlin, no ML framework | The k-NN analog approach in `telemetry-and-forecasting.md` is a weighted nearest-neighbor query over at most a few hundred rows — reaching for TensorFlow Lite would be solving a problem that doesn't exist at this scale. |
| Audio (Live Audio Callouts) | Android `TextToSpeech` + `AudioManager` audio focus (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) | On-device TTS avoids any per-request cloud cost; audio focus is the actual API behind the ducking behavior in [`live-audio.md`](live-audio.md). |
| Maps / route rendering | osmdroid (OpenStreetMap) over Google Maps SDK | Avoids Google Maps' per-load billing — meaningful for a donation-funded project with no guaranteed revenue. |
| Notifications | WorkManager | Schedules the same-day Predatory notification exception from [`composure-system.md`](composure-system.md); needs a battery-optimization exemption prompt to fire reliably. |
| Backend | None | Everything above runs on-device. No server, no hosting cost, nothing to maintain solo. |
| Release build | R8 + resource shrinking; signing via untracked `keystore.properties` | Release APK ~1.5 MB vs ~13 MB debug. The keystore never enters the repo (`keystore.properties.example` documents creating one with `keytool`). Debug builds get an `applicationIdSuffix` so debug and release coexist on a test device. |
| Tests | JUnit 4 unit tests on the pure engines | `ForecastEngine`, `ComposureEngine`, `RestDayPolicy` are plain Kotlin with no Android deps — exactly where a silent numeric bug would hide, and exactly where tests are cheap. |

## Why no backend

Every core system — Forecast, Fidelity, Composure, the session database — reduces to on-device computation over one person's own data. A backend would only be needed for things not in scope: cross-device sync, competition against other real people (explicitly rejected from the start), or server-side model training none of these systems require. Skipping it removes hosting cost entirely, which matters directly for a donation-only project with no guaranteed revenue, and it collapses the privacy story to almost nothing: no data ever leaves the device, so the privacy policy the Play Store requires for a location/health-permission app can stay short and honest instead of describing a data pipeline.

## MVP scope

Everything designed so far is the full vision, not the first release.

### Ships in v1
- One activity: **Running** only. No activity picker, no per-activity dimension logic yet — real complexity worth deferring.
- GPS tracking via a foreground service — live distance/pace/elapsed time, manual entry kept as a fallback. *(built)*
- Indoor mode (treadmill) — same timed foreground service without GPS, self-reported distance on stop, tagged and shown as unverified. *(built — see "Indoor vs. outdoor" in `activities-and-dimensions.md`)*
- Forecast → session logged (tracked or manual) → Fidelity/Composure update → Debrief, in one loop. *(built)*
- A curated starting set of **5 personas** rather than all 17 — Hated Person, Better Self, Just Twin-7, The Ex, The Auditor — enough range to see what resonates before writing templates for the rest. *(built)*
- Composure (Cowed/Predatory tone shift) — core to the hook, not optional. *(built)*
- Same-day Predatory notifications and sparse, randomized idle taunts. *(built — see `docs/notifications.md`)*
- A confirmation dialog when leaving mid-recording, so the back gesture can't silently interrupt a live session. *(built)*
- Rest days (declared + inferred) and the 30-day vacation bank — *enforced* in `SessionFinalizer` / `IdleTauntWorker`, not just displayed: Fidelity frozen, Composure neutral, no notifications. *(built, verified)*
- Statistics screen — period summary, per-session history, per-session delete. *(built)*
- Settings — rename / re-voice the Twin, change horde intensity, switch opponent type, erase everything. *(built)*
- Crash-safe sessions — `START_STICKY` service + persisted in-progress snapshot + "Interrupted session found" recovery. *(built, verified by force-stopping mid-run)*
- Moving-time pace with auto-pause, monotonic clock, clear "not saved because…" feedback on a too-short stop. *(built)*
- Zombie Horde as a selectable alternate opponent at setup, alongside the Rival Twin — see `docs/zombie-mode.md`. *(built)*
- Simulation as a separate, standalone `:simdemo` APK rather than a mode inside the real app — see `docs/simulation-mode.md`. *(built)*
- Unit tests for the three pure engines (Forecast, Composure, RestDayPolicy). *(built — 26 tests)*
- Release signing (untracked `keystore.properties`) + R8/resource shrinking, debug/release installable side by side. *(built, verified on device)*
- Live Divergence and Decommission Trial screens (currently only in the HTML concept demo, not yet wired into the app).

### v1.1
- Live audio callouts — the most exciting differentiator, but a real chunk of Android complexity (foreground audio, TTS, audio focus) better added once the core loop is validated with real use.
- Remaining 12 personas.
- Route storage and route-novelty scoring (currently only distance/pace/duration are recorded, not the GPS polyline itself).

### v1.2+
- Additional activities (Cycling, E-Scooter, Hiking, Hand-Cycle, ...) and their activity-aware dimension defaults.
- Overseer Twin (cross-modal patterns) — depends on multiple active activity profiles existing first.

### Parked, not scoped
- The four other original mechanics (Data Broker Loop, Overwatch Zones, Cyberware Profile, Hostile Takeover Offers) — still just documented ideas in [`concept.md`](concept.md), no MVP path assigned.

## Play Store compliance notes

- Tracking runs inside a foreground service with an ongoing notification, which Android exempts from the `ACCESS_BACKGROUND_LOCATION` requirement — so the mandatory prominent-disclosure screen for that permission is avoided entirely, not just deferred. Only foreground `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` are requested, at the point tracking is first started.
- Health & Fitness category apps get extra Play Console review scrutiny; expect a slower first review than a typical app.
- A privacy policy is mandatory for the location + Health Connect permissions regardless of the on-device-only architecture — required even when the honest answer is "we don't collect anything."
