# Activities & Competitive Dimensions

## Why GPS defaults to endurance — and why that's not the whole story

GPS naturally captures pace, route, and distance, which maps cleanly onto endurance sports. But raw pace is meaningless for assisted movement (e-scooter, e-bike) — a motor is doing the work. The app needs activity-aware competitive dimensions, not one metric applied everywhere.

## Supported activity types

### Human-powered, endurance-relevant
- Running (road)
- Trail running
- Walking / hiking
- Road cycling
- Gravel / mountain biking
- Inline skating / skateboarding
- Cross-country skiing
- Hand-cycling / racing wheelchair — explicit inclusion, a genuine endurance sport and fully GPS-trackable; deserves the same competitive depth as running, not an afterthought.
- Kayaking / rowing / stand-up paddling — GPS pace exists but current and wind confound it, so these default to conditions-adjusted scoring rather than raw pace.

### Assisted / motor-supported
- E-scooter
- E-bike
- Powered wheelchair

Pace is not a meaningful signal here. Default competitive dimensions shift to consistency, route novelty, and route efficiency instead of raw speed.

### Casual / utility
- Commute walking, cycling, scootering
- Dog walking

Low-intensity, frequency-driven. The Twin behaves more like a habit auditor here than a performance rival.

## Competitive dimensions (not just pace)

| Dimension | What it measures | Works for |
|---|---|---|
| Pace / Speed | Raw effort per distance | Human-powered only |
| Distance / Volume | Total covered in a period | All |
| Elevation Gain | Vertical climbed | Running, hiking, cycling |
| Negative Split / Pacing | Smart effort distribution across a session | Running, cycling |
| Consistency / Habit Strength | Session frequency and regularity | All — especially assisted/utility |
| Route Novelty | New terrain vs. repeated ground | All |
| Route Efficiency | Actual path vs. shortest path | Commute-style trips especially |
| Conditions-Adjusted Effort | Performance despite weather, current, or wind | All, weighted per activity |
| Time-of-Day Reliability | Sticking to a stated schedule | All — strong "corpo asset reliability" framing |

## Profile architecture

- **Per-activity Twin, by default.** Running, cycling, and e-scooter each get an independent Twin: own Fidelity, own Generation counter, own persona. A pace-based rival makes no sense for scootering, so patterns don't transfer between activities.
- **Activity-aware dimension defaults.** Setting up a Twin for an assisted/utility activity auto-suggests Consistency + Route Novelty + Reliability instead of Pace, so the opponent stays meaningful rather than measuring something the motor did for you.
- **Independent personas per profile.** You can run against "The Ex" and commute against "The Auditor" — different rivalries for different parts of your life, or reuse the same persona everywhere for one continuous nemesis.
- **Optional Overseer Twin (cross-modal).** An advanced, opt-in meta-twin that watches all your activity-specific twins and comments on patterns between them — "You only cycle on days you skip your run." Framed as a holding company reviewing its subsidiaries, ties back into the Data Broker world-building (see [concept.md](concept.md)).

## Further ideas worth tracking

- Seasonal activities (e.g. cross-country skiing) could pause/freeze their Twin's Fidelity outside season rather than let it decay to zero — a "dormant asset" state instead of lost progress.
- A grace period for a brand-new activity, since there's no data yet for a Forecast — the Twin says so, in voice: *"No baseline on you yet. Don't get used to that."*
- Cross-activity Decommission Trials as a rare, higher-stakes event — the Overseer Twin challenges you on a combined index instead of a single sport.
