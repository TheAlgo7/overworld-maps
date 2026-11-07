package com.thealgothrim.overworld

import com.thealgothrim.overworld.location.HeightChange
import com.thealgothrim.overworld.location.LevelCheck
import com.thealgothrim.overworld.location.metresFromSteps
import com.thealgothrim.overworld.routing.CueKind
import com.thealgothrim.overworld.routing.Level
import com.thealgothrim.overworld.routing.LevelAnalysis
import com.thealgothrim.overworld.routing.Mvt
import com.thealgothrim.overworld.routing.RoadLevels
import com.thealgothrim.overworld.routing.TileRoad
import com.thealgothrim.overworld.routing.VectorTiles
import com.thealgothrim.overworld.traffic.RouteLine
import com.thealgothrim.overworld.traffic.decodePolyline6
import com.thealgothrim.overworld.traffic.metres
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.CourseOverGround
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Speed
import uniffi.ferrostar.UserLocation

/**
 * The 7 Nov drive: NH-48 northbound at Shankar Chowk, Gurugram. The route left NH-48 with "Bear
 * left" at 28.4926, 77.0833 for the service road beside the Shankar Chowk to Ambience flyover, whose
 * deck starts 700 m on at 28.4981, 77.0885. Gaurav stayed on NH-48, went up the flyover, and the
 * arrow stayed on the service road. Real OpenStreetMap roads from the map's tiles, real Valhalla routes.
 */
class RoadLevelsTest {
  private val fixture = JSONObject(javaClass.getResource("/levels/shankar-chowk.json")!!.readText())

  private val roads: List<TileRoad> =
      (0 until fixture.getJSONArray("roads").length()).map { i ->
        val o = fixture.getJSONArray("roads").getJSONObject(i)
        val pts = o.getJSONArray("p")
        TileRoad(
            o.getString("c"),
            when (o.getString("b")) {
              "bridge" -> Level.BRIDGE
              "tunnel" -> Level.TUNNEL
              else -> Level.SURFACE
            },
            o.getInt("o"),
            o.getInt("r") == 1,
            (0 until pts.length()).map { k -> GeographicCoordinate(pts.getJSONArray(k).getDouble(0), pts.getJSONArray(k).getDouble(1)) },
        )
      }

  /** The route on the service road beside the flyover (what the app had on 7 Nov). */
  private val beside = decodePolyline6(fixture.getString("beside"))
  /** The route over the flyover on NH-48 (where Gaurav actually drove). */
  private val over = decodePolyline6(fixture.getString("over"))

  private val bearLeft = GeographicCoordinate(28.49264, 77.08327)
  private val deckStart = GeographicCoordinate(28.49808, 77.08852)

  private fun along(route: List<GeographicCoordinate>, p: GeographicCoordinate) = RouteLine(route).project(p).first

  @Test
  fun readsTheMapsOwnTile() {
    val bytes = javaClass.getResource("/levels/14-11700-6837.pbf")!!.readBytes()
    val layer = Mvt.layer(bytes, "transportation")
    assertNotNull(layer)
    assertEquals(4096, layer!!.extent)
    val tileRoads = VectorTiles.roads(layer, 11700, 6837)
    assertTrue("a tile of Gurugram has hundreds of roads", tileRoads.size > 100)
    // The flyover's north end, near Ambience, is in this tile: a one-way motorway bridge.
    val deckEnd = GeographicCoordinate(28.50400, 77.09370)
    val flyover = tileRoads.filter { it.level == Level.BRIDGE && it.cls == "motorway" }.minByOrNull { r -> r.line.minOf { metres(it, deckEnd) } }
    assertNotNull(flyover)
    assertTrue(flyover!!.line.minOf { metres(it, deckEnd) } < 60.0)
    assertEquals(1, flyover.oneway)
  }

  @Test
  fun theServiceRouteSaysDontTakeTheFlyoverAtItsBearLeft() {
    val turn = along(beside, bearLeft)
    val levels = RoadLevels.analyse(beside, roads, maneuvers = listOf(turn))
    // (The route also exits NH-48 under the IFFCO Chowk interchange earlier on: a cue of its own.)
    val cue = levels.cues.firstOrNull { it.kind == CueKind.SKIP_FLYOVER && kotlin.math.abs(it.structureAt - along(beside, deckStart)) < 200 }
    assertNotNull("cues: ${levels.cues}", cue)
    // On the route's own "Bear left", so the card shows both.
    assertEquals(turn, cue!!.at, 1.0)
    // The flyover starts some 700 m on, to the right of the service road.
    assertEquals(along(beside, deckStart), cue.structureAt, 60.0)
    assertEquals(1, cue.otherSide)
    // The route itself stays on the ground the whole way.
    assertTrue(levels.structures.none { it.second == Level.BRIDGE })
    assertTrue("NH-48 beside the route is known for the GPS check", levels.parallels.any { it.level == Level.BRIDGE })
  }

  @Test
  fun theFlyoverRouteIsOnTheBridgeAndNotTold() {
    val levels = RoadLevels.analyse(over, roads, maneuvers = emptyList())
    val bridge = levels.structures.firstOrNull { it.second == Level.BRIDGE }
    assertNotNull(bridge)
    assertEquals(along(over, deckStart), bridge!!.first.start, 25.0)
    assertTrue("the deck is most of a kilometre long", bridge.first.endInclusive - bridge.first.start > 800)
    // The service road ran beside NH-48 long before the deck (a separate road with its own slip
    // roads), so there's no split at the flyover to call out, and the route is on the flyover itself.
    assertTrue("cues: ${levels.cues}", levels.cues.none { it.kind == CueKind.SKIP_FLYOVER })
  }

  /** Fixes a second apart at [kmh] along [path] from [fromAlong] metres, each [sideways] metres to its right. */
  private fun drive(path: List<GeographicCoordinate>, kmh: Double, seconds: Int, fromAlong: Double = 0.0, sideways: Double = 0.0): List<UserLocation> {
    val line = RouteLine(path)
    val step = kmh / 3.6
    return (0 until seconds).mapNotNull { s ->
      val a = fromAlong + s * step
      if (a > line.length - 5) return@mapNotNull null
      val p = line.at(a)
      val q = line.at(a + 5)
      val heading = (Math.toDegrees(kotlin.math.atan2((q.lng - p.lng) * kotlin.math.cos(Math.toRadians(p.lat)), q.lat - p.lat)) + 360) % 360
      val r = Math.toRadians(heading + 90)
      val shifted = GeographicCoordinate(p.lat + sideways * kotlin.math.cos(r) / 111_320.0, p.lng + sideways * kotlin.math.sin(r) / (111_320.0 * kotlin.math.cos(Math.toRadians(p.lat))))
      UserLocation(shifted, 5.0, CourseOverGround(heading.toInt().toUShort(), null), Instant.ofEpochSecond(1_762_500_000L + s), Speed(step, null))
    }
  }

  private object NoBarometer : HeightChange {
    override fun rise(seconds: Int): Float? = null

    override fun drop(seconds: Int): Float? = null
  }

  /** A barometer saying the phone climbed [metres] in the last half minute. */
  private class Climbed(val metres: Float) : HeightChange {
    override fun rise(seconds: Int) = metres

    override fun drop(seconds: Int) = 0f
  }

  /** Seconds of driving [car] until the check calls a detour from [route], or null if it never does. */
  private fun secondsToCall(route: List<GeographicCoordinate>, car: List<UserLocation>, height: HeightChange): Int? {
    val levels = RoadLevels.analyse(route, roads, maneuvers = listOf(along(route, bearLeft)))
    val check = LevelCheck(height, clock = { 0L })
    car.forEachIndexed { i, fix ->
      val along = along(route, fix.coordinates)
      val off = metresFromSteps(fix.coordinates, listOf(route))
      if (check.judge(fix, along, off, levels) != null) return i
    }
    return null
  }

  @Test
  fun stayingOnTheFlyoverIsCaughtWithinSeconds() {
    // NH-48 from just past the split, at 40 km/h, as on 7 Nov.
    val car = drive(over, 40.0, 60)
    val seconds = secondsToCall(beside, car, NoBarometer)
    assertNotNull("never noticed the car was on NH-48", seconds)
    // The old rule needed 30 m for 8 s; NH-48 is 20 to 30 m from the service road here.
    assertTrue("took $seconds s", seconds!! <= 25)
  }

  @Test
  fun carryingStraightOnAtTheSplitIsCaughtByHeading() {
    // The car comes up NH-48 on the route, then carries straight on where the route bears left
    // (NH-48 runs straight there; the slip road leaves at about 15 degrees).
    val split = along(beside, bearLeft)
    val path = RouteLine(beside).slice(split - 150, split) + over
    val car = drive(path, 45.0, 60)
    val seconds = secondsToCall(beside, car, NoBarometer)
    assertNotNull("never caught", seconds)
    // 150 m at 45 km/h is 12 s to the split; Google Maps switched route a second after it.
    val afterSplit = seconds!! - 12
    assertTrue("caught $afterSplit s after the split", afterSplit in 0..2)
  }

  @Test
  fun takingTheSlipAsRoutedIsNeverCalled() {
    // Through the split on the route itself: the heading turns with the slip road, never away from it.
    for (side in listOf(-3.0, 0.0, 3.0)) {
      val car = drive(beside, 45.0, 90, fromAlong = along(beside, bearLeft) - 150, sideways = side)
      assertNull("called with GPS $side m to the side", secondsToCall(beside, car, NoBarometer))
    }
  }

  @Test
  fun aClimbOntoTheFlyoverSettlesItSooner() {
    val car = drive(over, 40.0, 60)
    val plain = secondsToCall(beside, car, NoBarometer)!!
    val climbed = secondsToCall(beside, car, Climbed(4f))!!
    assertTrue("$climbed s with the climb vs $plain s on GPS alone", climbed < plain)
  }

  @Test
  fun drivingTheServiceRoadAsRoutedIsNeverCalled() {
    // On the route, GPS a few metres either side of it the whole way.
    for (side in listOf(-4.0, 0.0, 4.0)) {
      val car = drive(beside, 35.0, 120, fromAlong = along(beside, bearLeft) + 20, sideways = side)
      assertNull("called with GPS $side m to the side", secondsToCall(beside, car, NoBarometer))
    }
  }

  @Test
  fun anglesAndBearings() {
    assertEquals(0.0, LevelAnalysis.angle(10.0, 10.0), 1e-9)
    assertEquals(20.0, LevelAnalysis.angle(350.0, 10.0), 1e-9)
    assertEquals(180.0, LevelAnalysis.angle(90.0, 270.0), 1e-9)
    assertEquals(90.0, LevelAnalysis.bearing(1.0, 0.0), 1e-9)
  }
}
