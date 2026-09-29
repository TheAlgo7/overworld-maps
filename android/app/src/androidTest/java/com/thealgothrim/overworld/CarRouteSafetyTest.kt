package com.thealgothrim.overworld

import android.util.Log
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stadiamaps.ferrostar.car.app.template.models.toCarStep
import java.time.Instant
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.ferrostar.DrivingSide
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.RouteStep
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind

/**
 * Real Valhalla routes through Delhi's roundabouts (and Chandigarh's), every step turned into
 * Android Auto's turn card and trip the way the car screen and the car's own display do it.
 *
 * Android Auto throws on a roundabout maneuver it considers malformed, which closed the app in the
 * car in 0.2.1 whenever a route started on a roundabout. Several trips here start on a roundabout
 * on purpose, the way a reroute after a missed exit does. Needs the network.
 */
@RunWith(AndroidJUnit4::class)
class CarRouteSafetyTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private fun at(lat: Double, lng: Double) = GeographicCoordinate(lat, lng)

  private val trips =
      listOf(
          // Starting on a roundabout ring (a trip begun there, or a reroute after a missed exit).
          "CP Outer Circle to India Gate" to (at(28.6315, 77.2167) to at(28.6129, 77.2295)),
          "Windsor Place to Rashtrapati Bhavan" to (at(28.6214, 77.2167) to at(28.6143, 77.1994)),
          "India Gate hexagon to Khan Market" to (at(28.6129, 77.2295) to at(28.6003, 77.2270)),
          "Mandi House to Lodhi Garden" to (at(28.6255, 77.2342) to at(28.5931, 77.2197)),
          "Teen Murti to AIIMS" to (at(28.6026, 77.1993) to at(28.5672, 77.2100)),
          "Dhaula Kuan to Airport T3" to (at(28.5918, 77.1617) to at(28.5562, 77.0999)),
          // Through Lutyens' Delhi, roundabout after roundabout.
          "Chhatarpur to Connaught Place" to (at(28.5068, 77.1749) to at(28.6315, 77.2167)),
          "Khan Market to Mandi House" to (at(28.6003, 77.2270) to at(28.6255, 77.2342)),
          "Lodhi Road to Rajiv Chowk" to (at(28.5890, 77.2270) to at(28.6328, 77.2197)),
          // Chandigarh: a city of roundabouts.
          "Chandigarh Sector 17 to Sector 35" to (at(30.7415, 76.7872) to at(30.7230, 76.7600)),
          "Chandigarh Matka Chowk to Sukhna Lake" to (at(30.7473, 76.7910) to at(30.7421, 76.8188)),
      )

  @Test
  fun everyStepOfRealRoutesBuildsForAndroidAuto() {
    AppModule.init(context)
    val core = AppModule.ferrostarCore
    var steps = 0
    var roundabouts = 0
    var rejectedRaw = 0
    val failures = mutableListOf<String>()

    for ((name, trip) in trips) {
      val (from, to) = trip
      val route =
          runBlocking {
                core.getRoutes(
                    UserLocation(from, 6.0, null, Instant.now(), null),
                    listOf(Waypoint(coordinate = to, kind = WaypointKind.BREAK)),
                )
              }
              .first()
      rejectedRaw += route.steps.count { carError(it) != null }
      for (step in route.safeForCar().steps) {
        steps++
        if (step.roundaboutExitNumber != null || step.visualInstructions.any { "ROUND" in it.primaryContent.maneuverType?.name.orEmpty() || "ROTARY" in it.primaryContent.maneuverType?.name.orEmpty() }) roundabouts++
        carError(step)?.let { failures += "$name: ${step.instruction} (${step.visualInstructions.map { it.primaryContent.maneuverType }}, exit ${step.roundaboutExitNumber}): $it" }
      }
      Thread.sleep(400) // go easy on the free routing server
    }

    Log.i(TAG, "$steps steps on ${trips.size} routes, $roundabouts at roundabouts; $rejectedRaw would have crashed Android Auto without safeForCar")
    assertTrue("Android Auto rejects:\n" + failures.joinToString("\n"), failures.isEmpty())
    // The routes must actually exercise the crash, or this test proves nothing.
    assertTrue("no step here would have crashed 0.2.1; pick routes that start on a roundabout", rejectedRaw > 0)
  }

  /** Builds [step] every way the car does; the exception Android Auto throws, or null. */
  private fun carError(step: RouteStep): Throwable? =
      runCatching {
            val side = step.drivingSide ?: DrivingSide.LEFT
            val estimate =
                TravelEstimate.Builder(
                        Distance.create(step.distance, Distance.UNIT_METERS),
                        DateTimeWithZone.create(System.currentTimeMillis(), TimeZone.getDefault()),
                    )
                    .setRemainingTimeSeconds(step.duration.toLong())
                    .build()
            for (instruction in step.visualInstructions) {
              val carStep = instruction.toCarStep(context, side, step.roundaboutExitNumber?.toInt())
              // The turn card (Android Auto's own cards) and the trip for the car's cluster display.
              RoutingInfo.Builder().setCurrentStep(carStep, Distance.create(100.0, Distance.UNIT_METERS)).build()
              Trip.Builder().addStep(carStep, estimate).build()
            }
          }
          .exceptionOrNull()

  private companion object {
    const val TAG = "CarRouteSafetyTest"
  }
}
