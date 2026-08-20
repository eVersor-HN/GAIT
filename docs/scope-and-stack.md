# Platform, Stack & MVP Scope

## Decisions

- **Platform:** Android only (native).
- **Ambition:** a real product, aiming for eventual release — not just a design document.
- **Monetization:** free, donation-supported (Ko-fi/PayPal/Bitcoin, already in the README). No payment or subscription infrastructure needed.

These three compound: Android-only removes the cross-platform tax, donation-only removes payment infra, and together they point at a fully **on-device architecture with no backend server** — the single biggest scope-reducing decision available here, and the one everything below follows from.

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

## Why no backend

Every core system — Forecast, Fidelity, Composure, the session database — reduces to on-device computation over one person's own data. A backend would only be needed for things not in scope: cross-device sync, competition against other real people (explicitly rejected from the start), or server-side model training none of these systems require. Skipping it removes hosting cost entirely, which matters directly for a donation-only project with no guaranteed revenue, and it collapses the privacy story to almost nothing: no data ever leaves the device, so the privacy policy the Play Store requires for a location/health-permission app can stay short and honest instead of describing a data pipeline.

## MVP scope

Everything designed so far is the full vision, not the first release.

### Ships in v1
- One activity: **Running** only. No activity picker, no per-activity dimension logic yet — real complexity worth deferring.
- The full six-phase loop: Setup/Naming → Forecast → Live Divergence → Fidelity Debrief → Decommission Trial → Generational Handoff.
- A curated starting set of **5 personas** rather than all 17 — Hated Person, Better Self, Just Twin-7, The Ex, The Auditor — enough range to see what resonates before writing templates for the rest.
- Composure (Cowed/Predatory tone shift) — core to the hook, not optional.
- Rest days (declared + inferred) — cheap to implement, meaningfully improves fairness from day one.

### v1.1
- Live audio callouts — the most exciting differentiator, but a real chunk of Android complexity (foreground audio, TTS, audio focus) better added once the core loop is validated with real use.
- Same-day Predatory push notifications — needs careful battery-optimization handling to be reliable; not required to prove the loop works.
- Remaining 12 personas.

### v1.2+
- Additional activities (Cycling, E-Scooter, Hiking, Hand-Cycle, ...) and their activity-aware dimension defaults.
- Overseer Twin (cross-modal patterns) — depends on multiple active activity profiles existing first.

### Parked, not scoped
- The four other original mechanics (Data Broker Loop, Overwatch Zones, Cyberware Profile, Hostile Takeover Offers) — still just documented ideas in [`concept.md`](concept.md), no MVP path assigned.

## Play Store compliance notes

- Background location requires Google's mandatory in-app disclosure screen before the permission prompt — budget UI time for this, it isn't optional.
- Health & Fitness category apps get extra Play Console review scrutiny; expect a slower first review than a typical app.
- A privacy policy is mandatory for the location + Health Connect permissions regardless of the on-device-only architecture — required even when the honest answer is "we don't collect anything."
