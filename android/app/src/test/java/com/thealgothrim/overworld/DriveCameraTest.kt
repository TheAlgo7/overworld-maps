package com.thealgothrim.overworld

import com.thealgothrim.overworld.map.autoZoom
import com.thealgothrim.overworld.map.driveCamera
import com.thealgothrim.overworld.map.hudScale
import com.thealgothrim.overworld.map.leadMetres
import com.thealgothrim.overworld.map.steadied
import com.thealgothrim.overworld.theme.Skin
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Speed
import uniffi.ferrostar.UserLocation

class DriveCameraTest {
  private val base = 16.4

  @Test
  fun fasterRoadsZoomOut() {
    val city = autoZoom(base, 20 / 3.6, null)
    val arterial = autoZoom(base, 60 / 3.6, null)
    val highway = autoZoom(base, 110 / 3.6, null)
    assertTrue(city > arterial && arterial > highway)
    assertEquals(base + 0.4, city, 0.01)
    assertEquals(base - 0.8, highway, 0.01)
  }

  @Test
  fun aTurnAheadZoomsIn() {
    val fast = 80 / 3.6
    val farFromTurn = autoZoom(base, fast, 1_500.0)
    val atTurn = autoZoom(base, fast, 60.0)
    assertTrue(atTurn > farFromTurn)
    assertEquals(base + 0.8, atTurn, 0.01)
    // Already close in at a crawl: the turn doesn't push it further than base + 0.8.
    assertEquals(base + 0.8, autoZoom(base, 10 / 3.6, 60.0), 0.01)
  }

  @Test
  fun longStraightOnAFastRoadPullsBackMore() {
    val fast = 80 / 3.6
    assertTrue(autoZoom(base, fast, 3_000.0) < autoZoom(base, fast, null))
  }

  @Test
  fun eachGameHasItsOwnTilt() {
    assertEquals(60.0, Skin.GTA6.driveCamera.tilt, 0.0)
    assertEquals(45.0, Skin.GTA.driveCamera.tilt, 0.0)
    assertEquals(0.0, Skin.RDR.driveCamera.tilt, 0.0)
  }

  @Test
  fun hudKeepsFullSizeOnTheCurvvAndShrinksElsewhere() {
    assertEquals(1f, hudScale(1500f, 540f), 0.001f)
    // 800 x 480 head unit, and the map's half of a split screen.
    assertEquals(0.8f, hudScale(800f, 456f), 0.001f)
    assertEquals(0.9f, hudScale(900f, 540f), 0.001f)
    // Never too small to read.
    assertEquals(0.6f, hudScale(400f, 300f), 0.001f)
  }

  @Test
  fun arrowLeadsByTheGlideAndTheFixAge() {
    fun fix(kmh: Double, ageMs: Long) =
        UserLocation(GeographicCoordinate(28.5, 77.2), 5.0, null, Instant.now().minusMillis(ageMs), Speed(kmh / 3.6, null))
    assertEquals(0.0, leadMetres(fix(2.0, 0)), 0.0)
    // 60 km/h, a fresh fix: one second's glide, about 16.7 m.
    assertEquals(16.7, leadMetres(fix(60.0, 0)), 0.3)
    // The fix was half a second old already.
    assertEquals(25.0, leadMetres(fix(60.0, 500)), 0.5)
  }

  @Test
  fun steadiedDropsKinksButKeepsCorners() {
    // A straight road east with a node 1 m off, then a real right-angle turn north.
    val line = listOf(
        GeographicCoordinate(28.50000, 77.20000),
        GeographicCoordinate(28.50001, 77.20050), // ~1 m north of the line: a kink
        GeographicCoordinate(28.50000, 77.20100),
        GeographicCoordinate(28.50090, 77.20100), // 100 m north: a real turn
    )
    val out = steadied(line)
    assertEquals(3, out.size)
    assertTrue(out.none { it.lng == 77.20050 })
  }
}
