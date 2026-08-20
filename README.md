# GAIT

**Status:** Frühes Konzept — noch kein Code, nur Produktdesign und interaktive Mockups.

GAIT ist die Konzeptidee für eine GPS-Bewegungs-App (Laufen, Gehen, Radfahren, E-Scooter, ...), die bewusst **keine** generischen Missionen, Achievements oder Leaderboards gegen fremde Menschen nutzt. Stattdessen ist alles im Kern Cyberpunk-Corpo-thematisiert: Deine Bewegungsdaten sind eine Ware, um die fiktive Konzerne konkurrieren.

## Kernmechanik: Asset Twin

Die aktuelle Fokus-Mechanik von GAIT. Eine KI baut aus deiner eigenen Trainingshistorie einen digitalen Zwilling, der versucht, dich vorherzusagen — ein Konzern-Experiment, das prüft, ob man dich durch eine Simulation ersetzen kann. Kein Wettkampf gegen fremde Menschen (keine Termine, keine Absagen anderer Leute), sondern ausschließlich gegen eine Version von dir selbst.

Den Zwilling benennst du beim Setup selbst — z. B. nach jemandem, den du nie wieder gewinnen sehen willst. Der Name taucht danach in jeder Prognose, jedem Live-Vergleich und jeder Nachricht auf.

Der Loop hat sechs Phasen:

| Phase | Beschreibung |
|---|---|
| 0 · Setup | Du gibst dem Zwilling einen Namen. Presets: „Gehasste Person", „Besseres Ich", „Nur Twin-7". |
| 1 · Forecast | Vor der Aktivität sagt der Zwilling exakt voraus, was du heute tun wirst (Pace, Route, Zielzeit) — basierend auf echten Mustern. |
| 2 · Live-Divergenz | Während der Aktivität schwächt alles Unvorhersehbare (neue Route, negativer Split, Training bei Regen) live die Fidelity des Zwillings — nicht reines Schnellerlaufen. |
| 3 · Fidelity-Bilanz | Nach der Session: Forecast vs. Actual, plus Trend der Gesamt-Fidelity über Wochen. Die Fidelity steigt langfristig fast immer leicht weiter. |
| 4 · Decommission Trial | Erreicht die Fidelity einen Schwellenwert: ein einzelnes Duell live gegen die stärkste bisherige Session des Zwillings. Bewusst selten. |
| 5 · Generationswechsel | Der Zwilling meldet sich in persönlichem Ton, zitiert deine eigenen Daten. Bei einem Sieg wird eine neue, präzisere Generation initialisiert. |

Die zentrale Spannung: Du willst explizit **nicht**, dass dein Zwilling besser wird oder gewinnt.

## Twin-Personas & Dialog-Variation

17 wählbare Ausgangs-Personas für den Zwilling (3 Basis-Presets + 14 vorgesprochene Archetypen wie „The Auditor", „The Doppelgänger", „Future You"), jede mit eigener Stimme. Alles, was der Zwilling sagt, muss sich wie eine echte, nie repetitive Person/KI anfühlen — dafür ein daten-basiertes Template- statt Festtext-System mit Zustands-abhängigem Ton und bewussten Callbacks. Details: [`docs/twin-personas.md`](docs/twin-personas.md). Standard-Content-Sprache der App ist Englisch.

## Sportarten & Wettbewerbsdimensionen

GPS liefert von Natur aus Pace/Route/Distanz — passt gut zu Ausdauersportarten, aber nicht zu motorunterstützter Fortbewegung (E-Scooter, E-Bike), wo nicht Tempo, sondern Konsistenz, Routen-Neuheit und Zuverlässigkeit die faire Wettbewerbsdimension sind. Jede Aktivität bekommt ein eigenständiges Twin-Profil (eigene Fidelity, eigene Generation, eigene Persona). Details: [`docs/activities-and-dimensions.md`](docs/activities-and-dimensions.md).

## Interaktive Demo

`demo/asset-twin-demo.html` — Handy-Mockups aller sechs Phasen, cyberpunk-corpo gestaltet. Einfach lokal im Browser öffnen (keine Abhängigkeiten, keine externen Requests außer Google Fonts).

## Weitere, aktuell zurückgestellte Modul-Ideen

Aus der ursprünglichen Ideenrunde, dokumentiert in [`docs/concept.md`](docs/concept.md), aber nicht mehr im Fokus:

- **Datenbroker-Kreislauf** — Sessions werden live an rivalisierende Konzerne versteigert.
- **Overwatch Zones** — dynamisch generierte Überwachungszonen auf der Route, meiden oder durchqueren.
- **Cyberware-Profil** — Fortschritt als Implantat-HUD statt XP-Balken.
- **Feindliche Übernahmeangebote** — rivalisierende Konzerne werben personalisiert um dich ab.

## Projektstruktur

```
README.md                      — dieses Dokument
CHANGELOG.md                    — Verlauf der Konzeptentscheidungen
LICENSE                         — MIT
docs/concept.md                  — vollständige Ideensammlung (alle 5 ursprünglichen Mechaniken)
docs/twin-personas.md             — 17 Twin-Personas + Dialog-Variationssystem
docs/activities-and-dimensions.md — Sportarten, Wettbewerbsdimensionen, Profil-Architektur
demo/asset-twin-demo.html         — interaktive HTML-Mockups des Asset-Twin-Loops
```

## Lizenz

MIT — siehe [LICENSE](LICENSE).
