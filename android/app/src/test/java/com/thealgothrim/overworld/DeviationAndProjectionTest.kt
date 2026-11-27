package com.thealgothrim.overworld

import com.thealgothrim.overworld.location.SustainedDeviation
import com.thealgothrim.overworld.location.metresFromSteps
import com.thealgothrim.overworld.map.RoutePolyline
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.Position
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.RouteDeviation

class DeviationAndProjectionTest {
  private val t0 = Instant.parse("2026-10-16T08:00:00Z")

  /** Seconds (of 1 Hz fixes) at [off] metres until the detector calls a detour, or null within [limit]. */
  private fun secondsToDetour(d: SustainedDeviation, off: Double, limit: Int = 40, accuracy: Double = 5.0, start: Int = 0): Int? {
    for (s in start until start + limit) {
      if (d.judge(off, accuracy, t0.plusSeconds(s.toLong())) is RouteDeviation.Deviation) return s - start
    }
    return null
  }

  @Test
  fun aServiceRoadAlongsideIsNoDetourButOneFortyMetresAwayIs() {
    assertEquals(null, secondsToDetour(SustainedDeviation(), 22.0))
    assertEquals(8, secondsToDetour(SustainedDeviation(), 40.0))
    assertEquals(2, secondsToDetour(SustainedDeviation(), 70.0))
  }

  @Test
  fun oneWildFixIsNoDetour() {
    val d = SustainedDeviation()
    assertTrue(d.judge(5.0, 5.0, t0) is RouteDeviation.NoDeviation)
    assertTrue(d.judge(90.0, 5.0, t0.plusSeconds(1)) is RouteDeviation.NoDeviation)
    // Back on the route: the clock starts again.
    assertTrue(d.judge(4.0, 5.0, t0.plusSeconds(2)) is RouteDeviation.NoDeviation)
    assertTrue(d.judge(90.0, 5.0, t0.plusSeconds(3)) is RouteDeviation.NoDeviation)
  }

  @Test
  fun aCarParkedBesideTheRouteStartIsNoDetour() {
    val d = SustainedDeviation()
    for (s in 0 until 60) assertTrue(d.judge(40.0, 5.0, t0.plusSeconds(s.toLong()), speed = 0.0) is RouteDeviation.NoDeviation)
    // Driving off the same 40 m away: a detour after 8 s of it.
    val driving = (60 until 80).first { s -> d.judge(40.0, 5.0, t0.plusSeconds(s.toLong()), speed = 8.0) is RouteDeviation.Deviation }
    assertEquals(67, driving)
  }

  @Test
  fun vagueFixesDecideNothing() {
    assertEquals(null, secondsToDetour(SustainedDeviation(), 70.0, accuracy = 60.0))
  }

  @Test
  fun aVagueFixDoesntPutACarThatLeftBackOnTheRoute() {
    val d = SustainedDeviation()
    assertEquals(2, secondsToDetour(d, 70.0))
    // Between tall buildings the next fix is good to 45 m: still off, not snapped back.
    assertTrue(d.judge(70.0, 45.0, t0.plusSeconds(3)) is RouteDeviation.Deviation)
    // A clear fix on the route ends it, and a vague one after that starts nothing.
    assertTrue(d.judge(5.0, 5.0, t0.plusSeconds(4)) is RouteDeviation.NoDeviation)
    assertTrue(d.judge(70.0, 45.0, t0.plusSeconds(5)) is RouteDeviation.NoDeviation)
  }

  /**
   * The 26 Nov drive, 23:23:16 to 23:23:28: the car took a right the route didn't want, GPS good to
   * 3.8 m, at about 25 km/h. Metres off the route each second, as the track recorded them. The old
   * rules called it at the last fix (77 m); turning away is called two fixes after 20 m.
   */
  @Test
  fun aCleanTurnOffTheRouteIsCalledInTwoSeconds() {
    val offs = listOf(9.0, 13.0, 18.0, 23.0, 28.0, 34.0, 40.0, 46.0, 52.0, 58.0, 64.0, 70.0, 77.0)
    val d = SustainedDeviation()
    val called = offs.indices.first { i -> d.judge(offs[i], 3.8, t0.plusSeconds(i.toLong()), speed = 7.0, against = false, angle = 55.0) is RouteDeviation.Deviation }
    assertEquals(28.0, offs[called], 0.0)
    // The old rules alone (no heading to go on) took until 64 m.
    val old = SustainedDeviation()
    val oldCalled = offs.indices.first { i -> old.judge(offs[i], 3.8, t0.plusSeconds(i.toLong()), speed = 7.0) is RouteDeviation.Deviation }
    assertTrue(offs[oldCalled] >= 60.0)
  }

  @Test
  fun aServiceRoadAlongsideStaysOnTheRouteEvenWithAWobblyHeading() {
    // 22 m beside the route, not getting further: no quick call, whatever the course says.
    val d = SustainedDeviation()
    for (s in 0 until 7) assertTrue(d.judge(22.0 + (s % 2), 5.0, t0.plusSeconds(s.toLong()), speed = 12.0, against = false, angle = 35.0) is RouteDeviation.NoDeviation)
  }

  @Test
  fun aTurnAwayNeedsASharpFixAndAHeadingOffTheLine() {
    val offs = listOf(20.0, 25.0, 30.0, 35.0)
    // Drifting off along the route's own direction (a road widening, GPS creeping): not a turn.
    val along = SustainedDeviation()
    assertTrue(offs.indices.none { i -> along.judge(offs[i], 5.0, t0.plusSeconds(i.toLong()), speed = 10.0, against = false, angle = 8.0) is RouteDeviation.Deviation })
    // The same turn with fixes good only to 18 m: left to the slower rules.
    val vague = SustainedDeviation()
    assertTrue(offs.indices.none { i -> vague.judge(offs[i], 18.0, t0.plusSeconds(i.toLong()), speed = 10.0, against = false, angle = 60.0) is RouteDeviation.Deviation })
  }

  @Test
  fun theArrowLetsGoOfTheLineWhenTheCarDoes() {
    val l = com.thealgothrim.overworld.map.LeavingRoute()
    // On the line, GPS wobbling a few metres: stays.
    for (off in listOf(3.0, 6.0, 9.0, 5.0)) assertTrue(!l.update(off, 3.8, 12.0))
    // One fix 17 m out is wobble; the second in a row is the car leaving.
    assertTrue(!l.update(17.0, 3.8, 7.0))
    assertTrue(l.update(23.0, 3.8, 7.0))
    // Held until the car is back near the line.
    assertTrue(l.update(12.0, 3.8, 7.0))
    assertTrue(!l.update(6.0, 3.8, 7.0))
    // One fix far out leaves at once; a standing car or a vague fix never does.
    assertTrue(l.update(35.0, 3.8, 7.0))
    l.reset()
    assertTrue(!l.update(35.0, 3.8, 0.3))
    assertTrue(!l.update(35.0, 30.0, 7.0))
  }

  @Test
  fun distanceToTheStepsAhead() {
    val east = listOf(GeographicCoordinate(28.5, 77.2), GeographicCoordinate(28.5, 77.21))
    // 0.0002° of latitude is about 22 m.
    assertEquals(22.3, metresFromSteps(GeographicCoordinate(28.5002, 77.205), listOf(east))!!, 0.3)
    assertEquals(null, metresFromSteps(GeographicCoordinate(28.5, 77.2), emptyList()))
  }

  @Test
  fun anOutAndBackRouteKeepsTheArrowOnTheLegItIsOn() {
    // East 1 km on one carriageway, a U-turn, and back west on the other, 20 m south.
    val route =
        RoutePolyline(
            listOf(
                GeographicCoordinate(28.50000, 77.20000),
                GeographicCoordinate(28.50000, 77.21023),
                GeographicCoordinate(28.49982, 77.21023),
                GeographicCoordinate(28.49982, 77.20000),
            )
        )
    // Halfway back along the return leg (about 1.5 km in), a fix drifted 13 m north: closer to the
    // way out than the way back. The whole-route search put it on the way out, 1 km behind.
    val drifted = Position(77.20512, 28.49994)
    assertTrue(route.project(drifted).progressMeters < 1_000.0)
    val near = route.projectNear(drifted, fromMetres = 1_500.0, aheadMetres = 150.0)
    assertNotNull(near)
    assertTrue("got ${near.progressMeters}", near.progressMeters in 1_450.0..1_600.0)
  }
}
