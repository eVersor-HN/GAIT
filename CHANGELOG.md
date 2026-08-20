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

## [0.1.0] - 2026-08-20
### Added
- Project initialized. First brainstorming round for a cyberpunk-corpo themed GPS movement app without generic missions/achievements.
