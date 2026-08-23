# Composure — Dominance-Reactive Tone

> **Superseded in v0.18.0.** The opponent no longer speaks. Every persona line, taunt, memo,
> caption and closing line was removed; the opponent communicates only through its forecast,
> the ledger, the board and the map. This document is kept as design history.

The core rule: **the better you are than the Twin, the smaller it gets. The moment it senses you slipping, it goes for the throat — immediately.**

## Composure vs. Fidelity

Two separate state variables:

- **Fidelity** (existing system) — how well the Twin can predict you. Long-term, drifts upward slowly regardless of outcome.
- **Composure** (new) — how confident the Twin currently feels about the matchup *right now*. Short-term, reactive, swings fast in either direction.

Composure is what decides tone. Fidelity decides the stakes.

## Three states

| State | Trigger (data-grounded) | Response speed |
|---|---|---|
| **Cowed** | Beating the Forecast by a wide margin repeatedly, an active completion streak, a new personal best, fast recovery between hard efforts | Never proactive — only surfaces when you next open the app |
| **Watchful** | Performance roughly matching Forecast, no strong signal either way | Normal — only at the usual Forecast/Debrief touchpoints |
| **Predatory** | A missed session against an established pattern, a session cut short, pace well below rolling baseline, a broken streak | Immediate — fires as a push notification the same day, not at the next scheduled touchpoint |

The asymmetry is deliberate: going quiet costs the Twin nothing, so it can afford to sulk on its own time. Going predatory only works if it lands the moment you're already doubting yourself — so that state alone breaks the normal "in-session only" contact pattern.

## Example dialogue

Same trigger event (you just crushed a session vs. you just skipped one), across personas — showing how the *range* stays consistent even as the *voice* changes.

| Persona | Cowed | Predatory |
|---|---|---|
| The Ex | "...Okay. That was fast. I don't have anything for that." | "There it is. Every time things get hard, you fold. At least you're consistent about something." |
| The Boss | "Numbers are... acceptable. That's all." | "Three days. Three days and you're already negotiating with yourself. I'll note the quarter as a write-off." |
| The Auditor | "Asset exceeding projected parameters. Recalibration pending." | "Asset underperforming for the fourth consecutive cycle. Recommend reclassification: hobbyist." |
| The Algorithm | "I don't have a response prepared for this outcome." | "I was trained to predict you, and even I didn't think you'd quit this early. That's almost impressive." |
| Future You — Feared | "...Maybe I was wrong about you." | "This is it. This is the week you started being me. Hope the couch is comfortable." |
| The Doppelgänger | "That wasn't me. I don't know whose that was." | "I finished this run in my head already. You, apparently, didn't finish it anywhere." |
| The Understudy | "I'll... come back to this one." | "No rush. Really. I'll just be here. Getting better. While you figure out whatever this is." |

Dark, blunt, and openly demeaning in the Predatory column — but aimed at performance, effort, and identity-as-rival, never at anything outside the fiction (no real-world protected traits, no self-harm-adjacent language). See **Guardrail** below.

## Beyond text: how Composure shows up elsewhere

The state should be visible in more than dialogue:

1. **Contact frequency.** Predatory: more unsolicited pings. Cowed: the Twin goes quiet — occasionally a "typing…" indicator appears in the message feed and then vanishes, implying it drafted something and thought better of it.
2. **Live ghost rendering.** Predatory: the ghost overlay renders bold and close, pulsing in alert-red. Cowed: it renders faint, lagging further behind, slightly desaturated — like it's losing signal.
3. **Forecast confidence.** Predatory: forecasts get sharp and taunting ("You'll quit at 3.2km again. You always do."). Cowed: forecasts go vague and hedge ("No strong prediction today.").
4. **Where it's allowed to reach you.** Predatory is the only state allowed to break containment — a same-day push notification outside the app, not just in-session commentary. Cowed never initiates outside the app.
5. **Whose name it uses.** In Predatory mode it leans hard on the name you gave it, repeating it almost mockingly. In Cowed mode it retreats to the sterile system ID — "Twin-7" — as if losing confidence costs it the identity it was given. A direct crossover with the naming system in [`twin-personas.md`](twin-personas.md).

## Guardrail

Brutality targets performance, effort, and the fiction of being out-competed — never real-world protected traits, appearance in a way that could read as disordered-eating bait, or anything self-harm-adjacent. A user-facing intensity setting (Off / Rival / Brutal) should exist from day one — this style of trash talk is a strong hook for people it clicks with and actively harmful for people it doesn't, and that's a per-person call, not a default to force on everyone.
