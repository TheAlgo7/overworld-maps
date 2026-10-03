package com.thealgothrim.overworld

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import uniffi.ferrostar.GeographicCoordinate

/**
 * Debug builds only: start or stop a simulated trip from the laptop, so the Android Auto screen can
 * be tested in the Desktop Head Unit without touching (or unlocking) the phone.
 *
 *   adb shell am broadcast -n com.thealgothrim.overworld.debug/com.thealgothrim.overworld.DebugDriveReceiver \
 *     -a com.thealgothrim.overworld.DEBUG_DRIVE --ef lat 28.6129 --ef lng 77.2295 --es name "India Gate"
 *   adb shell am broadcast -n com.thealgothrim.overworld.debug/com.thealgothrim.overworld.DebugDriveReceiver \
 *     -a com.thealgothrim.overworld.DEBUG_DRIVE --es theme rdr2
 *   adb shell am broadcast -n com.thealgothrim.overworld.debug/com.thealgothrim.overworld.DebugDriveReceiver \
 *     -a com.thealgothrim.overworld.DEBUG_STOP
 *
 * A place by name, the way Android Auto's "navigate to India Gate" arrives:
 *   adb shell am broadcast -n com.thealgothrim.overworld.debug/com.thealgothrim.overworld.DebugDriveReceiver \
 *     -a com.thealgothrim.overworld.DEBUG_DRIVE --es query "India%sGate"
 *
 * The camera chime (-a com.thealgothrim.overworld.DEBUG_CHIME), to hear it in the car.
 *
 * What the app holds right now (trip, simulator, route extras), in logcat under DebugDrive:
 *   adb shell am broadcast -n com.thealgothrim.overworld.debug/com.thealgothrim.overworld.DebugDriveReceiver \
 *     -a com.thealgothrim.overworld.DEBUG_STATE
 */
class DebugDriveReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    AppModule.init(context)
    val vm = AppModule.viewModel
    when (intent.action) {
      "com.thealgothrim.overworld.DEBUG_STOP" -> vm.stopNavigation()
      "com.thealgothrim.overworld.DEBUG_CHIME" -> com.thealgothrim.overworld.traffic.CameraChime.play(context)
      // How fast a virtual display like the car's runs, with and without asking for 60 Hz (logcat
      // under DebugDrive). Works with the phone locked: nothing shows on its screen.
      "com.thealgothrim.overworld.DEBUG_VDFPS" -> {
        val done = goAsync()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
          VirtualDisplayProbe.run(context.applicationContext, requested = 0f) { first ->
            VirtualDisplayProbe.run(context.applicationContext, requested = 60f) { second ->
              Log.i("DebugDrive", "virtual display: default $first, asking for 60 Hz $second")
              done.finish()
            }
          }
        }
      }
      // Free drive: the simulator moves the car along a route to --ef lat/lng (from --ef
      // from_lat/from_lng) with no trip running. DEBUG_STOP ends it.
      "com.thealgothrim.overworld.DEBUG_FREEDRIVE" -> {
        val to = GeographicCoordinate(intent.getFloatExtra("lat", 0f).toDouble(), intent.getFloatExtra("lng", 0f).toDouble())
        val from = GeographicCoordinate(intent.getFloatExtra("from_lat", 0f).toDouble(), intent.getFloatExtra("from_lng", 0f).toDouble())
        val done = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
          try {
            val route =
                AppModule.ferrostarCore
                    .getRoutes(
                        uniffi.ferrostar.UserLocation(from, 6.0, null, java.time.Instant.now(), null),
                        listOf(uniffi.ferrostar.Waypoint(coordinate = to, kind = uniffi.ferrostar.WaypointKind.BREAK)),
                    )
                    .first()
                    .safeForCar()
            AppModule.locationProvider.enableSimulationOn(route)
            Log.i("DebugDrive", "free drive on a ${route.distance.toInt()} m route, no trip")
          } catch (e: Exception) {
            Log.w("DebugDrive", "free drive route failed", e)
          } finally {
            done.finish()
          }
        }
      }
      // The app's search for --es query (%s for spaces) from --ef lat/lng, the first six results as
      // the car would list them. With --es nearby FUEL (a Nearby name) instead: that kind, from
      // where the app thinks the car is (on a trip, along the route).
      "com.thealgothrim.overworld.DEBUG_SEARCH" -> {
        val done = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
          try {
            val started = System.currentTimeMillis()
            val kind = intent.getStringExtra("nearby")?.let { com.thealgothrim.overworld.search.Nearby.valueOf(it) }
            val query = intent.getStringExtra("query")?.replace("%s", " ").orEmpty()
            val near = GeographicCoordinate(intent.getFloatExtra("lat", 0f).toDouble(), intent.getFloatExtra("lng", 0f).toDouble())
            val found = if (kind != null) vm.findNearby(kind) else AppModule.search.search(query, near)
            val took = System.currentTimeMillis() - started
            Log.i("DebugDrive", "search \"${kind ?: query}\": ${found.size} results in $took ms")
            found.take(6).forEachIndexed { i, p ->
              val km = com.thealgothrim.overworld.traffic.metres(vm.currentCoordinate ?: near, p.coordinate) / 1000
              Log.i("DebugDrive", "  ${i + 1}. ${p.name} | ${p.note ?: "%.1f km".format(km)} | ${p.detail}")
            }
            // --ez stop true: on a trip, add the first one as a stop, as picking it in the car does.
            if (intent.getBooleanExtra("stop", false)) {
              found.firstOrNull()?.let { first ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                  vm.addStop(first) { ok ->
                    val trip = AppModule.ferrostarCore.state.value.tripState as? uniffi.ferrostar.TripState.Navigating
                    Log.i("DebugDrive", "stop at ${first.name}: $ok, waypoints now ${trip?.remainingWaypoints?.map { "%.4f,%.4f".format(it.coordinate.lat, it.coordinate.lng) }}, ${trip?.progress?.distanceRemaining?.toInt()} m left")
                  }
                }
              }
            }
          } catch (e: Exception) {
            Log.w("DebugDrive", "search failed", e)
          } finally {
            done.finish()
          }
        }
      }
      // A camera at --ef lat/lng (and --ef heading), as if marked with + Cam there.
      "com.thealgothrim.overworld.DEBUG_CAMERA" ->
          AppModule.cameras.mark(
              GeographicCoordinate(intent.getFloatExtra("lat", 0f).toDouble(), intent.getFloatExtra("lng", 0f).toDouble()),
              if (intent.hasExtra("heading")) intent.getFloatExtra("heading", 0f).toDouble() else null,
          )
      "com.thealgothrim.overworld.DEBUG_STATE" -> {
        val ui = vm.navigationUiState.value
        val extras = vm.extras.value
        Log.i(
            "DebugDrive",
            "navigating=${ui.isNavigating()} to=${ui.destination} route=${ui.routeGeometry?.size ?: 0}pts " +
                "core=${AppModule.ferrostarCore.state.value.tripState::class.simpleName} " +
                "simulating=${AppModule.locationProvider.isSimulating.value} testDrive=${vm.testDrive.value} " +
                "extras: ${extras.signals.size} signals, ${extras.incidents.size} incidents, " +
                "${extras.trafficSpans.size} traffic spans, eta=${extras.eta?.travelSeconds} " +
                "voice=${AppModule.ttsObserver.tts != null} muted=${AppModule.ttsObserver.isMuted}",
        )
      }
      "com.thealgothrim.overworld.DEBUG_DRIVE" -> {
        intent.getStringExtra("theme")?.let { AppModule.themeStore.select(it) }
        if (intent.hasExtra("lat") && intent.hasExtra("lng")) {
          val to = GeographicCoordinate(intent.getFloatExtra("lat", 0f).toDouble(), intent.getFloatExtra("lng", 0f).toDouble())
          // --ez test false: a real trip from the phone's GPS position instead of a simulated one.
          vm.setTestDrive(intent.getBooleanExtra("test", true))
          // "%s" for spaces, the same convention as `adb shell input text`.
          // Optional start ("from_lat"/"from_lng"), so a drive can begin anywhere, not where the phone is.
          val from =
              if (intent.hasExtra("from_lat") && intent.hasExtra("from_lng"))
                  GeographicCoordinate(intent.getFloatExtra("from_lat", 0f).toDouble(), intent.getFloatExtra("from_lng", 0f).toDouble())
              else null
          vm.startNavigation(to, intent.getStringExtra("name")?.replace("%s", " "), from)
          Log.i("DebugDrive", "test drive to $to")
        }
        intent.getStringExtra("query")?.replace("%s", " ")?.let { query ->
          vm.setTestDrive(true)
          vm.navigateToQuery(query)
          Log.i("DebugDrive", "test drive to \"$query\"")
        }
      }
    }
  }
}
