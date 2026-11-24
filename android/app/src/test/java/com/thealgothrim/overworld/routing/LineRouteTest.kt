package com.thealgothrim.overworld.routing

import kotlin.math.cos
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate

class LineRouteTest {
  private val start = GeographicCoordinate(28.6300, 77.2160)

  /** A walk from [start]: each leg is (bearing in degrees, metres), one point every 2 m. */
  private fun walk(vararg legs: Pair<Double, Double>): List<GeographicCoordinate> {
    val out = mutableListOf(start)
    for ((bearing, metres) in legs) {
      val n = (metres / 2).toInt()
      repeat(n) {
        val p = out.last()
        val kx = 111_320.0 * cos(Math.toRadians(p.lat))
        out += GeographicCoordinate(p.lat + 2 * cos(Math.toRadians(bearing)) / 111_320.0, p.lng + 2 * Math.sin(Math.toRadians(bearing)) / kx)
      }
    }
    return out
  }

  @Test
  fun aLaneThatTurnsRightThenLeft() {
    val line = walk(0.0 to 60.0, 90.0 to 40.0, 0.0 to 50.0)
    assertEquals(listOf("right", "left"), LineRoute.turns(line).map { it.modifier })
  }

  @Test
  fun gentleWigglesAreNotTurns() {
    val line = walk(0.0 to 40.0, 15.0 to 40.0, 0.0 to 40.0, 345.0 to 40.0)
    assertEquals(emptyList<String>(), LineRoute.turns(line).map { it.modifier })
  }

  @Test
  fun aJogIsNotATurn() {
    // Right then left 6 m on, and the lane goes on the same way: no "turn right, turn left".
    val line = walk(0.0 to 50.0, 90.0 to 6.0, 0.0 to 50.0)
    assertEquals(0, LineRoute.turns(line).size)
  }

  @Test
  fun aCornerWithTwoBendsIsOneTurn() {
    // 45 degrees, then 45 more 6 m on: one right turn.
    val line = walk(0.0 to 50.0, 45.0 to 6.0, 90.0 to 50.0)
    assertEquals(listOf("right"), LineRoute.turns(line).map { it.modifier })
  }

  @Test
  fun modifiersByAngle() {
    assertEquals("slight right", LineRoute.modifier(40.0))
    assertEquals("left", LineRoute.modifier(-90.0))
    assertEquals("sharp right", LineRoute.modifier(140.0))
    assertEquals("uturn", LineRoute.modifier(175.0))
  }

  @Test
  fun stepsCarryTheNextMoveOnTheirCard() {
    val json = JSONObject(LineRoute.osrm(walk(0.0 to 60.0, 90.0 to 40.0, 0.0 to 50.0), 120.0)!!)
    val steps = json.getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONArray("steps")
    // depart, the right, the left, arrive.
    assertEquals(4, steps.length())
    assertEquals("depart", steps.getJSONObject(0).getJSONObject("maneuver").getString("type"))
    val card = steps.getJSONObject(0).getJSONArray("bannerInstructions").getJSONObject(0).getJSONObject("primary")
    assertEquals("right", card.getString("modifier"))
    assertEquals("arrive", steps.getJSONObject(2).getJSONArray("bannerInstructions").getJSONObject(0).getJSONObject("primary").getString("type"))
    assertEquals("arrive", steps.getJSONObject(3).getJSONObject("maneuver").getString("type"))
    // Google's time is kept: 120 s for the whole walk.
    assertEquals(120.0, json.getJSONArray("routes").getJSONObject(0).getDouble("duration"), 0.5)
  }

  @Test
  fun tooShortToWalk() {
    assertNull(LineRoute.osrm(listOf(start, start), 10.0))
  }
}
