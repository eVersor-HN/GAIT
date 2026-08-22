# Changelog

All notable changes to this concept project are tracked here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [0.15.1] - 2026-08-22

Findings from the 30-run speedrun (new user / explorer / seasoned; 0 crashes, 232 screens reviewed), fixed:

### Changed
- **Settings is sections, not a scroll of chips**: Opponent summary → *Name & voice* (folded; the 17 personas as a list with a radio mark and a sample line each, not a cloud of chips) → *Activity* (folded; 2 × 4 equal chips) → *Sound & notifications* (two switches in one panel) → *Asset transfer* (folded) → Switch opponent → *Danger zone* (folded).
- **Live screen**: the divergence card no longer shows the confusing "Fidelity impact +0.9 % live" (mathematically right, humanly wrong) — it says the pace gap and steadiness/climb; tile labels shortened so nothing clips ("Finish · if held · vs 27:00", "Round · 4 pts riding", "avg 24.1km/h", "of 27:00").
- **Direct Channel**: the division's sender no longer wraps letter by letter ("Division"); header sender/tag both ellipsise; no Back button on swipe pages (Channel, Statistics) — a footnote says where to swipe. Statistics' empty state explains what lands there.
- **Board**: YOU/opponent context lines are one line ("New hire · protected 60 d"), "bottom 400" tile no longer clips; Horde map is 240 dp so the map page fits, career numbers one footnote.
- **First-session Debrief** gets a "What happens next" panel (forecast from session 2, stakes from 3, 60-day cull protection) instead of half a page of nothing.
- Ledger strip survives long opponent names (ellipsis; "THEY" in the standing).

## [0.15.0] - 2026-08-22

Consistency pass after a 30-run speedrun (new user / explorer / seasoned), notifications, lock screen.

### Changed
- **Level rows, real controls.** Buttons have fixed heights per kind (primary 52 · safe/risk 46 · ghost 40) and never wrap — a row of three is always level. Stat tiles are always three lines (label / value / sub-or-blank), so tiles in a row match. Every tappable console element has **press feedback** (2 % shrink + dim, 90 ms). Chips are pill-shaped with an animated fill and a selected dot; a **segmented control** with a sliding thumb replaces loose chip rows (Statistics period, Horde intensity); yes/no settings are **switches** (Voice, Notifications); option cards carry a radio mark and animate selection. One **CorpoDialog** replaces every Material AlertDialog (Track leave/recover, Statistics delete, Settings switch/erase) — console panel, level console buttons.
- Labels: "Enrol with this twin" / "Release the horde" instead of "Confirm"; "Indoor / outdoor" instead of "Change mode"; Settings headline no longer duplicates the opponent screen's.
- `docs/information-architecture.md`: what goes where, in which priority, on every screen.

### Added
- **Lock-screen live card.** The recording notification is now a live instrument, public on the lock screen: `GAIT · 12:40 · 2.31 km` / `You 5:12/km · Markus K. 5:30/km · +0:41 ahead` / `2 pts riding`, refreshed every ~5 s; indoor shows the model's finish countdown. No unlock needed to see where you stand.
- **Notifications reworked.** Own status-bar icon (the three strides), three channels — *Opponent* (default: same-day reaction · stake on today · unprompted · you went quiet; subtext says which), *Division* (low: commendations, cull in 7 d / tomorrow / today), *Tracking* (the live card). Grouped per sender, every one opens the app. Stakes and commendations now reach the shade (they only lived in the inbox before). Mute silences Opponent + Division at the source.

## [0.14.0] - 2026-08-21

The dimensions beyond pace — the part that makes cycling, hiking and the motor-assisted activities real.

### Added
- **Routes are stored** (`domain/route/RouteMetrics`): the outdoor service keeps a downsampled GPS trace (~25 m steps, "lat,lon;…") per session, plus **climb** (positive altitude gain with a 3 m noise filter) and **per-km splits**. DB v10 (`route`, `elevationGainMeters`, `consistency`, `routeNovelty`, `forecastConsistency`).
- **Route novelty**: each new route is compared on a ~55 m grid against everything before (Jaccard overlap); novelty = 1 − best overlap. **Steadiness**: 1 − CV of the km splits. The model's expected steadiness is an EWMA of your recent ones.
- **Motor-assisted activities are judged on the dimensions, not speed** (E-Scooter, E-Bike): the round goes to you for a genuinely new route (≥ 40 % new) or a ride steadier than the model expected (+2 pp); otherwise to the model. Forecast tiles become Route (usual-route share) · Steadiness · Distance, with a forecast line that says so ("A new route or a steadier ride takes the round — speed doesn't"). Debrief rows: Route (usual → new · 62 %), Steadiness (expected → actual), then speed for information. Pace activities keep the pace rule and additionally show Steadiness, Climb and Route rows when data exists.
- **Hiking / Cycling**: Climb tile on the Forecast (average of recent climbs), a live **Climb** tile with steadiness underneath, Climb row on the Debrief.
- **Indoor live screen** gets an instrument: "**Model finishes in** 12:30" counting down against the model's forecast finish; turns red with "+0:42 over its time — distance decides now" once past it; points riding shown.

### Changed
- Persona quotes say "speed" instead of "pace" for wheeled activities (Debrief, handoff, stake lines); indoor distance prompt is activity-aware (roller/trainer display, trip display, console); the vacation bank folds away unless a vacation is running.
- 4 new unit tests for RouteMetrics (round-trip, novelty on same loop vs. elsewhere, consistency/climb, downsampling) — 56 total.

## [0.13.0] - 2026-08-21

Speedrun pass through every activity, as a multi-sport user would: what's harmonious, what's reachable fast, what can't be chosen, where space is wasted, does the loop (before / during / after) hold.

### Changed
- **Board fits one screen.** YOU and the opponent are now two rows of identical structure — rank · arrow · name + one line of context · index/Δ — no extra career block on YOU (tenure / culls survived / best streak remain on the termination notice and the horde map). Protected new hires get their context line in neutral, not alarm red.
- **Activity-aware everywhere.** Cycling, E-Bike, E-Scooter and Hand-cycle read in **km/h** on the Forecast tiles, in the opponent's forecast line ("speed 27.1km/h"), the Debrief compare grid ("Speed"), Statistics, the Asset File and the live screen; pace activities keep min/km. The indoor card names the right thing per activity (treadmill · indoor trainer · treadmill / stairmill · roller / treadmill · timed only for e-scooter).
- **Pre-session screen does more, costs fewer taps.** "Indoor or outdoor?" now shows the opponent's number for today (speed/pace · distance · points riding) and each card **starts the session on tap** (outdoor asks for location first if needed) — one screen less between Forecast and recording.
- Ticker and ledger strip appear immediately after a fresh setup (the flows were keyed to the activity chosen before setup); the notification prompt now comes on the first screen (board), not on the Forecast page behind it.

## [0.12.2] - 2026-08-21

### Changed
- **Ticker is a real news ticker now.** It scrolls right-to-left at a constant ~52 dp/s driven by the frame clock — it never pauses or restarts when its content is refreshed (every minute), so there is no visible jump. Content is repeated until it spans at least two screen widths. Besides the movers it carries division news, interleaved: next cull countdown, under-review count, review day, hires and decommissions (30 d), the latest decommission, an open stake on today, the head-count — so there is always something moving even when no one moved this second.
- **Breathing room** between the static bars: HUD 8 dp, ticker with 6 dp above and below (24 dp tall), ledger row 4/8 dp, page indicator 8 dp below.

## [0.12.1] - 2026-08-21

### Changed
- **Less scrolling, less squeeze.** The header is three tight rows (HUD · ticker · ledger with the standing inline) plus the page indicator; the ticker is 22 dp. The **board shows the top 5** — tap the table header or its foot to unfold to 15, tap again to fold; career numbers are one footnote line; Movers and Decommissioned fold behind a one-line summary. The **Forecast** folds the division memo to its first sentence, the ledger panel is one row, and the secondary buttons are one row (Log · Rest days · Settings) — Board, Channel and Stats are a swipe away, so their buttons went.

## [0.12.0] - 2026-08-21

### Added
- **Spoken live commentator** (`audio/Commentator`, `domain/live/CommentaryScript`): one voice, the division's — not the persona. Android text-to-speech with a female English voice where the device has one (pitch 1.08, natural rate), ducking your music for the length of a line. Says what a commentator would: "Recording. Markus K. is running its forecast beside you. 2 points riding." · "Kilometre 2. 14 seconds ahead of Markus K." · "Markus K. is 40 metres behind you." · "You've taken the lead." · "Model confidence is down to 31 percent." · Horde: "The horde is catching up — 60 metres and closing at 18 a minute." / "You're pulling away." Cadence: opening line, every kilometre, lead changes (after the first minute), a status line every ~2 minutes; 40 s cooldown, 20 lines per session. Settings → Voice: On / Off (default on). No engine → silent; the on-screen comms stay the primary channel. Timbre work per `docs/voice-design.md` is the next step (the system voice is the placeholder).

## [0.11.0] - 2026-08-21

### Added
- **Live screen that's worth watching.** A **gap clock** at the top — seconds ahead/behind the model *at your current distance* (`+2:33 at 1.36 km`), green/red — with three projection tiles: finish if you hold this rolling pace vs. the model's; who the round goes to right now and what's riding; and **"Board →"**, your rank on the division board if the round landed now, with ▲/▼ places vs. today. Underneath, the model's **confidence decaying live** as your session average drifts off its number. **Splits** build up per completed kilometre: your split, their split, difference. Speed-based activities (cycling, e-bike, e-scooter) read in km/h. Horde mode replaces the gap clock with **separation in metres** and a closing/falling-back rate per minute (they move at your projected pace); turns red under 50 m.
- `docs/voice-design.md` — the one GAIT voice (young female AI timbre, 70/30 natural/synthetic, delivery and processing chain) for the upcoming live-audio work.

## [0.10.0] - 2026-08-21

### Added
- **Asset transfer.** Settings → "Export my asset · share" writes the division's assessment of *you* as a readable `GAIT-ASSET/1` text block — archetype ("reads as the comeback"), talent, consistency, grit, drift, training time, rest days, index, tenure, rounds, strengths/weaknesses and a two-line assessment — all derived from your real sessions and ledger (`domain/transfer/AssetTransfer`). Share it any way you like. Another GAIT pastes it under "Import asset": your asset then occupies a slot in *their* division with those fixed traits, simulated from the import day — it climbs, gets reviewed, shows as TRANSFER on their board with its own dossier, and can be decommissioned or culled there (transfer slots are never rehired). Imported assets are listed in Settings and can be removed. Stored in `imported_assets` (DB v9, `Migration(8, 9)`). 3 new unit tests (round-trip, assessment reads the data, garbage rejected) — 52 total.

## [0.9.0] - 2026-08-21

### Changed
- **A new asset starts last.** Enrolment index is below the floor (`START_INDEX = 250`), for you and for your model alike (ties go to you); every ledger point is +30 — the first places come fast, the top takes a long lead. The division memo and the board say so ("Everyone starts last"), and new hires see how many protected days they have left before the quarterly cull can touch them (60).
- **Repository now actually receives the app context** — the chosen activity is persisted across launches (it wasn't, since 0.8.0; the constructor call in `GaitApplication` still used the one-arg form).
- **Roster preload** at process start (background), so the board is usually ready when the splash hands over; the loading screen says why it takes a few seconds on the first open of the day.
- Ticker refreshes every minute like the board; no track graphic indoors (it only moved by time); Room schemas are now exported to `app/schemas/` (`exportSchema = true`) so future migrations can be written and tested against a real history.
- **Debug builds only:** a Developer section in Settings — "Fidelity → 96 %" (reach the Trial) and "Seed 6 sessions" (get a forecast, stakes and a ledger without a week of running).

### Added
- `docs/privacy-policy.md` — the honest version: nothing leaves the device; the division is a simulation.

## [0.8.2] - 2026-08-21

### Changed
- **Live pace is a rolling 200 m window** (falls back to the session average until there's that much ground), so the divergence card and the opponent's callouts react to what you're doing now; the tile shows "last 200 m · avg X" underneath. The Debrief still judges the whole-session moving pace.
- **Tenure / founding day** come from the earliest profile across activities, not the active one — switching activity no longer resets "company days".
- **Forecast is shorter by default**: Asset File and Asset Status fold behind their headers (one-line summary shown; tap to open; Status opens itself when a Trial is due). Message cards with a long sender no longer collide with their tag.

## [0.8.1] - 2026-08-21

### Added
- **Swipe navigation.** The main screen is now a pager: Board (or Containment Map) ↔ Forecast ↔ Direct Channel ↔ Statistics, with a labelled page indicator under the ledger strip (tap to jump, swipe to move). Opens on the board as before; Track, Log, Rest & Vacation and Settings stay as pushed screens. The exit dialog now lives once, on the main screen, so the system back gesture always asks before the app closes. Channel and Statistics refresh whenever their page is entered.

## [0.8.0] - 2026-08-21

The division becomes a place you can be fired from. Plus: all 17 personas, a real ticker, a live board, the opponent's own row, a rest calendar, an activity picker, an exit dialog, dossiers, commendations.

### Added
- **Quarterly cull.** Headcount now breathes between ~900 and 1,300: new hires arrive at ~4.5 a day, and every 90 days the bottom 400 by Retention Index (tenure ≥ 60 days) are decommissioned — "fired, gone, whatever" — and their slots refilled over the following quarter. The board shows the **cull line** ("#762 — 640 below you"), days to the next cull, and your row turns red below the line. **If you are in the bottom 400 at a cull, the app is over for that asset**: a termination notice replaces the board (rank, line, tenure, culls survived, best streak) and the only way on is **Enrol a new asset** — everything is wiped and setup starts again. New hires (and new users) get a 60-day grace. Deterministic: the verdict is recomputed from the simulation and your ledger as of that day, so it can't be dodged by reinstalling.
- **All 17 personas** (`PersonaRoster.kt`): The Sibling, Former Best Friend, Mentor Who Moved On, Rival Teammate, The Understudy, Replacement Hire, The Boss, Future You (Ideal), Future You (Feared), Younger You, **The Doppelgänger** (the AI clone of you — built from your data, speaks as if it *is* you, intends to be the better copy), The Algorithm — each with forecast, cowed/watchful/predatory, idle, handoff, duel-lost, stake, call, and live ahead/behind/level banks. "Just Twin-7" is now **The Model** (same key, existing profiles unaffected).
- **The opponent's own row on the board.** Its index is the same ledger read from its side (its lead, its streak; Fidelity *helps* it) with the same soft clamp — so it sits exactly where the rounds put it, ahead of you when it has earned that, behind when it hasn't. Shown under your row and inline in the top 15; in the ticker too. Ties on day one go to you.
- **Live board.** The Asset Board re-evaluates every minute while open; an asset's result lands at its own training minute (now ± up to 40 min per day), so rows move when *that person* finishes — not every second, not on a timer. Reopening the app recomputes from scratch (the simulation is pure; cached per day).
- **Dossier on tap.** Any row (and any mover) opens the division's file: rank, index, tenure, 14-day sparkline with best/worst, what the asset "reads as", drift this month, when its results land, rest days, hire number; synths flagged.
- **Ticker** rebuilt: constant right-to-left speed (~56 dp/s), fixed 26 dp height, clipped to its own box, duplicated content for a seamless loop — it never overlaps the ledger strip.
- **Containment map** rebuilt: you are ahead (upper third, heading up), the horde spreads in a cone *behind* you, newest closest; range rings 100 m. The "joined the horde" panel is gone.
- **Exit dialog**: back on a root screen (Board, Forecast) asks "Leave the floor?" — Close · keep notifications / Close · mute notifications / Stay. Mute is a real switch (`NotificationPrefs`): the opponent keeps writing to the Direct Channel, nothing reaches the notification shade. Re-enable in Settings ("Notifications: On / Muted").
- **Rest & Vacation calendar**: a month grid (prev/next, Monday-first) where any day from today on can be tapped off in advance; planned days are stored (`planned_days_off`, DB v8) and treated as rest days when they arrive — sessions count, Fidelity frozen, no stake, no notification. Weekly pattern and vacation bank stay; legend shows all three.
- **Activity picker** (setup step 1/3, the demo's "Wähle deine Sportart"): Running, Walking, Cycling, Hiking, E-Scooter, E-Bike, Hand-cycle, Wheelchair — each with its dimension hints and an honest note that v1 scores on pace. Every activity has its own opponent profile, Fidelity, generation, ledger; switchable in Settings (a new activity starts its setup). The active activity is persisted and shown on the Forecast.
- **Division commendations** — the company's version of a like: a formal note when the numbers back it (3 / 5 / 10 rounds clear of the opponent; forecast beaten by ≥ 0:30/km; ledger recovered from behind to level; 40+ / 100+ places climbed in a day). Recorded in the inbox (from "Asset Performance Division"), shown on the Debrief and as the Forecast's last-message card.
- **Career numbers** on the board: tenure in company days, culls survived, best streak, rounds played.
- **Arcade pressure**: the opponent's stake scales with your lead — 2 normally, 3 at +4, 4 at +8 (counter-stake always doubles). Pressure, not punishment.
- **Easter eggs**: ~0.4 % of hires are familiar names — Marco Fattizzo, Chiara Thiele; synths Rubina, Fay, Kimmi, Noah, Aslan, Penny, Abei, Marsh.

### Changed
- Database v8 with `Migration(7, 8)` (new `planned_days_off` table). Repository defaults now follow the active activity; `GaitRepository` takes the app context for its tiny preference store.
- Roster tuning for liveliness: floor 340, wider talent range, stronger fader drift, bigger injuries and form cycles; fixed-arity hashing. 49 tests green (roster tests cover the dynamic headcount, firings ≥ 400 over the prehistory, synths, gender split, intraday movement, user/twin index behaviour and the one-rank-per-participant invariant incl. the opponent).

### Verified
- Emulator over v7 data: migration → Containment Map with the horde behind you → Settings switch to Twin → Asset Board with your row, the opponent's row (#1161 while you lead 6–0), top 15, dossier dialog → Rest & Vacation calendar with two days planned → exit dialog on back. Screenshots reviewed.

## [0.7.0] - 2026-08-21

The division gets a roster. You are no longer one asset against one model — you are one of 1,001 assets the Asset Performance Division ranks, reviews, decommissions and replaces. Opening the app now lands on the **Asset Board** (Twin) or the **Containment Map** (Horde); a stock-style **ticker** of today's movers runs under the HUD on every screen.

### Added
- **Roster simulation** (`domain/roster/RosterEngine`): 1,000 simulated assets — women and men 50:50, ~4 % humanoid synths with designations and callsigns (`KR-78 “Ferrule”`, id `SX-0972`) — each with an archetype (grinder, metronome, sprinter, weekend warrior, comeback, fader, early bird, night owl, steady hand), talent, consistency, grit, a monthly trend, a training time of day, weekly rest days, leave periods (synths: maintenance windows), the occasional injury, and a unit. Their **Retention Index** moves day by day as a noisy mean-reverting draw around their level; results land at *their* training minute, so the board shifts through the day. Every 14 days the division reviews: under the floor (340) → **decommissioned**, slot rehired three days later with a new person. **The decommissioned are the horde.** Fully deterministic from (slot, hire, day) via hashing — no rows in the database, yesterday's board is the same function at day − 1, and the division has ~14 months of history before the user enrolled (≈ 70 decommissions on file at enrolment). ~0.3 s to simulate, cached per process per day.
- **Asset Board** (first screen after launch, Twin): enrolled / under review / decommissioned-30d tiles; **your row pinned** (#rank, ▲▼ vs. yesterday, index, Δ, "2 places off the board"); the **top 15** with ▲ green / ▼ red rank arrows, index and Δ, status tags (NEW HIRE, ON LEAVE, MAINTENANCE, UNDER REVIEW, INJURED), synths in cyan; today's biggest movers; the most recent decommissions ("Folded into containment. They don't leave — they follow."); review countdown. Your own Retention Index lives in the same space: 500 at enrolment, up with ledger lead and streak, down with Fidelity (a well-modelled asset is a replaceable one), soft-clamped to 0–1000.
- **Containment Map** (first screen, Horde): you at the centre, every decommissioned asset a red dot — newest closest, range set by Proximity — on 100 m rings, with the "joined the horde" list.
- **Ticker strip** under the HUD on every screen: a continuously scrolling `▲ MATTEO N. KRÜGER #87 +21 · ▼ KOFI NYGAARD #982 −79 · · YOU #86 0 …` of today's movers, you in brass.
- Forecast: "Asset board" / "Containment map" button. Unit tests for the roster (determinism, every asset ranked once, history with firings/rehires/synths, gender split, intraday movement, user index behaviour). 49 tests total, all green.

### Verified
- Emulator: launch → Asset Board with you at #17 (▲), top 15 with arrows/tags/synth, movers, decommissioned; ticker scrolling; switch to Horde in Settings → Containment Map with 72 dots and the recent-decommission list. First roster build ~3 s on the emulator before the hashing rewrite, now well under a second.

## [0.6.0] - 2026-08-21

The competitive layer. v0.5.0 made the screens look like the demo; this release makes it feel like there's someone on the other side who wants you to lose — and a division that requires you not to. Everything is framed corpo: you are the asset, the Twin is your proposed replacement, the Asset Performance Division keeps score.

### Added
- **Asset Ledger** (`domain/ledger`): every session with a forecast is a *round*. Beat the forecast pace → your point(s); match or miss it → the opponent's. Points sum the round's stake. Derived from stored sessions (never disagrees with history, survives a generation handoff). Exposed as a **tug-of-war strip under the HUD on every screen** — `YOU 6 ──●── 0 MARKUS K.` with the standing ("You lead by 6 · streak 3 you") — plus a ledger panel on the Forecast (score, last-5 form dots), a **Ruling** panel on the Debrief ("Round to asset · +4 pts", score before → after), per-round points in the Direct Channel, and a per-weekday record in Statistics ("Fridays are yours, 2–0" / "Markus K. owns your Mondays, 3–0").
- **Model commitment (stakes)** (`domain/wager`): once per day, when the forecast is confident enough (≥ 55 %, ≥ 3 sessions, not a rest day), the opponent **puts 2 points on its own forecast** in its persona's voice ("You won't beat 5:30/km today. I'd put money on it — so I'm putting 2 points on it."). A **COUNTER-STAKE** button makes the round worth 4 either way; the opponent reacts ("Doubled. Remember you did that to yourself.") and the reaction lands in the inbox. The stake is consumed by the day's first scored session. The Track screen shows what's riding on the round; the Debrief ruling shows "called stake · 4 pts".
- **Live comms** (`domain/live/LiveCommentary`): during an outdoor session the opponent talks — at every kilometre mark and on lead changes, never on a timer, 45 s cooldown, 12 lines per session max. Persona-voiced per zone ("Km 1. 2:26/km under my number. Enjoy it while it lasts." / "0:18/km behind. Exactly where I said you'd be."), horde equivalents as captions. Shown in a COMMS panel on the Track screen, newest first.
- **Memos from the Asset Performance Division** (`domain/directive`): one memo per Forecast, picked from the ledger and Fidelity ("Asset trails its model by 3. The division does not fund assets that lose to their own forecast. Close the gap." / "Substitution review open. Model fidelity 96 % exceeds the 95 % retention ceiling…"). The company's voice — requirement, not taunt.
- **Asset File** (`domain/intel`): one data-grounded observation per Forecast, chosen by how pointed it is — days since your last session, a streak against you, which weekday the opponent owns and whether today is it, your slow/fast weekdays by average pace, your best pace and how long ago it was. Every line cites a number from the log; nothing is invented.
- **Inbox table** (`twin_messages`): idle taunts, gap-predatory pings, stakes and call reactions are now stored, not just notified. The Direct Channel merges them with the Debrief lines into one timeline with tags (stake / stake called / unprompted / you went quiet) and an "N unprompted" count.
- Persona banks: `stakeLine`, `callLines`, `liveAheadLines`, `liveBehindLines`, `liveLevelLines` for all 5 personas; horde `stakeCaption` / `callCaption` / `liveAhead` / `liveBehind` / `liveLevel`.
- Unit tests for Ledger (round rules, ties to the Twin, stakes sum, streak/form, weekday ownership), WagerPolicy (when it stakes, round stake 1/2/4, local epoch-day), LiveCommentary (km marks, lead changes with cooldown and level band, per-session cap, silence without pace). 45 tests total, all green.

### Changed
- **Database v7** with a real `Migration(6, 7)`: `sessions.stake`, `twin_profiles.wagerStake / wagerCalled / wagerEpochDay / wagerClaim`, new `twin_messages` table. Verified on the emulator that v6 data survives.
- `SessionFinalizer` sets the round's stake (1 / 2 staked / 4 called / 3 duel), consumes the day's stake, and returns the round winner plus the ledger before/after.
- Forecast `refresh()` is serialised with a mutex — it's called from both `init` and the screen's `LaunchedEffect`, and the stake must be made exactly once (caught live as a duplicated stake message).

### Verified
- Emulator, over the v0.5.0 data: ledger strip on every screen → memo + asset file on the Forecast → third session makes the opponent stake 2 pts → COUNTER-STAKE → Track shows "4 PTS · CALLED" → mocked-GPS run with "Km 1. 2:26/km under my number…" in COMMS → Debrief "Round to asset · +4 pts", 2–0 → 6–0 → Direct Channel shows stake / stake called / debrief with "ROUND TO YOU · +4 PTS" → Statistics weekday record. Screenshots reviewed for each.

## [0.5.0] - 2026-08-21

The "the app is naked" release. v0.4.0 had the whole loop running underneath, but every screen was a few lines of text and a stack of Material buttons. This pass rebuilds the UI after `demo/asset-twin-demo.html` phase by phase, and wires in the two phases that were still only in the demo: Live Divergence and the Decommission Trial / Generational Handoff.

### Added
- **Widget kit** (`ui/theme/CorpoWidgets.kt`) mirroring the demo's phone mockups: stat tiles (brass = you, cyan = opponent), Forecast/Actual compare grid, Fidelity sparkline with the 95 % review line, meter bars, toned panels (warn / divergence / twin), opponent message cards tagged with the Composure state, pulsing REC dot, five-bar phase track, mono chips, console buttons (primary / safe / risk / ghost), selectable option cards. Every screen now composes these instead of raw `Text()` + `Button()`.
- **Phase 01 — Forecast** rebuilt: the opponent's line as a quote, PACE / DISTANCE / FINISH tiles, confidence + basis footnotes, "START ROUTE — REFUTE IT", an **Asset status** panel (big Fidelity %, meter with the review threshold, Fidelity-over-sessions sparkline, generation), the opponent's **last message** as a card, and the secondary actions as a ghost-button grid.
- **Phase 02 — Live Divergence**: the Track screen now runs the opponent alongside you. YOUR PACE vs **{TWIN} PACE** tiles, distance/moving tiles against the forecast, a live route track with both markers (yours by distance, the opponent's by moving time against its forecast finish — auto-pause doesn't hand it free ground), and a **Divergence card** ("0:13/km faster than Markus K.'s forecast · FIDELITY IMPACT: −1.4 % LIVE") computed with the same EWMA the Debrief will apply. Indoor shows the opponent's finish time instead.
- **Phase 03 — Debrief** rebuilt: Forecast/Actual grid for pace, distance and finish (actual tinted good/alert), big Fidelity % with the signed per-session delta ("+5 % this session" in red — up is bad for you), the replayed Fidelity sparkline, generation + next-review footnote, and the opponent's line as a Composure-tinted message card.
- **Phase 04 — Decommission Trial** (`domain/trial/DecommissionTrial.kt`): at Fidelity ≥ 95 % the Forecast shows a red **SUBSTITUTION ELIGIBLE** panel with a meter and **START DUEL**. A duel is an ordinary tracked session (outdoor or indoor) judged on average pace against the opponent's *strongest session* — the fastest pace you ever held over ≥ 1 km — with a 1 km minimum so a sprint can't count. The Track screen shows the target pace and a "Beat 5:25/km" briefing; the status bar reads ASSET REVIEW.
- **Phase 05 — Generational Handoff**: a won duel resets Fidelity to 61 %, advances the generation, and the opponent sends a handoff line quoting your data ("You beat my forecast 5 times this generation. I've adjusted. Generation 2 is watching now."); the Debrief turns green with DUEL: WON / FIDELITY RESET → 61 % / GENERATION 2 IS INITIALISING. A lost duel is Predatory by definition and gets its own line bank; Fidelity updates normally. Horde equivalents: "Outrun Trial", wave advance, non-verbal captions.
- **Direct Channel** screen: every line the opponent has ever said, newest first, as message cards tagged by date and Composure state (the demo's "Same twin. Same weeks." panel), with Cowed/Predatory counts. Reachable from the Forecast.
- **Setup** rebuilt: opponent type as two selectable cards with a CONTINUE; naming with a brass input, persona chips, and a "how this persona sounds" preview (one Cowed and one Predatory line) before committing; horde setup with a swarming-caption preview.
- `FidelityReplay` (`domain/fidelity`): the running Fidelity replayed deterministically from stored sessions (forecast vs. actual per row, rest days skipped, won duels reset), so the sparkline needs no extra storage and uses exactly the finalizer's update step. Splash shows the GAIT wordmark.
- Unit tests for `DecommissionTrial` (eligibility, meter, strongest-session target, verdicts) and `FidelityReplay` (per-session accuracy, replay vs. running update, rest-day skip, duel reset). 35 tests total, all green.

### Changed
- **Database v6** with a real `Migration(5, 6)` — `sessions` gains `twinLine`, `composureState`, `isDuel`, `duelWon`. Existing data on devices is preserved; the destructive fallback only still applies to pre-v5 schemas nobody has.
- `SessionFinalizer` stores the opponent's line and Composure state with the session, takes a `duel` flag, and returns the full Debrief payload (previous Fidelity, history, distance/finish labels, duel outcome). Composure is evaluated on the new session before it's written (a stub row carries the two fields the engine reads) so the verdict can be stored with it — same result as before.
- `formatDuration` handles hours; `formatDistanceKm` added. Settings / Rest days / Statistics / Log session headers and buttons restyled to the kit (no functional change).

### Verified
- On the `llmtest` emulator (API 35): setup → naming → cold Forecast → five manual sessions (Debrief grid, Fidelity delta, sparkline, Predatory card) → Forecast with tiles + asset status + last message → Direct Channel log → outdoor session with mocked GPS (REC · LIVE, live pace vs. twin pace, route markers, "1:04/km faster" divergence card) → Fidelity forced to 96 % → SUBSTITUTION ELIGIBLE panel → indoor duel → DUEL: WON, Generation 2, handoff card. Screenshots were reviewed for each.

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
