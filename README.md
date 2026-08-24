# GAIT

Local movement tracking. One opponent. No strangers, no leaderboards, no cloud.

GAIT records your runs, rides, walks and rolls and puts a single adversary against them: a model of you, built from your own history, that predicts what you will do today and gets sharper every time it is right. You are an asset. It is your proposed replacement. A fictional corporate division keeps score between you, and every session is a round you either take or lose.

For people who train alone and are done with generic missions, badges and leaderboards full of strangers. There is no scheduling, no waiting on anyone else, no social feed — only a standing you hold or lose, and an opponent that reads your data back to you.

---

## Status & license

Open-source software, licensed under the MIT License. Full terms in [LICENSE](LICENSE).

Android. In active development toward v1.0. Runs on a physical device today.

---

## Support the project

- Ko-fi: https://ko-fi.com/eversorhn
- PayPal: https://paypal.me/FAMarco
- Bitcoin: `bc1qv92c3eyeqvhgfnez7spfd7v2aytkhpshsl65yv`

> You have spent this whole page being told you are replaceable.
> The one person who never tried to replace you is the one who built this.

---

## Local first, private by design

Everything GAIT knows about you stays on your phone.

- No account. No sign-up. No profile on anyone's server.
- No telemetry, no analytics, no crash reporting.
- No cloud sync, no backend, no upload of routes, sessions or standings.
- The division, its roster and its rankings are simulated on your device. There are no other players and no network behind them.
- Erase all data from Settings removes it permanently. Uninstalling removes it with the app.

The app can read exercise history from Health Connect if — and only if — you grant it. That read is one-way: GAIT never writes back.

---

## Official source

Author / copyright: **© 2026 eVersor-HN**.
This is the **official** distribution repository — get GAIT only from here:
**https://github.com/eVersor-HN/GAIT**

Binaries published anywhere else are not ours.

---

## Download & install

1. Download the APK from the [Releases](https://github.com/eVersor-HN/GAIT/releases) page.
2. Verify the SHA-256 against the hash listed in that release.
3. Install it on your phone (allow installation from your browser or file manager when prompted).
4. Open the app, enrol, and pick your activity and opponent. Nothing else needs configuring.

`gait-…-release.apk` is the app. `gait-wear-…apk` is the optional watch app — install it on the watch, not the phone. `gait-simdemo-…apk` is a small standalone preview with no real data, no permissions and no database: install it to look, not to train.

---

## Verify authenticity — SHA-256

Every published asset carries its SHA-256 hash in the release that contains it. Filename and hash must match exactly.

Windows:

```
certutil -hashfile gait-vX.Y.Z-release.apk SHA256
```

Linux / macOS:

```
sha256sum gait-vX.Y.Z-release.apk
```

If a hash does not match, delete the file.

---

## Features

**CONTROL**
- Runs entirely on your phone. No account, no cloud, no other people.
- Multiple enrolments side by side — each with its own activity, opponent, standing and settings.
- 25 activities on foot, on wheels, on water, on snow and on gym machines, adaptive ones included.
- Two opponents: a named rival model of you, or a horde that closes on you from behind.
- Sound, spoken readout and vibration are three separate switches. Intensity is yours to set.

**WORKFLOW**
- Open the app, land on your standing, start from the button at the top.
- Outdoor sessions are GPS-verified; indoor sessions are timed with a self-reported distance and stay marked as such.
- Before the session: what your opponent predicts you will do today. During: where you stand, right now, and the pace that still wins the round. After: forecast against reality, and what it cost or gained you.
- An analysis page that answers one question — what do I do next, and by how much.
- Import your recent exercise history from Health Connect so the first forecasts have something to stand on.

**PRESSURE**
- A division of roughly a thousand simulated assets, each with a working life of its own — day shifts, nights, rotating crews, part-time, studies, retirement — ranked against you every day.
- Your opponent trains on the days you skip, and shares those sessions live the way a training partner would.
- Your opponent stakes points on its own prediction. You can double the round.
- Nothing talks at you. The opponent is its forecast, the score and the distance — no scripted lines, no taunts, no messages.
- The live figures read aloud at kilometre marks and lead changes, and felt through the pocket: a tick every kilometre, a knock at every lead change, a pulse that tightens as the horde closes.
- Every quarter the bottom of the division is cut. If that is you, the enrolment ends and you start again.

**ON EVERY SURFACE**
- A lock-screen card with the gap, the pace that still wins the round and your projected finish — no unlocking.
- A home-screen widget with your rank, the standing and the days to the next cull.
- A quick-settings tile that starts a session from anywhere.
- A watch app showing the gap while you run and your rank when you are not.
- Heart rate from any standard Bluetooth monitor, and sessions written back to Health Connect if you want them there.
- The instruments the phone already has: the barometer for climb that resolves a single flight of stairs, and the step counter for cadence — which still works on a treadmill.

**PRIVACY**
- Your routes, times and standings never leave the device.
- Notifications can be silenced at the source without losing the messages themselves.

---

## Build from source

Requirements:

- Windows, Linux or macOS
- JDK 17
- Android SDK, platform 35

Commands:

```
./gradlew :app:assembleDebug        # installable debug build
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:assembleRelease      # shrunk release build
./gradlew :wear:assembleRelease     # the watch app
```

Output lands in `app/build/outputs/apk/`. Debug builds install alongside a release build. Release signing is optional and read from an untracked `keystore.properties`; `keystore.properties.example` shows the format. Without it, the release build is unsigned.

---

## Security & privacy notes

- **Location.** Outdoor tracking requires foreground location access and runs as a visible foreground service. Background location is never requested. Indoor tracking asks for no location permission at all.
- **Notifications.** Optional. Denying them costs you the shade messages, nothing else.
- **Health Connect.** Optional, read-only, and only for the activity types you are training. Nothing is written back.
- **Network.** The app requests no internet permission and makes no network calls.
- **Storage.** Sessions, routes, messages and standings live in a local database on the device. GAIT does not encrypt that database beyond the platform's own device encryption; anyone with unlocked access to the phone can reach app data through normal means.
- **Not a medical device.** GAIT gives no medical, diagnostic or training advice; its targets are arithmetic on your own past sessions. Distances, paces, elevations and heart rate are approximate. The app states this on first launch and will not start until you have read it.
- **Bluetooth.** Used only to find and read a heart-rate monitor, and only while you are pairing or recording. Nothing is scanned for location.
- **Tone.** The opponent is designed to be blunt and, at its harshest, openly demeaning. Intensity is adjustable, including off.

---

## System requirements

- Android 8.0 (API 26) or newer
- GPS for outdoor tracking (indoor works without it)
- Roughly 50 MB of storage, growing slowly with session history
- Text-to-speech engine on the device for spoken callouts (optional)
- Health Connect installed, to import or write back sessions (optional)
- A Bluetooth heart-rate monitor, for heart rate (optional)
- A barometer and a step counter, for climb and cadence (optional; most phones have both)
- Wear OS 3 or newer, for the watch app (optional)

---

## License

MIT — see [LICENSE](LICENSE).

---

## Third-party notices

GAIT is built on the Android platform and open-source components published by Google and JetBrains under the Apache License 2.0, and uses the Google Play services location APIs under their own terms. Those licenses continue to apply to those components.
