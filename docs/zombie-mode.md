# Zombie Horde — Alternate Opponent Mode

Chosen at setup instead of a Rival Twin. Same underlying engines (Forecast, Fidelity/Composure math, Rest Days), completely different fiction and delivery: no name, no persona voice — a distance closing behind you, and non-verbal sound.

## Lore: where the horde comes from

Every Twin that loses its Decommission Trial doesn't get deleted — GAIT recycles it into a shared, anonymous pool of failed prediction units. No name, no voice left, just distance. This isn't a disconnected gimmick bolted onto the app: it's the in-fiction explanation for why this mode exists at all, and it ties the two opponent types into one world instead of two unrelated features. See [`composure-system.md`](composure-system.md) and [`concept.md`](concept.md) for the surrounding lore.

## Three tiers

| Tier | Represents | Behavior |
|---|---|---|
| **Shamblers** (slow) | Long-decommissioned, low-fidelity failed units | Ambient, ever-present background presence — ties to overall Proximity, not a specific trigger |
| **Stalkers** (medium) | More recently decommissioned units, still holding onto trained pace data | Pace-matched pressure — the day-to-day tension, tracks the gap the same way a Twin's Live Divergence does |
| **Screamers** (fast) | A decommissioned unit's "last burst" | Rare, high-stakes surges — the Breach-event equivalent of a Decommission Trial |

## Non-verbal captions, not words

Per the brief: never comprehensible speech, only sound — represented as bracketed captions (`[distant groan]`, `[snarling, just behind you]`). This is the *primary* representation, not a placeholder for missing audio assets: captions work with zero sound files, are accessible by default (no reliance on hearing), and cost nothing to localize. Live in-session audio triggering (matching the gap-threshold architecture in [`live-audio.md`](live-audio.md)) is a natural v1.1 extension once real audio assets exist, using these same captions as the source text.

### Sound cue catalog (`HordeSoundCues`, built)

| State | Maps to (Composure) | Example captions |
|---|---|---|
| Fallen Back | Cowed | `[groan fading into the distance]`, `[silence, for once]` |
| Tracking | Watchful | `[shuffling, several sets of footsteps]`, `[wet, ragged breathing, steady]` |
| Swarming | Predatory | Intensity-dependent — see below |
| Ambient (pre-session / idle) | — | `[distant groan]`, `[low murmur, many voices]` |

Unlike a Twin, which stays silent while Watchful, the horde always has *something* audible — that constant presence is the point of choosing this mode over a Twin's more occasional, personal confrontation.

## Intensity (chosen at setup)

Only the Swarming bank varies by intensity — Tracking and Fallen Back stay shared, since those are baseline states rather than the "how brutal" dial (mirrors the Composure intensity setting for Twins):

- **Calm**: `[breathing quickens, somewhere behind you]`
- **Standard**: `[snarling, just behind you]`, `[fingers scraping pavement]`
- **Relentless**: `[a scream cuts through the group]`, `[ASSET CONNECTION UNSTABLE -- static]`

That last line is deliberate: even at peak intensity, a "catch" is framed as a corpo system glitch, not violence — consistent with the game's dystopian-bureaucracy tone rather than horror-gore.

## Metric relabeling

Same numbers, different names, so the fiction stays consistent everywhere they're shown (Forecast, Debrief, Statistics):

| Twin term | Horde term |
|---|---|
| Fidelity | Proximity |
| Generation | Wave |
| Composure (Cowed/Watchful/Predatory) | Aggression (Fallen Back/Tracking/Swarming) |
| Persona forecast line | Ambient/gap caption |

## What's shared with the Twin system (not duplicated)

- `TwinProfileEntity` gains one `opponentType` column (`"twin"` | `"horde"`) rather than a parallel table — `fidelity`/`generation` are reinterpreted as Proximity/Wave, `personaKey` holds the intensity key instead of a persona key. See the entity's doc comment.
- `ForecastEngine`, `ComposureEngine`, `SessionFinalizer`, Rest Days/Vacation — all identical code paths, branching only on `opponentType` where the *content* differs (caption vs. persona line), never the underlying math.
- `SessionFinalizer` is the single place that branches: Horde → `HordeSoundCues.captionFor(...)`, Twin → persona line banks. Both still route through the same same-day Predatory/Swarming notification exception.

## Setup flow (built)

New Setup Step 1 — **Opponent Type** (Rival Twin vs. Zombie Horde) — precedes both existing flows. Twin path is unchanged (Naming screen). Horde path is a new Step 2 screen: the lore blurb above, then an intensity picker, then confirm. No name entry — deliberately anonymous.

## Verified

Full flow tested live on device: Opponent Type screen → Horde setup (lore + Relentless intensity) → Forecast showing `Relentless · Wave 1 · Proximity 50%` and a cold-start `[no signal yet]` caption → a logged session producing `Proximity now 50% · Aggression: TRACKING` and a real bracketed caption (`[shuffling, several sets of footsteps]`) in the Debrief. No crashes.

Two latent crash bugs were found and fixed while wiring this up: `IdleTauntWorker` and Simulation mode both called `Personas.byKey()` unconditionally, which would throw for a Horde profile's intensity key. Both now branch on `opponentType` the same way `SessionFinalizer` does.

## Not yet built (v1.1+, same bucket as Live Audio Callouts)

- Live in-session audio triggering of these captions (currently only surfaced in Forecast/Debrief text).
- A real Breach event (Screamer surge as a timed chase, mirroring the Decommission Trial) — currently only the lore and the Swarming caption bank exist; the actual timed-challenge mechanic isn't implemented.
- Cross-user horde "population" — right now Proximity/Wave are purely local to one device, not actually pooled from other users' decommissioned Twins as the lore implies. Doing that for real would need a backend, which [`scope-and-stack.md`](scope-and-stack.md) deliberately avoids for v1.
