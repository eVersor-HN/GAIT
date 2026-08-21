package dev.eversorhn.gait.domain.activity

/**
 * The activity a profile belongs to (docs/activities-and-dimensions.md). Every activity gets
 * its own opponent profile, Fidelity, generation and session history; switching activity in
 * Settings switches to that profile (and into setup if it doesn't exist yet).
 * v1 scoring is pace-based for all of them; the dimension labels are the honest caveat for
 * motor-assisted movement, where pace says little and consistency/novelty should count.
 */
data class Activity(
    val key: String,
    val label: String,
    val verb: String,
    val dimensions: List<String>,
    val paceMeaningful: Boolean,
    /** Wheeled / motor-assisted: read in km/h rather than min/km. */
    val usesSpeed: Boolean = false,
    /** What "indoor" means for this activity, for the mode card. */
    val indoorLabel: String = "treadmill",
)

object Activities {
    val RUNNING = Activity("RUNNING", "Running", "run", listOf("Pace", "Consistency", "Route novelty"), true, indoorLabel = "treadmill")
    val WALKING = Activity("WALKING", "Walking", "walk", listOf("Pace", "Consistency", "Distance"), true, indoorLabel = "treadmill")
    val CYCLING = Activity("CYCLING", "Cycling", "ride", listOf("Speed", "Consistency", "Elevation"), true, usesSpeed = true, indoorLabel = "indoor trainer")
    val HIKING = Activity("HIKING", "Hiking", "hike", listOf("Distance", "Elevation", "Consistency"), true, indoorLabel = "treadmill / stairmill")
    val E_SCOOTER = Activity("E_SCOOTER", "E-Scooter", "ride", listOf("Consistency", "Route novelty", "Reliability"), false, usesSpeed = true, indoorLabel = "timed only")
    val E_BIKE = Activity("E_BIKE", "E-Bike", "ride", listOf("Consistency", "Route novelty", "Distance"), false, usesSpeed = true, indoorLabel = "indoor trainer")
    val HAND_CYCLE = Activity("HAND_CYCLE", "Hand-cycle", "ride", listOf("Speed", "Consistency", "Route novelty"), true, usesSpeed = true, indoorLabel = "indoor trainer")
    val WHEELCHAIR = Activity("WHEELCHAIR", "Wheelchair", "roll", listOf("Pace", "Consistency", "Distance"), true, indoorLabel = "roller / treadmill")

    val all = listOf(RUNNING, WALKING, CYCLING, HIKING, E_SCOOTER, E_BIKE, HAND_CYCLE, WHEELCHAIR)

    fun byKey(key: String?): Activity = all.firstOrNull { it.key == key } ?: RUNNING

    /** "5:32/km" for pace activities, "10.8 km/h" for wheeled ones. One formatter for every screen. */
    fun formatPaceOrSpeed(secPerKm: Double, key: String?): String {
        val a = byKey(key)
        if (a.usesSpeed) return "%.1fkm/h".format(3600.0 / secPerKm.coerceAtLeast(1.0))
        val t = secPerKm.toInt()
        return "${t / 60}:${(t % 60).toString().padStart(2, '0')}/km"
    }

    fun paceWord(key: String?): String = if (byKey(key).usesSpeed) "Speed" else "Pace"
}
