# Voice Design — the one GAIT voice

> **Changed in v0.18.0.** Nobody speaks any more. The voice survives only as a spoken
> *instrument readout* — kilometre marks, the gap, what is riding on the round — read aloud for
> when the phone is in a pocket. The character notes below still describe its timbre; the words
> it says are now numbers, never a persona's lines.

Decision (2026-08-21): **one voice for everything spoken** — the Twin's live callouts, stakes, memos read aloud, the Horde's captions if ever voiced. No roster of different voices per persona; persona lives in the *words*, the voice is the division's.

## Character
A young adult woman, roughly 17–20: feminine, soft, clear, slightly higher pitch. Exceptionally constant timbre, very precise and clean tone, controlled consonants, extremely clean vowels, little breath noise. A subtle metallic / digital undertone, near-perfect overtones, less natural roughness than a human speaker, a faint futuristic presence. Not a robot voice, no heavy vocoder, no obvious autotune.

Target feel: **~70 % natural young female voice, ~30 % high-grade synthetic AI voice.** The AI impression comes from the *timbre*, not from unnatural speech. After a few seconds: "that is clearly an AI — and it speaks surprisingly naturally."

## Delivery
Natural human timing; ~150–170 words per minute; short natural pauses; subtle emotional reactions; light emphasis on key words; naturally varying sentence melody; occasionally slightly softer sentence endings; minimal variation in tempo and loudness; pitch movement a little more controlled and precise than a human's; very even articulation.

## AI timbre
Slightly glassy; subtle digital brilliance; very clean harmonic structure; slightly synthetic resonances; controlled formants; almost no random vocal roughness; extremely even tone quality; a discreet upper harmonic layer; minimally "holographic"; clear separation of fundamental and overtones.

## Optional processing chain (to reinforce, not to create, the character)
1. High-pass ≈ 80 Hz
2. Gentle cut 200–350 Hz
3. Subtle presence lift 2.5–4 kHz
4. Air boost ≈ 9–12 kHz
5. Soft de-esser
6. Compression ≈ 2.5:1
7. Very light harmonic saturation
8. Subtle formant work — without making the voice unnaturally high
9. Very gentle pitch quantising for slightly superhuman pitch stability
10. Extremely subtle chorus / micro-doubling for a synthetic shimmer
11. Very short, quiet digital reverb
12. Optional: a very quiet synthetic harmony layer an octave (or a few semitones) away — felt more than heard
13. Limiter

The voice should read as slightly artificial **dry**, before any of this.

## Must not sound like
Phone robot · 1990s speech computer · heavy vocoder · autotune singing · anime child voice · a normal narrator with reverb.

## The horde's counterpart

A horde enrolment does not get this voice. It gets the opposite of it: deep, slow, physical.
Pitch far below natural, rate ~0.78, a male engine voice where one exists; the chain keeps the
bottom (+6 dB at 110 Hz), scoops the presence band (−4 dB at 900 Hz), removes air entirely
(−8 dB shelf above 2.6 kHz), adds a 28 ms detuned double at 42 % — a second throat half a step
behind — and saturates hard. It speaks only what is inside the caption brackets.

## Implementation notes (GAIT)
- Lines are data-grounded templates (docs/twin-personas.md), so they cannot all be pre-rendered. Plan: on-device `TextToSpeech` with a selected female neural system voice, pitch ≈ 1.08, rate ≈ 1.0, synthesised to PCM (`synthesizeToFile` / `AudioTrack`) and run through a light DSP chain (high-pass, presence, air, micro-doubling, short reverb, limiter) before playback — the chain above, reduced to what's cheap on a phone. If the system voice can't carry the timbre, a pre-rendered bank of fixed phrases (numbers, km marks, stock reactions) from a neural TTS provider is the fallback, assembled at runtime.
- Audio focus ducking against the user's music; never more than the text Comms cap (12 lines per session, 45 s cooldown) — see docs/live-audio.md.
