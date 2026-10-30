package com.thealgothrim.overworld

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thealgothrim.overworld.location.runsAlong
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.ferrostar.CourseOverGround
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Speed
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind

/**
 * The 29 Oct drive on NH-48 at Ambience, against the live servers: every reroute there came back
 * from Google U-turning through a median gap OpenStreetMap maps as a ramp further on, the match
 * broke in two and the app dropped Google's route. Now the gap is bridged, and a car's heading
 * decides which carriageway a route starts on. Needs the network.
 */
@RunWith(AndroidJUnit4::class)
class RerouteLiveTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val chhatarpurMetro = GeographicCoordinate(28.5068, 77.1749)

  private fun car(lat: Double, lng: Double, course: Int?, speed: Double) =
      UserLocation(GeographicCoordinate(lat, lng), 5.0, course?.let { CourseOverGround(it.toUShort(), 10u) }, Instant.now(), Speed(speed, 1.0))

  private fun route(from: UserLocation) =
      runBlocking { AppModule.routes.getRoutes(from, listOf(Waypoint(coordinate = chhatarpurMetro, kind = WaypointKind.BREAK))) }.first()

  @Test
  fun googlesUTurnAtAmbienceIsKept() {
    AppModule.init(context)
    // Where three of the night's reroutes were asked from, heading north-east up NH-48.
    for ((lat, lng) in listOf(28.484 to 77.075, 28.497 to 77.081, 28.499 to 77.080)) {
      val r = route(car(lat, lng, course = 40, speed = 11.0))
      Log.i(TAG, "$lat,$lng: ${AppModule.routes.lastSource}, ${r.distance.toInt()} m, ${r.steps.size} steps")
      assertEquals("from $lat,$lng", "Google", AppModule.routes.lastSource)
      Thread.sleep(500)
    }
  }

  @Test
  fun aRouteStartsTheWayTheCarIsGoing() {
    AppModule.init(context)
    // Both ways along NH-48 from the same point: each route's start runs the car's way.
    for (course in listOf(40, 220)) {
      val r = route(car(28.4925, 77.0845, course = course, speed = 12.0))
      val start = r.geometry.take(12)
      Log.i(TAG, "course $course: ${AppModule.routes.lastSource}, ${r.distance.toInt()} m")
      assertTrue("course $course starts against the car", runsAlong(start.first(), course.toDouble(), listOf(start), 40.0))
      Thread.sleep(500)
    }
  }

  private companion object {
    const val TAG = "RerouteLiveTest"
  }
}
