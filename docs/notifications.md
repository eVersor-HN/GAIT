# Notifications

Two kinds of Twin message break out of the app itself. Both share one notification channel ("Twin messages") so muting is a single system setting, not a hunt through in-app options.

## Same-day Predatory exception

Covered in [`composure-system.md`](composure-system.md): when the Composure state flips to Predatory after a session, that reaction is allowed to fire as a same-day push, not just an in-app line — this is the one state allowed to break containment, because it only works if it lands while the user is already doubting themselves.

## Idle taunts

The "random jabs every few days, not annoying" ask. Deliberately not a fixed daily timer — WorkManager's periodic floor is 15 minutes and a naive daily notification would get tuned out fast. Instead:

- A daily worker checks a randomized due-time, persisted locally.
- Nothing is sent until that time passes.
- After a taunt fires, the next one is scheduled 2–4 days out (uniformly random), so the cadence itself never becomes predictable enough to anticipate or resent.
- The first-ever run only sets the initial due-time — it never taunts immediately on install.
- Content comes from a persona's `idleLines`: lighter than in-session Predatory lines, since these aren't reacting to a specific weak session, just reminding the user the Twin exists.

Implementation: `IdleTauntWorker` (`app/.../work/IdleTauntWorker.kt`), scheduled once in `GaitApplication.onCreate()`.

## Permissions

Android 13+ requires runtime `POST_NOTIFICATIONS` permission — requested once on first launch. If denied, both notification types silently no-op rather than erroring; the in-app experience (Forecast, Live Divergence, Debrief) works fully without it.
