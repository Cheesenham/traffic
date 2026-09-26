package com.trafficgauge.app.routing

/**
 * "교차로 정보" — one signalized intersection's static metadata, from the nationwide
 * (행정안전부/한국지역정보개발원) traffic signal open dataset.
 */
data class IntersectionInfo(
    val intersectionId: String,
    val name: String?,
    val location: LatLon,
    val orgCode: String?,
    val orgName: String?,
    val speedLimitKmh: Int?,
)

/** The 8 approach directions used by the national signal controller spec (경찰청 규격). */
enum class ApproachDirection(val fieldPrefix: String, val bearingDegrees: Double) {
    NORTH("nt", 0.0),
    NORTHEAST("ne", 45.0),
    EAST("et", 90.0),
    SOUTHEAST("se", 135.0),
    SOUTH("st", 180.0),
    SOUTHWEST("sw", 225.0),
    WEST("wt", 270.0),
    NORTHWEST("nw", 315.0),
    ;

    companion object {
        /** Nearest of the 8 compass directions to the given bearing (0..360, 0 = north). */
        fun fromBearing(bearingDegrees: Double): ApproachDirection {
            val normalized = ((bearingDegrees % 360) + 360) % 360
            return entries.minBy { direction ->
                val diff = kotlin.math.abs(normalized - direction.bearingDegrees)
                minOf(diff, 360 - diff)
            }
        }
    }
}

enum class SignalMovement(val fieldInfix: String) {
    STRAIGHT("St"),
    LEFT_TURN("Lt"),
    PEDESTRIAN("Pd"),
    BUS("Bs"),
    BICYCLE("Bc"),
    U_TURN("Ut"),
}

/**
 * One direction+movement's current reading: e.g. "북쪽 직진신호는 지금 녹색, 87초 남음".
 *
 * [stateName] holds a SAE J2735 MovementPhaseState value (confirmed against a real KLID
 * response: "protected-Movement-Allowed", "permissive-Movement-Allowed", "stop-And-Remain",
 * "stop-Then-Proceed", "dark", or "" when this movement has no active signal head) — not
 * Korean 녹색/적색 text as the Seoul dataset's description page implied.
 */
data class SignalSnapshot(
    val stateName: String?,
    val remainingSeconds: Double?,
) {
    val isGreen: Boolean get() = stateName == "protected-Movement-Allowed" || stateName == "permissive-Movement-Allowed"
}
