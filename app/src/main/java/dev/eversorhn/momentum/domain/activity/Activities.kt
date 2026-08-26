package dev.eversorhn.momentum.domain.activity

/**
 * The activity a profile belongs to (docs/activities-and-dimensions.md). Every activity gets
 * its own opponent profile, Fidelity, generation and session history.
 *
 * Adaptive activities sit in the group they belong to by movement, not in a category of their
 * own — a racing chair is wheels, a guided run is on foot, adaptive rowing is water.
 */
data class Activity(
    val key: String,
    val label: String,
    val verb: String,
    val group: String,
    val dimensions: List<String>,
    val paceMeaningful: Boolean,
    /** Wheeled / motor-assisted: read in km/h rather than as a pace. */
    val usesSpeed: Boolean = false,
    /** Pace is read per this many metres: 1000 = /km, 500 = rowing split, 100 = swimming. */
    val paceUnitMeters: Int = 1000,
    /** The machine you read the distance off indoors. Blank when there is none. */
    val indoorLabel: String = "treadmill",
    /** Gym machines have no outdoor form — the mode picker only offers the timed one. */
    val outdoorCapable: Boolean = true,
)

object Activities {

    private const val FOOT = "On foot"
    private const val WHEELS = "Wheels"
    private const val WATER = "Water"
    private const val SNOW = "Snow"
    private const val MACHINE = "Indoor machines"

    // --- On foot -----------------------------------------------------------------------------
    val RUNNING = Activity("RUNNING", "Running", "run", FOOT, listOf("Pace", "Consistency", "Route novelty"), true, indoorLabel = "treadmill")
    val GUIDE_RUN = Activity("GUIDE_RUN", "Guided run", "run", FOOT, listOf("Pace", "Consistency", "Distance"), true, indoorLabel = "treadmill")
    val WALKING = Activity("WALKING", "Walking", "walk", FOOT, listOf("Pace", "Consistency", "Distance"), true, indoorLabel = "treadmill")
    val NORDIC_WALKING = Activity("NORDIC_WALKING", "Nordic walking", "walk", FOOT, listOf("Pace", "Consistency", "Distance"), true, indoorLabel = "treadmill")
    val HIKING = Activity("HIKING", "Hiking", "hike", FOOT, listOf("Distance", "Elevation", "Consistency"), true, indoorLabel = "treadmill / stairmill")

    // --- Wheels ------------------------------------------------------------------------------
    val CYCLING = Activity("CYCLING", "Cycling", "ride", WHEELS, listOf("Speed", "Consistency", "Elevation"), true, usesSpeed = true, indoorLabel = "indoor trainer")
    val HAND_CYCLE = Activity("HAND_CYCLE", "Hand-cycle", "ride", WHEELS, listOf("Speed", "Consistency", "Route novelty"), true, usesSpeed = true, indoorLabel = "indoor trainer")
    val WHEELCHAIR = Activity("WHEELCHAIR", "Wheelchair", "roll", WHEELS, listOf("Pace", "Consistency", "Distance"), true, indoorLabel = "roller / treadmill")
    val RACING_CHAIR = Activity("RACING_CHAIR", "Racing chair", "roll", WHEELS, listOf("Speed", "Consistency", "Distance"), true, usesSpeed = true, indoorLabel = "roller")
    val INLINE = Activity("INLINE", "Inline skating", "skate", WHEELS, listOf("Speed", "Consistency", "Route novelty"), true, usesSpeed = true, indoorLabel = "")
    val SKATEBOARD = Activity("SKATEBOARD", "Skateboard", "ride", WHEELS, listOf("Speed", "Consistency", "Route novelty"), true, usesSpeed = true, indoorLabel = "")
    val KICK_SCOOTER = Activity("KICK_SCOOTER", "Kick scooter", "ride", WHEELS, listOf("Speed", "Consistency", "Distance"), true, usesSpeed = true, indoorLabel = "")
    val E_BIKE = Activity("E_BIKE", "E-Bike", "ride", WHEELS, listOf("Consistency", "Route novelty", "Distance"), false, usesSpeed = true, indoorLabel = "indoor trainer")
    val E_SCOOTER = Activity("E_SCOOTER", "E-Scooter", "ride", WHEELS, listOf("Consistency", "Route novelty", "Reliability"), false, usesSpeed = true, indoorLabel = "")

    // --- Water -------------------------------------------------------------------------------
    val ROWING = Activity("ROWING", "Rowing", "row", WATER, listOf("Split", "Consistency", "Distance"), true, paceUnitMeters = 500, indoorLabel = "rowing ergometer")
    val ADAPTIVE_ROWING = Activity("ADAPTIVE_ROWING", "Adaptive rowing", "row", WATER, listOf("Split", "Consistency", "Distance"), true, paceUnitMeters = 500, indoorLabel = "rowing ergometer")
    val KAYAK = Activity("KAYAK", "Kayak / canoe", "paddle", WATER, listOf("Speed", "Consistency", "Route novelty"), true, usesSpeed = true, indoorLabel = "paddle ergometer")
    val PARA_CANOE = Activity("PARA_CANOE", "Para-canoe", "paddle", WATER, listOf("Speed", "Consistency", "Distance"), true, usesSpeed = true, indoorLabel = "paddle ergometer")
    val SWIMMING = Activity("SWIMMING", "Swimming", "swim", WATER, listOf("Pace", "Consistency", "Distance"), true, paceUnitMeters = 100, indoorLabel = "pool")

    // --- Snow --------------------------------------------------------------------------------
    val XC_SKI = Activity("XC_SKI", "Cross-country skiing", "ski", SNOW, listOf("Pace", "Consistency", "Elevation"), true, indoorLabel = "roller ski / treadmill")
    val SIT_SKI = Activity("SIT_SKI", "Sit-ski", "ski", SNOW, listOf("Pace", "Consistency", "Distance"), true, indoorLabel = "roller ski")

    // --- Indoor machines: no outdoor form ------------------------------------------------------
    val ROWING_ERG = Activity("ROWING_ERG", "Rowing ergometer", "row", MACHINE, listOf("Split", "Consistency", "Distance"), true, paceUnitMeters = 500, indoorLabel = "ergometer", outdoorCapable = false)
    val BIKE_ERG = Activity("BIKE_ERG", "Bike ergometer", "ride", MACHINE, listOf("Speed", "Consistency", "Distance"), true, usesSpeed = true, indoorLabel = "ergometer", outdoorCapable = false)
    val ELLIPTICAL = Activity("ELLIPTICAL", "Elliptical", "train", MACHINE, listOf("Pace", "Consistency", "Distance"), true, indoorLabel = "cross-trainer", outdoorCapable = false)
    val STAIR = Activity("STAIR", "Stair climber", "climb", MACHINE, listOf("Pace", "Consistency", "Distance"), true, indoorLabel = "stair machine", outdoorCapable = false)

    val all = listOf(
        RUNNING, GUIDE_RUN, WALKING, NORDIC_WALKING, HIKING,
        CYCLING, HAND_CYCLE, WHEELCHAIR, RACING_CHAIR, INLINE, SKATEBOARD, KICK_SCOOTER, E_BIKE, E_SCOOTER,
        ROWING, ADAPTIVE_ROWING, KAYAK, PARA_CANOE, SWIMMING,
        XC_SKI, SIT_SKI,
        ROWING_ERG, BIKE_ERG, ELLIPTICAL, STAIR,
    )

    /** The picker's order: groups in the order they first appear in [all]. */
    val groups: List<Pair<String, List<Activity>>> =
        all.groupBy { it.group }.toList().sortedBy { (name, _) -> all.indexOfFirst { it.group == name } }

    fun byKey(key: String?): Activity = all.firstOrNull { it.key == key } ?: RUNNING

    /** "5:32/km", "1:48/100m", "2:04/500m" for pace activities, "10.8 km/h" for wheeled ones. */
    fun formatPaceOrSpeed(secPerKm: Double, key: String?): String {
        val a = byKey(key)
        if (a.usesSpeed) return "%.1fkm/h".format(3600.0 / secPerKm.coerceAtLeast(1.0))
        val perUnit = (secPerKm * a.paceUnitMeters / 1000.0).toInt()
        val unit = if (a.paceUnitMeters == 1000) "km" else "${a.paceUnitMeters}m"
        return "${perUnit / 60}:${(perUnit % 60).toString().padStart(2, '0')}/$unit"
    }

    fun paceWord(key: String?): String = when {
        byKey(key).usesSpeed -> "Speed"
        byKey(key).paceUnitMeters == 500 -> "Split"
        else -> "Pace"
    }
}
