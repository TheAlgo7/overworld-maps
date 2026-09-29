package com.thealgothrim.overworld

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stadiamaps.ferrostar.core.isNavigating
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind

/**
 * Trips ended the moment they start. The location service Ferrostar starts with each trip only
 * calls startForeground() once it is bound, and Android kills the app ("did not then call
 * Service.startForeground()") if such a service is stopped before that. Stopping in the same
 * main-thread turn as starting always lands in that gap. Needs the network for the route.
 */
@RunWith(AndroidJUnit4::class)
class TripStopTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  private fun route(): Route {
    AppModule.init(instrumentation.targetContext)
    return runBlocking {
          AppModule.ferrostarCore.getRoutes(
              UserLocation(GeographicCoordinate(28.6315, 77.2167), 6.0, null, Instant.now(), null),
              listOf(Waypoint(coordinate = GeographicCoordinate(28.6129, 77.2295), kind = WaypointKind.BREAK)),
          )
        }
        .first()
        .safeForCar()
  }

  @Test
  fun stoppingATripAsItStartsDoesNotCrash() {
    val route = route()
    val core = AppModule.ferrostarCore
    AppModule.locationProvider.enableSimulationOn(route)
    repeat(3) {
      instrumentation.runOnMainSync {
        core.startNavigation(route)
        core.stopNavigation()
      }
      Thread.sleep(500)
    }
    // Android's deadline for startForeground() is 10 s; a broken stop kills the app by then.
    Thread.sleep(12_000)
    assertFalse(core.state.value.isNavigating())
    AppModule.locationProvider.disableSimulation()
  }

  @Test
  fun aTripStartedAgainBeforeTheServiceIsUpKeepsRunning() {
    val route = route()
    val core = AppModule.ferrostarCore
    AppModule.locationProvider.enableSimulationOn(route)
    instrumentation.runOnMainSync {
      core.startNavigation(route)
      core.stopNavigation()
      core.startNavigation(route)
    }
    Thread.sleep(12_000)
    assertTrue("trip ended on its own", core.state.value.isNavigating())
    instrumentation.runOnMainSync { core.stopNavigation() }
    Thread.sleep(2_000)
    AppModule.locationProvider.disableSimulation()
  }
}
