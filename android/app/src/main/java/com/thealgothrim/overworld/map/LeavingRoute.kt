package com.thealgothrim.overworld.map

/**
 * Whether the arrow should let go of the route line and go where GPS says, before Ferrostar has
 * called the car off the route.
 *
 * On a route the arrow is Ferrostar's point on the current step, carried ahead along the line. When
 * the car turns off, that point slid on down the old road (and after a turn the route wanted but the
 * car drove past, stood at the corner) for the 10 to 12 s until the detour was called: on the 26 Nov
 * drive, with GPS good to 4 m, it sat up to 64 m from the car. Google Maps' arrow takes the turn
 * with the car. So once a sharp fix is clearly off the line while moving, the arrow follows the
 * fixes, and comes back to the line when the car does.
 */
internal class LeavingRoute {
  var leaving = false
    private set
  private var farFixes = 0

  /**
   * One fix: [offMetres] from the route near where the arrow is, good to [accuracy] metres, at
   * [speed] m/s. Null [offMetres]: no route, so nothing to leave.
   */
  fun update(offMetres: Double?, accuracy: Double, speed: Double?): Boolean {
    if (offMetres == null) {
      reset()
      return false
    }
    if (leaving) {
      if (offMetres <= BACK_METRES) reset()
      return leaving
    }
    // A vague fix, or a car standing (GPS wanders beside the road at a light): stay on the line.
    if (accuracy > MAX_ACCURACY_METRES || (speed ?: 0.0) < MOVING_MS) {
      farFixes = 0
      return false
    }
    farFixes = if (offMetres >= maxOf(LEAVE_METRES, accuracy * 1.5)) farFixes + 1 else 0
    if (farFixes >= LEAVE_FIXES || offMetres >= maxOf(SURE_METRES, accuracy * 2.5)) leaving = true
    return leaving
  }

  fun reset() {
    leaving = false
    farFixes = 0
  }

  companion object {
    /** The last state written to the drive log; the phone and car maps each keep a tracker. */
    @Volatile var logged = false

    /** Off the line by this much for [LEAVE_FIXES] fixes in a row: a lane or two past the road's edge. */
    const val LEAVE_METRES = 16.0
    const val LEAVE_FIXES = 2
    /** This far off on one sharp fix is no GPS wobble. */
    const val SURE_METRES = 28.0
    /** Back within this of the line: on the route again. */
    const val BACK_METRES = 8.0
    const val MAX_ACCURACY_METRES = 20.0
    /** About 5 km/h. */
    const val MOVING_MS = 1.5
  }
}
