package dev.eversorhn.gait.domain.activity

/**
 * The activity a profile belongs to (docs/activities-and-dimensions.md). Every activity gets
 * its own opponent profile, Fidelity, generation and session history; switching activity in
 * Settings switches to that profile (and into setup if it doesn't exist yet).
 * v1 scoring is pace-based for all of them; the dimension labels are the honest caveat for
 * motor-assisted movement, where pace says little and consistency/novelty should count.
 */
data class Activity(val key: String, val label: String, val verb: String, val dimensions: List<String>, val paceMeaningful: Boolean)

object Activities {
    val RUNNING = Activity("RUNNING", "Running", "run", listOf("Pace", "Consistency", "Route novelty"), true)
    val WALKING = Activity("WALKING", "Walking", "walk", listOf("Pace", "Consistency", "Distance"), true)
    val CYCLING = Activity("CYCLING", "Cycling", "ride", listOf("Pace", "Consistency", "Elevation"), true)
    val HIKING = Activity("HIKING", "Hiking", "hike", listOf("Distance", "Elevation", "Consistency"), true)
    val E_SCOOTER = Activity("E_SCOOTER", "E-Scooter", "ride", listOf("Consistency", "Route novelty", "Reliability"), false)
    val E_BIKE = Activity("E_BIKE", "E-Bike", "ride", listOf("Consistency", "Route novelty", "Distance"), false)
    val HAND_CYCLE = Activity("HAND_CYCLE", "Hand-cycle", "ride", listOf("Pace", "Consistency", "Route novelty"), true)
    val WHEELCHAIR = Activity("WHEELCHAIR", "Wheelchair", "roll", listOf("Pace", "Consistency", "Distance"), true)

    val all = listOf(RUNNING, WALKING, CYCLING, HIKING, E_SCOOTER, E_BIKE, HAND_CYCLE, WHEELCHAIR)

    fun byKey(key: String?): Activity = all.firstOrNull { it.key == key } ?: RUNNING
}
