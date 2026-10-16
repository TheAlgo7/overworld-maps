package com.thealgothrim.overworld

import com.thealgothrim.overworld.location.GateFix
import com.thealgothrim.overworld.location.GateRules
import com.thealgothrim.overworld.location.GateVerdict
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The location gate's rules on made-up drives along a straight road heading 82° (Safdarjung Road). */
class LocationGateTest {
  private val start = 28.5900 to 77.2000
  private val heading = 82.0

  /** A fix [along] metres up the road and [right] metres off it, at [s] seconds. */
  private fun fix(s: Double, along: Double, speed: Float?, right: Double = 0.0, bearing: Float? = heading.toFloat(), accuracy: Float = 5f): GateFix {
    val th = Math.toRadians(heading)
    val n = along * cos(th) - right * sin(th)
    val e = along * sin(th) + right * cos(th)
    val lat = start.first + n / 111_320.0
    val lng = start.second + e / (111_320.0 * cos(Math.toRadians(start.first)))
    return GateFix(lat, lng, atMs = (s * 1000).toLong(), speed = speed, bearing = bearing, accuracy = accuracy)
  }

  private fun metresBetween(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val dn = (bLat - aLat) * 111_320.0
    val de = (bLng - aLng) * 111_320.0 * cos(Math.toRadians(aLat))
    return Math.hypot(dn, de)
  }

  /** Drives up to [stopAt] metres at 8.3 m/s (30 km/h), then stands; returns the rules and the next second. */
  private fun driveThenStop(rules: GateRules, stopAt: Double): Double {
    var s = 0.0
    var along = 0.0
    while (along < stopAt) {
      assertTrue(rules.next(fix(s, along, 8.3f)) is GateVerdict.Pass)
      s += 1.0
      along += 8.3
    }
    rules.next(fix(s, stopAt, 0.2f)) // the last rolling metre
    return s + 1.0
  }

  @Test
  fun standingAtARedLightBadFixesDontMoveTheCar() {
    val rules = GateRules()
    var s = driveThenStop(rules, 200.0)
    val bad = setOf(8, 20, 33)
    for (k in 0 until 45) {
      val f =
          if (k in bad) fix(s, 200.0 + 22, 0.1f, right = 18.0, bearing = 170f) // onto the crossing road
          else fix(s, 200.0 + ((k * 7) % 9 - 4).toDouble(), 0.1f, right = ((k * 5) % 7 - 3).toDouble(), bearing = (k * 37 % 360).toFloat())
      val v = rules.next(f)
      // Held exactly where it stopped, standing, with the heading it drove in on.
      assertTrue("second $k", v is GateVerdict.Replace)
      v as GateVerdict.Replace
      assertEquals(0f, v.speed)
      assertEquals(heading.toFloat(), v.bearing!!, 0.01f)
      val stop = fix(0.0, 200.0, 0f)
      assertTrue("second $k moved", metresBetween(stop.lat, stop.lng, v.lat, v.lng) < 0.5)
      s += 1.0
    }
  }

  @Test
  fun pullingAwayFromALongStopIsImmediate() {
    val rules = GateRules()
    var s = driveThenStop(rules, 100.0)
    repeat(90) { rules.next(fix(s, 100.0 + (it % 3), 0.0f)); s += 1.0 }
    // 2 m/s^2 from rest: the first fix moving is believed.
    assertTrue(rules.next(fix(s, 101.0, 2.0f)) is GateVerdict.Pass)
    assertTrue(rules.next(fix(s + 1, 104.0, 4.0f)) is GateVerdict.Pass)
  }

  @Test
  fun crawlingTrafficIsFollowedWithoutDelay() {
    val rules = GateRules()
    var along = 0.0
    for (k in 0 until 60) {
      assertTrue("second $k", rules.next(fix(k.toDouble(), along, 0.9f, bearing = null)) is GateVerdict.Pass)
      along += 0.9
    }
  }

  @Test
  fun gpsBackAfterACutIsTakenAtOnce() {
    val rules = GateRules()
    var along = 0.0
    for (k in 0 until 20) { rules.next(fix(k.toDouble(), along, 13.9f)); along += 13.9 }
    // 20 s without fixes: stopped 48 m on (braked during the gap), and moving 278 m on.
    assertTrue(GateRules().also { r -> for (k in 0 until 20) r.next(fix(k.toDouble(), k * 13.9, 13.9f)) }.next(fix(39.0, along + 48, 0f)) is GateVerdict.Pass)
    assertTrue(rules.next(fix(39.0, along + 278, 13.9f)) is GateVerdict.Pass)
    // A minute without any (a long tunnel, the app coming back): taken whatever it says.
    assertTrue(rules.next(fix(110.0, along + 2_000, 0f)) is GateVerdict.Pass)
  }

  @Test
  fun aBadFixWhileMovingCarriesTheCarOnWithItsOwnSpeedAndHeading() {
    val rules = GateRules()
    var along = 0.0
    for (k in 0 until 10) { rules.next(fix(k.toDouble(), along, 13.9f)); along += 13.9 }
    // 60 m sideways, with a wild speed and heading of its own.
    val v = rules.next(fix(10.0, along, 30f, right = 60.0, bearing = 300f))
    assertTrue(v is GateVerdict.Replace)
    v as GateVerdict.Replace
    assertEquals(13.9f, v.speed, 0.01f)
    assertEquals(heading.toFloat(), v.bearing!!, 0.01f)
    // Carried on along the road from the last believed fix (139 m up), about 13.9 m further.
    val expected = fix(10.0, along, 13.9f)
    assertTrue(metresBetween(expected.lat, expected.lng, v.lat, v.lng) < 1.0)
    // The next good fix is believed again.
    assertTrue(rules.next(fix(11.0, along + 13.9, 13.9f)) is GateVerdict.Pass)
  }

  @Test
  fun gpsThatHasReallyMovedOnIsBelievedAfterTwoFixes() {
    val rules = GateRules()
    var along = 0.0
    for (k in 0 until 10) { rules.next(fix(k.toDouble(), along, 13.9f)); along += 13.9 }
    assertTrue(rules.next(fix(10.0, along, 13.9f, right = 80.0)) is GateVerdict.Replace)
    assertTrue(rules.next(fix(11.0, along + 13.9, 13.9f, right = 80.0)) is GateVerdict.Pass)
  }

  @Test
  fun aSimulatedDriveAtThreeTimesSpeedPassesUntouched() {
    // Ferrostar's simulator at warp 3: a fix every third of a second, 10 m apart, no speed.
    val rules = GateRules()
    for (k in 0 until 300) {
      assertTrue("fix $k", rules.next(fix(k / 3.0, k * 10.0, null, bearing = null)) is GateVerdict.Pass)
    }
  }

  @Test
  fun aBadFixClaimingSpeedAtALightIsStillHeld() {
    val rules = GateRules()
    var s = driveThenStop(rules, 200.0)
    repeat(5) { rules.next(fix(s, 200.0, 0.1f)); s += 1.0 }
    // Multipath with a speed of its own (72 km/h) can't buy its way off the crossing road, however
    // long it stays: fixes that claim to move but don't never agree with each other.
    repeat(12) {
      val v = rules.next(fix(s, 222.0, 20f, right = 18.0, bearing = 170f))
      assertTrue(v is GateVerdict.Replace)
      v as GateVerdict.Replace
      assertEquals(0f, v.speed)
      assertEquals(heading.toFloat(), v.bearing!!, 0.01f)
      s += 1.0
    }
  }

  @Test
  fun aRightPlaceWithAnImpossibleSpeedKeepsThePlaceNotTheSpeed() {
    val rules = GateRules()
    var along = 0.0
    for (k in 0 until 10) { rules.next(fix(k.toDouble(), along, 13.9f)); along += 13.9 }
    val v = rules.next(fix(10.0, along, 45f))
    assertTrue(v is GateVerdict.Replace)
    v as GateVerdict.Replace
    val at = fix(10.0, along, 13.9f)
    assertTrue(metresBetween(at.lat, at.lng, v.lat, v.lng) < 0.1)
    assertTrue("speed ${v.speed}", v.speed <= 13.9f + 6f)
  }

  @Test
  fun aVagueFixFarOffIsDoubted() {
    val rules = GateRules()
    var along = 0.0
    for (k in 0 until 10) { rules.next(fix(k.toDouble(), along, 5f)); along += 5.0 }
    assertTrue(rules.next(fix(10.0, along, 5f, right = 70.0, accuracy = 80f)) is GateVerdict.Replace)
  }
}
