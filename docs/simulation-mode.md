# Simulation Mode — a Separate, Small APK

Originally a screen inside the main GAIT app; moved out into its own Gradle module (`:simdemo`, applicationId `dev.eversorhn.gait.simdemo`) so the real app stays free of demo/dummy content.

## Why a separate app instead of a mode

- **The real app has no dummy data, period.** A "Simulation" entry point inside the main app is one tap away from something that only *looks* like real content. Moving it out removes the temptation entirely rather than relying on a warning label to keep it from ever being confused with a real session.
- **A separate `applicationId` can't read the main app's database even if it tried.** Android sandboxes each app's private storage by `applicationId`; `:simdemo` has no Room dependency at all, so there's no code path that could accidentally touch real Fidelity, Proximity, or session history.
- **It's a better artifact for its actual job.** A tiny, standalone, shareable APK is what you hand someone to show them what GAIT feels like — a marketing/preview teaser — without asking them to install the real app, grant it location permission, or go through Setup first.

## What it is

One screen, no navigation graph, no permissions requested. On launch it immediately animates a fixed 5 km / 25:00 demo session against a slightly slower fixed opponent ("Markus K.", one of the Twin voices from the main app), then shows a result with a real flavor line pulled from that persona's actual line bank. Every run is identical in shape (same pace, same gap) since there's no real data to vary it — the point is showing the *feel* of the loop, not simulating variety.

A persistent red **"DEMO — NOT A REAL SESSION"** badge stays visible throughout, on top of the same cyberpunk-corpo visual chrome as the main app (duplicated, not shared — see below) so it's immediately recognizable as GAIT without being mistakable for the real thing.

## Reduced motion

The animated ramp *is* the app's entire content, not decoration — see `ui/util/ForceMotion.kt` (duplicated into `:simdemo` as well) for why it's pinned to full motion speed regardless of the system's reduced-motion setting, unlike everything else in either app.

## Why the theme/chrome is duplicated, not shared

A proper shared `:core-ui` Gradle module would be the "correct" long-term structure once there's a third consumer of the theme. For two modules and a few hundred lines of Color/Theme/CorpoChrome code, duplicating is the simpler tradeoff — no module-boundary API to design and maintain yet, and `:simdemo` stays trivially easy to reason about as fully standalone. Worth revisiting if a third app-like target shows up.

## Verified

Built and installed alongside the main app (`dev.eversorhn.gait` + `dev.eversorhn.gait.simdemo` coexist, confirmed via `pm list packages`). Live on device: the animated ramp progresses in real time, distance/gap math checks out against elapsed time, immersive fullscreen matches the main app, and the completed run shows a real persona line with no crashes.
