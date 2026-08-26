package dev.eversorhn.momentum.domain.horde

/**
 * How hard a horde enrolment presses. A difficulty setting, nothing more — the horde has no
 * voice and says nothing; the intensity only scales how fast it closes on you.
 */
object HordeIntensity {
    const val CALM = "calm"
    const val STANDARD = "standard"
    const val RELENTLESS = "relentless"

    val all = listOf(CALM, STANDARD, RELENTLESS)

    fun label(key: String?): String = when (key) {
        CALM -> "Calm"
        RELENTLESS -> "Relentless"
        else -> "Standard"
    }
}
