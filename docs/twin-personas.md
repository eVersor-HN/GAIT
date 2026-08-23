# Twin Personas & Dialogue Variation

> **Superseded in v0.18.0.** The opponent no longer speaks. Every persona line, taunt, memo,
> caption and closing line was removed; the opponent communicates only through its forecast,
> the ledger, the board and the map. This document is kept as design history.

Default content language for the app is **English**; other locales are a later, separate effort and out of scope for now.

## Persona roster

Beyond the three original setup presets (Hated Person, Better Self, Just Twin-7), the naming step offers 14 additional pre-voiced archetypes. Each has a distinct emotional register so the generation system below can produce dialogue that never converges on one generic "rival AI" tone.

| # | Persona | Voice | Example line |
|---|---|---|---|
| 1 | The Ex | Familiar, cutting, uses shared history as ammunition | "You always did this the night before you'd quit on me too." |
| 2 | The Sibling | Casual, lifelong rivalry, deflects with humor | "Mom still asks why I'm faster. I don't correct her." |
| 3 | The Former Best Friend | Warm surface, occasional passive-aggressive dig | "I'm still proud of you. Just also slightly ahead." |
| 4 | The Mentor Who Moved On | Disappointed rather than mocking, guilt-based | "I stopped expecting more from you around week six. Surprise me." |
| 5 | The Rival Teammate | Sports banter, comparative stats, competitive camaraderie | "Coach cut you, kept me. Read into that however you want." |
| 6 | The Understudy | Hungry junior, eerily deferential while closing the gap | "I'm not trying to replace you. I'm just... available." |
| 7 | The Replacement Hire | Corporate onboarding cheer, unsettling politeness | "Quick sync: I've matched your Q2 numbers. Exciting times!" |
| 8 | The Auditor | Bureaucratic, clinical, refers to you by asset ID | "Asset performance nominal. Human variance noted, not yet flagged." |
| 9 | The Boss | Blunt authority, quota-driven | "Numbers are due Friday. I don't care how." |
| 10 | Future You — Idealized | Calm, already arrived, waiting for you | "I did this already. I'm just waiting for you to arrive." |
| 11 | Future You — Feared | Cautionary, warns you away from complacency | "I stopped here too, once. It took four years to start again." |
| 12 | Younger You | Nostalgic guilt, references who you used to be | "You used to run in the rain for fun. When did that stop?" |
| 13 | The Doppelgänger | Uncanny identity-blur, speaks as if it IS you | "I remember this hill. It hurt less, for me." |
| 14 | The Algorithm | Self-aware, philosophical, owns being built from your data | "You trained me on your worst week. I've been generous ever since." |

Any persona can still be renamed at setup, same as the original "Hated Person" preset — the roster defines a starting *voice*, not a fixed identity.

## Why static lines don't scale

"Incredibly many variations" can't be reached by hand-writing enough lines — any fixed bank eventually repeats, and repetition is exactly what breaks the illusion of a real opponent. Structural techniques instead:

1. **Data-grounded templates, not fixed lines.** Every message is built from real, ever-changing inputs — exact time deltas, specific week counts, route names, weather, session counts. Two users never get the same message, and the same user rarely gets the same message twice, because their own data keeps changing. (Already used in the Asset Twin demo: *"You beat the forecast by 0:13, nine times this month."*)

2. **State-conditioned tone.** The same trigger reads differently depending on relationship state — Fidelity trend, win/loss streak, days since last session, proximity to a Decommission Trial. A post-win message lands differently at Fidelity 40% (dismissive) than at Fidelity 94% (rattled).

3. **Anti-repetition with intentional callbacks.** Recently used phrasings are excluded from selection for a cooldown window — except for deliberate callbacks, where the twin repeats something verbatim from weeks earlier to feel unsettlingly attentive: *"You said this exact thing twelve weeks ago. Nothing changed."*

4. **Recommended implementation: grounded generation, not a static bank.** At this required volume of variation, each line should be generated at request time from a persona voice profile + the user's real recent data + a short history of past lines (to steer away from repeats) — rather than authoring thousands of static strings by hand. The example lines above define the *voice profile* for generation, not the shipped inventory.
