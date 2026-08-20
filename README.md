# GAIT

**Status:** Early build — the Android app has started. Setup/Naming → Forecast → manual session logging → Debrief is working end to end on a real device (live GPS tracking isn't wired up yet). Free with optional donations, aiming at a real v1 release. See [`docs/scope-and-stack.md`](docs/scope-and-stack.md) for the tech stack and MVP scope, and `app/` for the source.

GAIT is a concept for a GPS movement app (running, walking, cycling, e-scooter, ...) that deliberately avoids generic missions, achievements, or leaderboards against strangers. Everything is cyberpunk-corpo themed at its core: your movement data is a commodity that fictional corporations compete over.

## Core mechanic: Asset Twin

GAIT's current focus mechanic. An AI builds a digital twin from your own training history that tries to predict you — a corporate experiment testing whether you could be replaced by a simulation. No competition against strangers (no scheduling, no other people's cancellations) — only ever against a version of yourself.

You name the Twin yourself at setup — for example, after someone you never want to see win again. That name then appears in every forecast, every live comparison, and every message it sends.

The loop has six phases:

| Phase | Description |
|---|---|
| 0 · Setup | You give the Twin a name. Presets: "Hated Person," "Better Self," "Just Twin-7." |
| 1 · Forecast | Before the activity, the Twin predicts exactly what you'll do today (pace, route, finish time) — based on real patterns. |
| 2 · Live Divergence | During the activity, anything unpredictable (a new route, a negative split, training in the rain) weakens the Twin's Fidelity live — not just running faster. |
| 3 · Fidelity Debrief | After the session: Forecast vs. Actual, plus the overall Fidelity trend over weeks. Fidelity almost always creeps up slightly over the long run. |
| 4 · Decommission Trial | Once Fidelity crosses a threshold: a single live duel against the Twin's strongest session to date. Deliberately rare. |
| 5 · Generational Handoff | The Twin messages you in a personal tone, quoting your own data. A win spins up a new, sharper generation. |

The central tension: you explicitly do **not** want your Twin to get better or to win.

## Composure: dominance-reactive tone

The better you are than the Twin, the smaller it gets — quiet, defensive, sometimes visibly losing its nerve. The moment it senses you slipping, it goes for the throat immediately, breaking containment with a same-day notification. Dark, blunt, and openly demeaning at its harshest — with a user-facing intensity setting (Off / Rival / Brutal) since this only works for people it clicks with. Details: [`docs/composure-system.md`](docs/composure-system.md).

## Twin personas & dialogue variation

17 selectable starting personas for the Twin (3 base presets + 14 pre-voiced archetypes like "The Auditor," "The Doppelgänger," "Future You"), each with its own voice. Everything the Twin says has to feel like a real, never-repetitive person/AI — driven by data-grounded templates instead of fixed text, state-conditioned tone, and deliberate callbacks. Details: [`docs/twin-personas.md`](docs/twin-personas.md). Default content language for the app is English.

## Activities & competitive dimensions

GPS naturally captures pace/route/distance — a good fit for endurance sports, but not for motor-assisted movement (e-scooter, e-bike), where consistency, route novelty, and reliability are the fair competitive dimension instead of speed. Every activity gets an independent Twin profile (own Fidelity, own Generation, own persona). Details: [`docs/activities-and-dimensions.md`](docs/activities-and-dimensions.md).

## Telemetry & forecasting

How the Forecast, Fidelity, and Composure numbers actually get computed — sensor inputs, activity auto-classification, a small-data-appropriate forecasting approach, the training-load concepts (EWMA, ACWR-style load, route-similarity metrics) behind them, and how rest days work (declared, inferred, and the Twin's own post-Trial calibration window). Details: [`docs/telemetry-and-forecasting.md`](docs/telemetry-and-forecasting.md).

## Live audio callouts

Spoken lines from the Twin mid-activity, for when the phone is in a pocket — triggered by live gap-threshold crossings and route events rather than a timer, with a hard cooldown and per-session cap so it never turns into constant chatter. Reuses the persona/Composure system as a new delivery channel. Details: [`docs/live-audio.md`](docs/live-audio.md).

## Notifications

The same-day Predatory exception from Composure, plus sparse, randomized "idle taunt" pings every 2–4 days so the Twin has a presence outside of active sessions without becoming annoying. Details: [`docs/notifications.md`](docs/notifications.md).

## Interactive demo

`demo/asset-twin-demo.html` — phone mockups of all six phases, cyberpunk-corpo styled. Open locally in a browser (no dependencies, no external requests beyond Google Fonts).

## Other, currently parked module ideas

From the original brainstorm, documented in [`docs/concept.md`](docs/concept.md), no longer the active focus:

- **Data Broker Loop** — sessions get live-auctioned to rival corporations.
- **Overwatch Zones** — dynamically generated surveillance zones on your route, avoid or cross through.
- **Cyberware Profile** — progress as an implant HUD instead of an XP bar.
- **Hostile Takeover Offers** — rival corporations personally poach you.

## Project structure

```
README.md                          — this document
CHANGELOG.md                        — history of concept decisions
LICENSE                             — MIT
docs/concept.md                      — full idea collection (all 5 original mechanics)
docs/twin-personas.md                 — 17 Twin personas + dialogue variation system
docs/activities-and-dimensions.md     — activities, competitive dimensions, profile architecture
docs/composure-system.md              — dominance-reactive tone system
docs/telemetry-and-forecasting.md     — sensor inputs, forecasting/fidelity algorithms, rest days
docs/live-audio.md                    — real-time spoken callouts during an activity
docs/scope-and-stack.md               — Android tech stack, on-device architecture, MVP scope
docs/notifications.md                 — same-day Predatory pings and idle taunts
demo/asset-twin-demo.html             — interactive HTML mockups of the Asset Twin loop
app/                                   — the Android app itself (Kotlin + Jetpack Compose)
```

## Support

If you'd like to support this project:

- Ko-fi: [ko-fi.com/eversorhn](https://ko-fi.com/eversorhn)
- PayPal: [paypal.me/FAMarco](https://paypal.me/FAMarco)
- Bitcoin: `bc1qv92c3eyeqvhgfnez7spfd7v2aytkhpshsl65yv`

## License

MIT — see [LICENSE](LICENSE).
