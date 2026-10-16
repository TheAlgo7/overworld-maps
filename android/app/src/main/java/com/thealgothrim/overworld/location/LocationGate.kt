package com.thealgothrim.overworld.location

import android.location.Location
import android.os.SystemClock
import com.stadiamaps.ferrostar.core.location.NavigationLocationProviding
import com.thealgothrim.overworld.DriveLog
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Between the phone's GPS and everything that uses it, the trip included, so a single bad fix can't
 * move the car. Standing at a light, multipath puts a fix on the crossing road now and then; that
 * one fix, 28 m away, used to carry a trip past its turn and leave it there (Android navigation
 * study, test T5), because the app's own filters only ever saw free driving and Ferrostar took GPS
 * straight. Google Maps' arrow didn't move.
 *
 * A fix the gate doesn't believe is never passed on with its own speed and heading either: the car
 * is shown where the last believed fix says it is now, with that fix's speed and heading. Only real
 * GPS goes through here; a test drive's simulator is already exact.
 */
class LocationGate(private val inner: NavigationLocationProviding) : NavigationLocationProviding {
  @Volatile private var lastShown: Location? = null

  /** The last place the gate showed, while it's fresh: a trip starting at a light starts there too. */
  override suspend fun lastLocation(): Location? {
    val shown = lastShown
    if (shown != null && SystemClock.elapsedRealtimeNanos() - shown.elapsedRealtimeNanos < FRESH_NANOS) return shown
    return inner.lastLocation()
  }

  // Each collector (the trip, the free-drive map) gets its own rules, fed the same fixes.
  override fun locationUpdates(intervalMillis: Long): Flow<Location> = flow {
    val rules = GateRules()
    var lastNote = 0L
    inner.locationUpdates(intervalMillis).collect { raw ->
      val verdict = rules.next(raw.toGateFix())
      val out =
          when (verdict) {
            is GateVerdict.Pass -> raw
            is GateVerdict.Replace -> raw.replacedBy(verdict)
          }
      if (verdict.note != null && raw.elapsedRealtimeNanos - lastNote > NOTE_NANOS) {
        lastNote = raw.elapsedRealtimeNanos
        DriveLog.note("gps: ${verdict.note}")
      }
      lastShown = out
      emit(out)
    }
  }

  private fun Location.toGateFix() =
      GateFix(
          lat = latitude,
          lng = longitude,
          // Elapsed time where the fix has it (it doesn't jump with the clock), else wall time.
          atMs = if (elapsedRealtimeNanos > 0) elapsedRealtimeNanos / 1_000_000 else time,
          speed = if (hasSpeed()) speed else null,
          bearing = if (hasBearing()) bearing else null,
          accuracy = if (hasAccuracy()) accuracy else null,
      )

  /** [this] fix's time and accuracy at the place, speed and heading the gate believes instead. */
  private fun Location.replacedBy(r: GateVerdict.Replace): Location {
    val raw = this
    return Location(raw.provider).apply {
      latitude = r.lat
      longitude = r.lng
      time = raw.time
      elapsedRealtimeNanos = raw.elapsedRealtimeNanos
      if (raw.hasAccuracy()) accuracy = raw.accuracy
      if (raw.hasAltitude()) altitude = raw.altitude
      speed = r.speed
      r.bearing?.let { bearing = it }
    }
  }

  private companion object {
    const val FRESH_NANOS = 30_000_000_000L
    /** At most one drive-log line every 10 s from here. */
    const val NOTE_NANOS = 10_000_000_000L
  }
}

/** A GPS fix as the gate judges it, in plain values so the rules run (and are tested) off the phone. */
data class GateFix(
    val lat: Double,
    val lng: Double,
    /** Milliseconds on a clock that only goes forward. */
    val atMs: Long,
    /** m/s; null when the fix has no speed. */
    val speed: Float? = null,
    /** Degrees; null when the fix has no heading. */
    val bearing: Float? = null,
    /** Metres; null when the fix doesn't say. */
    val accuracy: Float? = null,
)

/** What happens to a fix: passed on as it is, or replaced by where the car is believed to be. [note] is for the drive log. */
sealed class GateVerdict(val note: String?) {
  class Pass(note: String? = null) : GateVerdict(note)

  class Replace(val lat: Double, val lng: Double, val speed: Float, val bearing: Float?, note: String?) : GateVerdict(note)
}

/**
 * The gate's rules for one stream of fixes.
 *
 * - **Standing** (the believed speed and the fix's both under 2.5 km/h): GPS wander within 12 m keeps
 *   the car exactly where it stopped, at speed 0 with its last real heading. A fix further away is
 *   doubted: a standing car can't be 30 m on without its own GPS speed saying it moved.
 * - **Moving**: a fix is believed when it is no further from the last believed one than the two
 *   speeds allow over the time between them (30% spare, plus both accuracies and 3 m). Its own speed
 *   counts only as far as a car could change speed in that time (6 m/s² up, 10 braking); a believed
 *   place with an impossible speed is passed on with a possible one. A doubted fix shows the car
 *   carried on from the last believed one at its speed and heading, for up to 4 s.
 * - Doubted fixes that agree with each other become believed: 2 in a row moving, 8 standing (GPS
 *   that has really moved on, a long-lost signal settling).
 * - Nothing to judge by (the first fix, or none believed for 30 s, after a tunnel or the app coming
 *   back): believed. So is a car pulling away, at once, and a fix after a GPS gap, judged by the
 *   real length of the gap.
 */
class GateRules {
  private var believed: GateFix? = null
  private var believedSpeed = 0f
  private var believedBearing: Float? = null
  private val doubted = ArrayList<GateFix>()

  fun next(fix: GateFix): GateVerdict {
    val ref = believed
    if (ref == null || fix.atMs - ref.atMs > STALE_MS || fix.atMs < ref.atMs) {
      believe(fix, fix.speed ?: 0f, ref)
      return GateVerdict.Pass()
    }
    val seconds = ((fix.atMs - ref.atMs) / 1000f).coerceAtLeast(MIN_GAP_S)
    val moved = metres(ref.lat, ref.lng, fix.lat, fix.lng)
    // A fix's own speed only counts as far as a car could get there from the believed speed in the
    // time: multipath that claims 40 m/s can't stretch its own allowance. No speed: from the movement.
    val claimed = fix.speed
    val speedNow = claimed?.let { plausibleSpeed(believedSpeed, it, seconds) } ?: (moved / seconds)

    if (believedSpeed < STOPPED_MS && speedNow < STOPPED_MS) {
      if (moved < STILL_WANDER_M) {
        // Standing: the car stays where it stopped. The believed fix moves on in time only, so a
        // pull-away is judged against the last second, not the start of a long red light.
        doubted.clear()
        believed = ref.copy(atMs = fix.atMs)
        return GateVerdict.Replace(ref.lat, ref.lng, 0f, believedBearing, note = null)
      }
      return doubt(fix, ref, moved, STOPPED_CONFIRMATIONS)
    }

    val reach = (believedSpeed + speedNow) / 2f * seconds * SPEED_MARGIN + spare(ref) + spare(fix) + SLACK_M
    if (moved <= reach) {
      believe(fix, speedNow, ref)
      if (claimed == null || claimed == speedNow) return GateVerdict.Pass()
      // The place is believable but the speed isn't: pass the place with a speed a car could have.
      val bearing = if (speedNow >= HEADING_SPEED_MS) fix.bearing ?: believedBearing else believedBearing
      return GateVerdict.Replace(fix.lat, fix.lng, speedNow, bearing, note = "kept a fix but not its ${"%.0f".format(claimed * 3.6f)} km/h")
    }
    // A car that was standing needs the longer run of agreeing fixes, whatever speed the fix claims.
    return doubt(fix, ref, moved, if (believedSpeed < STOPPED_MS) STOPPED_CONFIRMATIONS else CONFIRMATIONS)
  }

  /** [claimed] held to what a car can do from [from] m/s in [seconds]: 6 m/s² up, 10 m/s² braking. */
  private fun plausibleSpeed(from: Float, claimed: Float, seconds: Float) =
      claimed.coerceIn((from - MAX_BRAKE_MS2 * seconds).coerceAtLeast(0f), from + MAX_ACCEL_MS2 * seconds)

  private fun doubt(fix: GateFix, ref: GateFix, moved: Float, needed: Int): GateVerdict {
    doubted += fix
    if (doubted.size >= needed && doubted.zipWithNext().all { (a, b) -> agree(a, b) }) {
      val n = doubted.size
      // Their speed: the newest's own, else how fast the agreeing fixes moved between them.
      val before = doubted[n - 2]
      val between = metres(before.lat, before.lng, fix.lat, fix.lng) / ((fix.atMs - before.atMs) / 1000f).coerceAtLeast(MIN_GAP_S)
      believe(fix, fix.speed ?: between, ref)
      return GateVerdict.Pass(note = "took a jump of ${moved.toInt()} m after $n fixes agreed")
    }
    val note = "doubted a fix ${moved.toInt()} m away at ${"%.1f".format((fix.speed ?: 0f) * 3.6f)} km/h (believed ${"%.1f".format(believedSpeed * 3.6f)} km/h)"
    if (believedSpeed < STOPPED_MS) {
      believed = ref.copy(atMs = fix.atMs)
      return GateVerdict.Replace(ref.lat, ref.lng, 0f, believedBearing, note)
    }
    // Moving: carry on from the believed fix along its heading, for a few seconds at most.
    val bearing = believedBearing
    val carry = believedSpeed * ((fix.atMs - ref.atMs) / 1000f).coerceAtMost(MAX_CARRY_S)
    val at = if (bearing != null) ahead(ref.lat, ref.lng, bearing.toDouble(), carry.toDouble()) else ref.lat to ref.lng
    return GateVerdict.Replace(at.first, at.second, believedSpeed, bearing, note)
  }

  /** Whether two doubted fixes tell the same story: [b] where [a] could have got to. */
  private fun agree(a: GateFix, b: GateFix): Boolean {
    val seconds = ((b.atMs - a.atMs) / 1000f).coerceAtLeast(MIN_GAP_S)
    val moved = metres(a.lat, a.lng, b.lat, b.lng)
    val va = a.speed ?: (moved / seconds)
    val vb = b.speed?.let { plausibleSpeed(va, it, seconds) } ?: (moved / seconds)
    if (va < STOPPED_MS && vb < STOPPED_MS) return moved < STILL_WANDER_M
    val expected = (va + vb) / 2f * seconds
    val give = spare(a) + spare(b) + SLACK_M
    // Fixes that claim to move must actually have moved about that far: a run of fixes saying
    // 70 km/h from one spot is multipath, not a car.
    return moved <= expected * SPEED_MARGIN + give && moved >= expected / SPEED_MARGIN - give
  }

  private fun believe(fix: GateFix, speed: Float, previous: GateFix?) {
    doubted.clear()
    believed = fix
    believedSpeed = speed
    // A heading only from a fix moving fast enough for it to mean something.
    if (speed >= HEADING_SPEED_MS) {
      believedBearing =
          fix.bearing
              ?: previous?.takeIf { metres(it.lat, it.lng, fix.lat, fix.lng) > HEADING_MOVE_M }?.let { bearing(it.lat, it.lng, fix.lat, fix.lng).toFloat() }
              ?: believedBearing
    }
  }

  /** What a fix's own accuracy adds to the distance allowed: capped, so a vague fix can't excuse a jump. */
  private fun spare(fix: GateFix) = (fix.accuracy ?: DEFAULT_ACCURACY_M).coerceIn(2f, MAX_SPARE_M)

  private companion object {
    /** Under this (2.5 km/h) the car is standing; crawling traffic above it counts as moving. */
    const val STOPPED_MS = 0.7f
    const val STILL_WANDER_M = 12f
    const val SPEED_MARGIN = 1.3f
    const val SLACK_M = 3f
    const val DEFAULT_ACCURACY_M = 10f
    const val MAX_SPARE_M = 25f
    const val CONFIRMATIONS = 2
    const val STOPPED_CONFIRMATIONS = 8
    const val MAX_CARRY_S = 4f
    const val MIN_GAP_S = 0.2f
    const val STALE_MS = 30_000L
    const val HEADING_SPEED_MS = 2f
    const val HEADING_MOVE_M = 8f
    /** A quick car's 0 to 100 km/h in under 5 s. */
    const val MAX_ACCEL_MS2 = 6f
    /** An emergency stop. */
    const val MAX_BRAKE_MS2 = 10f
  }
}

private const val EARTH_M = 6_371_008.8

private fun metres(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
  val p1 = Math.toRadians(lat1)
  val p2 = Math.toRadians(lat2)
  val dp = p2 - p1
  val dl = Math.toRadians(lng2 - lng1)
  val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
  return (2 * EARTH_M * asin(sqrt(h.coerceIn(0.0, 1.0)))).toFloat()
}

private fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
  val p1 = Math.toRadians(lat1)
  val p2 = Math.toRadians(lat2)
  val dl = Math.toRadians(lng2 - lng1)
  val y = sin(dl) * cos(p2)
  val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
  return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

/** The point [metres] from (lat, lng) towards compass [bearing]. */
private fun ahead(lat: Double, lng: Double, bearing: Double, metres: Double): Pair<Double, Double> {
  if (metres <= 0.0) return lat to lng
  val d = metres / EARTH_M
  val p1 = Math.toRadians(lat)
  val l1 = Math.toRadians(lng)
  val th = Math.toRadians(bearing)
  val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(th))
  val l2 = l1 + atan2(sin(th) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
  return Math.toDegrees(p2) to Math.toDegrees(l2)
}
