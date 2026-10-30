package com.thealgothrim.overworld

import com.thealgothrim.overworld.location.SustainedDeviation
import com.thealgothrim.overworld.location.runsAlong
import com.thealgothrim.overworld.routing.OverworldRoutes
import com.thealgothrim.overworld.traffic.RouteLine
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.RouteDeviation

/**
 * The 29 Oct drive: Google's reroutes U-turned through a median gap at Ambience on NH-48 that
 * OpenStreetMap maps as a ramp further on, so every match came back in two pieces and was thrown
 * away; and a car going the wrong way under 30 m from its route was never off it.
 */
class RerouteTest {
  private fun fixture(name: String) = javaClass.classLoader!!.getResource("google/$name")!!.readText()

  private val t0 = Instant.parse("2026-10-29T15:40:00Z")

  @Test
  fun findsTheGapWhereTheMatchBroke() {
    val answer = JSONObject(fixture("trace-ambience-uturn.json"))
    val gaps = OverworldRoutes.gaps(answer.getJSONArray("tracepoints"))
    assertEquals(1, gaps.size)
    val gap = gaps.single()
    assertEquals(16, gap.fromIndex)
    assertEquals(17, gap.toIndex)
    // The two carriageways at the U-turn, tens of metres apart.
    assertTrue(com.thealgothrim.overworld.traffic.metres(gap.from, gap.to) in 15.0..80.0)
  }

  @Test
  fun readsValhallasShape() {
    val bridge = OverworldRoutes.decodePolyline6(JSONObject(fixture("route-ambience-uturn.json")).getString("shape"))
    assertTrue(bridge.size > 3)
    assertEquals(28.502, bridge.first().lat, 0.002)
    assertEquals(77.092, bridge.first().lng, 0.002)
    // The ramp OpenStreetMap has for that U-turn: about 219 m.
    assertEquals(219.0, RouteLine(bridge).length, 15.0)
  }

  @Test
  fun aLineThatStartsBehindTheCarIsCaught() {
    val north = listOf(GeographicCoordinate(28.50, 77.10), GeographicCoordinate(28.501, 77.10))
    assertFalse(OverworldRoutes.startsAgainst(north, 10.0))
    assertTrue(OverworldRoutes.startsAgainst(north, 190.0))
    // Across: not "behind", a turn.
    assertFalse(OverworldRoutes.startsAgainst(north, 90.0))
  }

  @Test
  fun prefixCutsTheLineWhereAsked() {
    val east = listOf(GeographicCoordinate(28.5, 77.20), GeographicCoordinate(28.5, 77.21), GeographicCoordinate(28.5, 77.22))
    val cut = OverworldRoutes.prefix(east, 300.0)
    assertEquals(300.0, RouteLine(cut).length, 1.0)
    assertEquals(2, cut.size)
  }

  @Test
  fun drivingTheOtherCarriagewayIsADetourEvenUnderThirtyMetres() {
    val d = SustainedDeviation()
    // 20 m off, going the other way at 40 km/h: a detour after 5 s.
    val s = (0 until 20).first { d.judge(20.0, 5.0, t0.plusSeconds(it.toLong()), speed = 11.0, against = true) is RouteDeviation.Deviation }
    assertEquals(5, s)
  }

  @Test
  fun aWanderingHeadingAtWalkingPaceIsNoDetour() {
    val d = SustainedDeviation()
    for (s in 0 until 30) assertTrue(d.judge(5.0, 5.0, t0.plusSeconds(s.toLong()), speed = 1.0, against = true) is RouteDeviation.NoDeviation)
  }

  @Test
  fun bothLegsOfAUTurnCountAsTheRoute() {
    // East on one carriageway, a U-turn, west on the other 20 m south.
    val out = listOf(GeographicCoordinate(28.50000, 77.20000), GeographicCoordinate(28.50000, 77.21000))
    val back = listOf(GeographicCoordinate(28.49982, 77.21000), GeographicCoordinate(28.49982, 77.20000))
    val onTheWayBack = GeographicCoordinate(28.49984, 77.205)
    assertTrue(runsAlong(onTheWayBack, 270.0, listOf(out, back), 35.0))
    assertTrue(runsAlong(onTheWayBack, 90.0, listOf(out, back), 35.0))
    // Only the way out on the route, driving west 20 m from it: against it.
    assertFalse(runsAlong(onTheWayBack, 270.0, listOf(out), 35.0))
  }
}
