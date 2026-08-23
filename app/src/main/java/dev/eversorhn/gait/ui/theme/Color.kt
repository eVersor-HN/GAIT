package dev.eversorhn.gait.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Monochrome first. A corporate console is black, white and grey; colour is a signal, not a
 * decoration. Exactly three accents exist and each means one thing:
 *   Signal (amber)  — you, and the one primary action per screen
 *   Alert  (red)    — danger: below the cull line, behind the model, the horde closing
 *   Good   (pale)   — a confirmed win; deliberately quiet, never celebratory
 * The opponent is not a colour — it is a lighter grey than the background, so the eye reads
 * you against it rather than "two teams".
 */

// --- ground ---
val Ink = Color(0xFF07080A)
val Ink2 = Color(0xFF0E1013)
val Ink3 = Color(0xFF14171B)
val Line = Color(0xFF2B2F35)
val LineSoft = Color(0xFF1B1F24)

// --- type ---
val TextPrimary = Color(0xFFF1F2F3)
val TextDim = Color(0xFF9AA0A6)
val TextFaint = Color(0xFF5C6167)

// --- the three signals ---
/** You, and the single primary action. */
val Brass = Color(0xFFD8A62A)
val BrassDim = Color(0xFF7A5F1C)
/** Danger only. */
val Alert = Color(0xFFD7443A)
/** A win, stated quietly. */
val Good = Color(0xFF8FB58A)

/** The opponent: greyscale, one step brighter than the panel — never a second brand colour. */
val Cyan = Color(0xFFB9C0C7)
