# Information Architecture — what goes where, in which order

The rule for every screen: **the one thing you came for is on top, unfolded; detail is one tap down; navigation is a swipe away.** Priority 1 = visible without scrolling on a 6" phone, 2 = one scroll or one tap, 3 = folded / secondary.

## Header (every screen, fixed)
1. HUD — clock · screen label · battery
2. Ticker — today's movers + division news, continuous
3. Ledger strip — `YOU n ─●─ n OPPONENT · standing`
4. Page indicator (main screen only) — Board · Forecast · Channel · Stats

## Board (first page on launch) — "where do I stand?"
| Prio | Element |
|---|---|
| 1 | Enrolled · Under review · Next cull tiles |
| 1 | **YOU** row (rank · arrow · one context line · index/Δ) |
| 1 | **Opponent** row, same structure |
| 1 | Top 5 of the table (tap header/foot → top 15) |
| 2 | Movers today (folded, biggest mover in the header) |
| 2 | Decommissioned (folded) |
| 2 | Continue to forecast |
| 3 | Dossier (tap any row) |
| — | Termination notice replaces all of the above when the cull caught you |

Horde: the containment map replaces the table; tiles become Behind you · Proximity · Nearest.

## Forecast — "what's expected of me today, and what's at stake?"
| Prio | Element |
|---|---|
| 1 | Division memo (folded to first sentence) |
| 1 | The opponent's line + Pace/Speed · Distance · Finish (or Route · Steadiness · Distance for motor-assisted) |
| 1 | **Stake panel** when the opponent has staked (COUNTER-STAKE) |
| 1 | **START ROUTE** (primary) |
| 1 | Substitution-eligible panel + START DUEL (only when Fidelity ≥ 95 %) |
| 2 | Asset ledger (score · form) |
| 2 | Asset file (folded), Asset status (folded; opens itself when a Trial is due) |
| 2 | Last message card |
| 3 | Log manually · Rest days · Settings |

## Track (pushed) — "am I beating it, right now?"
| Prio | Element |
|---|---|
| 0 | Pre-session: opponent's number for today + two start cards (tap = start) |
| 1 | REC · elapsed |
| 1 | **Gap clock** (+/− vs. the model at this distance) + Finish-if-held · Round-now · Board→ |
| 1 | Live comparison track + You/Them tiles |
| 2 | Distance · Moving · Climb |
| 2 | Divergence card, Splits, Comms |
| 1 | STOP (always reachable) |
| — | Indoor: model-finish countdown instead of the gap clock |
| — | Lock screen: the live notification carries elapsed · distance · your pace vs theirs · gap · stake |

## Debrief — "who won the round, and what did it cost?"
1. Ruling (round, points, score before → after) · 2. Commendation if earned · 3. Duel verdict if duel · 4. Forecast vs. Actual grid · 5. Fidelity + sparkline · 6. Opponent's line · 7. Back

## Direct Channel — one timeline, newest first, tags (stake / called / unprompted / debrief / commendation)
## Statistics — ledger for the period (segmented 7D/30D/ALL) → totals → accuracy curve → sessions
## Rest & Vacation — calendar (tap days) → weekly pattern → vacation bank (folded unless active)
## Settings — opponent (name/voice or intensity) → activity → voice → notifications → asset transfer → switch opponent → danger zone

## Notifications
- **Opponent** channel (default): same-day reaction · stake on today · unprompted · you-went-quiet. Title = who, subtext = what kind.
- **Division** channel (low): commendations · cull in 7 d / 1 d / today.
- **Tracking** (low, public on lock screen): live numbers while recording.
- Mute (exit dialog / Settings) silences the first two; the third is the foreground service's.
