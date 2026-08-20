# Live Audio Callouts

Spoken lines from the Twin during an activity, for when the phone is in a pocket and the screen isn't an option. The entire design problem is the same one already solved for messaging in [`composure-system.md`](composure-system.md): it has to feel like a real presence reacting to *this* moment, not a loop of stock phrases — which here specifically means never firing on a fixed timer or fixed distance.

## What triggers a line

Not time, not distance — events:

- **Gap-threshold crossings.** The live delta between you and the Twin crosses a meaningful boundary: first time it drops under 60s, under 30s, under 10s, crosses to zero (Twin takes the lead), or widens back past a "safe" margin after being close. Each boundary fires once, on the crossing — not continuously while true. This is the direct home for the example line: *"Not far behind. Give it a hundred meters."*
- **Route events.** Approaching a landmark or split where the Twin's historical data says something's about to happen — a hill it usually gains ground on, a stretch you usually slow at.
- **Session-phase awareness.** Sparser during steady-state middle miles, more frequent in the closing stretch — the same pacing real race commentary uses, quiet through the grind and louder at the finish.
- **Composure state.** Predatory sessions get a higher chatter budget; Cowed sessions stay mostly silent, maybe one deflated line near the end; Watchful sits at a moderate baseline. Same three states as [`composure-system.md`](composure-system.md) — this is a new delivery channel for that system, not a new one.

## Hard throttle

Trigger volume alone must never translate directly into speech volume:

- Minimum cooldown between any two lines: roughly 90–120 seconds or ~400m, whichever is longer, no matter how many triggers fired in between.
- Session cap: roughly 6–10 lines for a typical session, so even a back-and-forth session with constant gap-crossings doesn't turn into commentary.
- A gap-threshold only re-fires after moving back out of the band and crossing in again — sitting exactly on a boundary doesn't repeat the line.

## Content

Reuses the existing persona voice profiles and templating approach — data-grounded, state-conditioned, anti-repetition with occasional deliberate callbacks — rather than a separate line bank. Range across the gap states, in one persona's voice:

- Closing in: *"Not far behind. Give it a hundred meters."*
- Very close: *"I can see you."*
- Overtaking: *"...There."*
- Falling behind (Cowed): little to nothing, or a grudging *"Fine. Keep going."*
- Closing stretch: *"Last push. This is usually where you slow down."*

## Delivery

- Text-to-speech per persona voice profile, or a recorded voice-line library for personas with a fixed identity.
- Audio ducking under music/podcast for the duration of a line — the same pattern existing running apps already use for pace callouts, not a novel interaction to teach.
- No visual dependency: works phone-locked, in a pocket, over earbuds or open-ear audio.
