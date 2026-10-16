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
