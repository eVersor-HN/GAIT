# Telemetry & Forecasting

## Design principle

"As mature as possible" here doesn't mean a bigger model — it means picking techniques that fit the actual data shape. A single user's training history is a small, noisy, per-person time series (dozens to low hundreds of sessions, not millions). A generic deep model would overfit and, worse, couldn't explain itself — and the product needs explainable output: the Forecast card cites *why* it predicts what it predicts ("Based on 41 Tuesday sessions..."). Everything below is chosen to stay small-data-appropriate and interpretable, not to sound impressive.

## Telemetry inputs

| Source | Derived signal | Reliability caveat |
|---|---|---|
| GPS trace (lat/lon/elevation/timestamp) | Pace, distance, route polyline, elevation change | Raw altitude is noisy; drifts in urban canyons and under tree cover — needs smoothing before use |
| Accelerometer / gyroscope | Cadence, activity-type signature | Ambiguous at the edges (slow cycling vs. fast walking) — treat as probabilistic, not certain |
| Barometer | Elevation / grade | More reliable than GPS altitude where available; not present on all devices |
| Heart rate (optional, paired wearable) | Effort/intensity independent of pace | Optional input — system must degrade gracefully without it |
| Weather API | Ambient temp, humidity, wind, precipitation | Needed to effort-adjust performance; forecast-vs-actual weather can diverge for outdoor sessions |
| Device clock / calendar | Time of day, day of week | High reliability, used as a core Forecast feature |
| Session history database | Full per-user, per-activity time series | The actual training corpus every model below runs on |

## Activity classification

Auto-detecting activity type (run vs. walk vs. ride vs. scooter) from speed profile + accelerometer signature is good but not certain at the margins. The classifier should expose a confidence score rather than a silent guess, and let the user correct a misclassification — that correction is high-value training signal, feeding back into the per-user classifier rather than being thrown away.

## Forecast engine

**Approach: weighted k-nearest historical analog, not a black-box model.** For a given upcoming session, find the *k* most similar past sessions — weighted by day-of-week match, time-of-day match, forecast weather similarity, and recent training load — and blend their outcomes (pace, likely walk breaks, finish time) into the prediction. This is case-based reasoning, not a neural net: it naturally produces a human-readable justification ("based on 41 Tuesday sessions"), degrades sensibly with little data, and doesn't need enough volume to train a generalized model that a single person's history could never provide.

**Recent training load** is a real feature here, borrowed from sports science: a rolling acute (7-day) vs. chronic (28-day) load ratio (ACWR-style) captures whether the user is fresher or more fatigued than their norm right now, and shifts the prediction accordingly.

**Forecast Confidence** is not flavor text — it's the variance of the k-nearest analogs' outcomes, inverted and scaled by *n*. A tight cluster of similar past sessions produces a high, sharp forecast; a scattered or thin history produces a hedged one. This is also the direct mechanism behind the Cowed-state "no strong prediction today" behavior in [`composure-system.md`](composure-system.md) — low confidence isn't a mood, it's what the statistic actually says.

**Cold start:** a brand-new activity type has no analog pool. The Forecast screen should say so explicitly rather than guess ("No baseline on you yet"), as noted in [`activities-and-dimensions.md`](activities-and-dimensions.md).

## Rest days

Two mechanisms combine, rather than one rigid rule:

- **Declared rest days.** At setup, users can mark specific days (e.g. "never Sundays") as rest days. On a declared rest day: no Forecast fires, no Composure evaluation happens, Fidelity is frozen rather than penalized. Useful immediately, without waiting on weeks of history.
- **Inferred rest days.** The k-nearest-analog engine above naturally has no confident analog pool for a day-of-week with no training history, so it stays silent there without being told to — the same cold-start behavior already covers this, not a separate rule.
- **Anti-gaming.** Declaring most of the week as "rest" defeats the point. Past a small number of declared rest days (roughly 2–3/week), the Twin should comment on the pattern itself in character rather than silently comply — *"Four rest days this week. I've stopped scheduling around them."* — treating excessive rest-day declaration as a Composure-relevant behavior signal, not a silent quota check.
- **The Twin's own downtime.** After a Decommission Trial, the new Generation doesn't start at full aggression — a short calibration window (24–48h) follows where Predatory-state behavior is suppressed, framed in-world as *"Twin-8 — recalibration in progress."* Corporate self-interest, not kindness, but it functions as a built-in pacing break so confrontation never feels relentless.

## Fidelity

Fidelity is an exponentially-weighted moving average (EWMA) of `1 − normalized forecast error`, so recent sessions move the number more than old ones — matching the week-over-week trend shown in the Debrief sparkline, rather than an unweighted lifetime average that would barely move.

Error is multi-dimensional, not just pace:

- **Pace error** — actual vs. forecast pace.
- **Route error** — how different the actual route was from the predicted one, measured via a similarity metric between GPS polylines (Fréchet distance, or a cheaper geohash-grid overlap against the user's historical route corpus for routes at scale). This is what "route novelty" in Live Divergence actually computes, not a vague heuristic.
- **Timing error** — actual finish time vs. forecast finish time.

## Composure

Composure thresholds are personalized, not global constants. "A missed session" or "pace well below baseline" is evaluated as a z-score against *that user's own* distribution of session outcomes and inter-session gaps — someone who trains twice a week and someone who trains daily have very different definitions of "a broken streak." Predatory triggers at roughly z < −1.5 sustained or a gap beyond the user's own historical rhythm; Cowed triggers at roughly z > +1.5 sustained. Full behavior spec: [`composure-system.md`](composure-system.md).

## Data quality & robustness

- **GPS smoothing.** Raw traces get simplified/smoothed (e.g. Kalman filtering or Ramer–Douglas–Peucker simplification) before any pace or route metric is computed — otherwise Forecast and Fidelity are garbage-in-garbage-out.
- **Elevation fallback.** Where barometer data is unavailable, elevation is pulled from a digital elevation model (DEM) lookup against the map-matched route rather than trusting raw GPS altitude.
- **Missing-sensor degradation.** No heart rate strap → effort estimated from pace, grade, and cadence alone. No barometer → DEM-based elevation as above. The system should always produce *something* usable, clearly marked as lower-confidence, rather than blocking.
