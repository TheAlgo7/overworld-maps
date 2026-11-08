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
 *     -a com.thealgothrim.overworld.DEBUG_DRIVE --es theme gta6 --es time night
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
      // --es open settings|saved|nearby|none: the car's themed panel, for screenshots. --ef scroll
      // 300: a drag on the car screen of that many pixels while it's open (--ef fling for a flick).
      "com.thealgothrim.overworld.DEBUG_PANEL" -> {
        intent.getStringExtra("open")?.let { open ->
          com.thealgothrim.overworld.ui.CarPanels.open =
              when (open) {
                "settings" -> com.thealgothrim.overworld.ui.CarPanel.Settings
                "saved" -> com.thealgothrim.overworld.ui.CarPanel.Saved
                "nearby" -> com.thealgothrim.overworld.ui.CarPanel.NearbyKinds
                else -> null
              }
        }
        if (intent.hasExtra("scroll")) com.thealgothrim.overworld.ui.CarPanels.scrollBy(intent.getFloatExtra("scroll", 0f))
        if (intent.hasExtra("fling")) com.thealgothrim.overworld.ui.CarPanels.fling(intent.getFloatExtra("fling", 0f))
        Log.i("DebugDrive", "panel ${com.thealgothrim.overworld.ui.CarPanels.open}, scrolled to ${com.thealgothrim.overworld.ui.CarPanels.scroll?.value} of ${com.thealgothrim.overworld.ui.CarPanels.scroll?.maxValue}")
      }
      // --es as home|work --es name X --ef lat --ef lng: set Home or Work without tapping through the phone.
      "com.thealgothrim.overworld.DEBUG_SAVE" -> {
        val place =
            com.thealgothrim.overworld.search.Place(
                intent.getStringExtra("name") ?: "Home",
                "",
                GeographicCoordinate(intent.getFloatExtra("lat", 0f).toDouble(), intent.getFloatExtra("lng", 0f).toDouble()),
            )
        if (intent.getStringExtra("as") == "work") AppModule.saved.setWork(place) else AppModule.saved.setHome(place)
      }
      // --ez on true|false: the phone's car remote without a car (RemoteScreen).
      "com.thealgothrim.overworld.DEBUG_REMOTE" -> com.thealgothrim.overworld.ui.CarRemote.forced = intent.getBooleanExtra("on", true)
      // 0.6 screens, opened from adb for checks: --es you saved|timeline|history|none,
      // --ei settings 0..6 (-1 closes), --es day today|yesterday|2026-11-07|none, --ez area true.
      "com.thealgothrim.overworld.DEBUG_UI" -> {
        intent.getStringExtra("you")?.let { com.thealgothrim.overworld.ui.DebugUi.you.value = it }
        if (intent.hasExtra("settings")) com.thealgothrim.overworld.ui.DebugUi.settingsTab.value = intent.getIntExtra("settings", 0)
        intent.getStringExtra("day")?.let { com.thealgothrim.overworld.ui.DebugUi.day.value = it }
        if (intent.getBooleanExtra("area", false)) com.thealgothrim.overworld.ui.DebugUi.pickArea.value = true
        intent.getStringExtra("mode")?.let { m -> runCatching { com.thealgothrim.overworld.routing.TravelMode.valueOf(m.uppercase()) }.getOrNull()?.let { AppModule.prefs.setMode(it) } }
        intent.getStringExtra("units")?.let { u -> runCatching { com.thealgothrim.overworld.prefs.Units.valueOf(u.uppercase()) }.getOrNull()?.let { AppModule.prefs.setUnits(it) } }
        intent.getStringExtra("engine")?.let { e -> runCatching { com.thealgothrim.overworld.prefs.EngineType.valueOf(e.uppercase()) }.getOrNull()?.let { AppModule.prefs.setEngine(it) } }
        // --ef to_lat --ef to_lng [--es to_name X] [--ez preview true]: a place card, and with
        // preview its route preview with the mode tabs, as a tap on Directions would.
        if (intent.hasExtra("to_lat")) {
          val place = com.thealgothrim.overworld.search.Place(intent.getStringExtra("to_name")?.replace("%s", " ") ?: "Dropped pin", "", GeographicCoordinate(intent.getFloatExtra("to_lat", 0f).toDouble(), intent.getFloatExtra("to_lng", 0f).toDouble()))
          vm.choose(place)
          if (intent.getBooleanExtra("preview", false)) vm.directions()
        }
        // --ez icons/b3d true|false: the map's place icons and 3D buildings (phone and car), for
        // measuring what each costs a frame.
        if (intent.hasExtra("icons") || intent.hasExtra("b3d")) {
          val store = AppModule.themeStore
          val d = store.details.value
          val c = store.carDetails.value
          val icons = intent.getBooleanExtra("icons", d.placeIcons)
          val b3d = intent.getBooleanExtra("b3d", d.buildings3d)
          store.setDetails(d.copy(placeIcons = icons, buildings3d = b3d))
          store.setCarDetails(c.copy(placeIcons = intent.getBooleanExtra("icons", c.placeIcons), buildings3d = intent.getBooleanExtra("b3d", c.buildings3d)))
        }
        // --es bench "lat,lng,zoom,tilt,seconds": a scripted pan of the phone map there, then one
        // frames line in logcat (see FrameBenchEffect). The app must be open on the map.
        intent.getStringExtra("bench")?.let { b ->
          com.thealgothrim.overworld.ui.DebugUi.bench.value = b.split(',').mapNotNull { it.trim().toDoubleOrNull() }.toDoubleArray().takeIf { it.size >= 2 }
        }
        // --ez frames true: how smoothly each map drew since the last call (fps, stutters, CPU/GPU
        // per frame), in logcat under DebugDrive; the first call starts a fresh count.
        if (intent.getBooleanExtra("frames", false)) {
          android.os.Handler(android.os.Looper.getMainLooper()).post { Log.i("DebugDrive", "frames: ${com.thealgothrim.overworld.map.FrameStats.summary() ?: "no map drew"}") }
        }
        // --es net off|on|auto: MapLibre as if offline (only downloaded areas and its cache draw), or back.
        when (intent.getStringExtra("net")) {
          "off" -> org.maplibre.android.MapLibre.setConnected(false)
          "on" -> org.maplibre.android.MapLibre.setConnected(true)
          "auto" -> org.maplibre.android.MapLibre.setConnected(null)
        }
        // --es offline download|list: download a small demo box at Connaught Place, or log the areas.
        when (intent.getStringExtra("offline")) {
          "download" -> AppModule.offline.download("Connaught Place (test)", 28.640, 77.228, 28.622, 77.206) { Log.i("DebugDrive", "offline download: ${it ?: "started"}") }
          // Deletes every downloaded area (test areas off a phone).
          "deleteall" -> {
            AppModule.offline.refresh()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ AppModule.offline.areas.value.forEach { AppModule.offline.delete(it.id) } }, 1500)
          }
          // Empties MapLibre's ordinary cache, so with --es net off only downloaded areas can draw.
          "clearcache" ->
              org.maplibre.android.offline.OfflineManager.getInstance(context)
                  .clearAmbientCache(
                      object : org.maplibre.android.offline.OfflineManager.FileSourceCallback {
                        override fun onSuccess() = Log.i("DebugDrive", "ambient cache cleared").let {}

                        override fun onError(message: String) = Log.w("DebugDrive", "cache: $message").let {}
                      }
                  )
          "list" -> {
            AppModule.offline.refresh()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ AppModule.offline.areas.value.forEach { Log.i("DebugDrive", "offline: $it") } }, 1500)
          }
        }
      }
      // Demo saved places and lists (public Delhi landmarks), a demo Timeline for today and
      // yesterday, and a parked car: for checking the screens on a test phone. --ez clear true
      // removes what the demo added.
      "com.thealgothrim.overworld.DEBUG_DEMO" -> DebugDemo.run(intent.getBooleanExtra("clear", false))
      "com.thealgothrim.overworld.DEBUG_CHIME" -> com.thealgothrim.overworld.traffic.CameraChime.play(context)
      // A problem report's files, written but not sent (logcat under DebugDrive lists them); with
      // --ez send true, the email app opens as from Settings.
      "com.thealgothrim.overworld.DEBUG_REPORT" -> {
        if (intent.getBooleanExtra("mark", false)) com.thealgothrim.overworld.report.ProblemReport.mark("debug")
        if (intent.getBooleanExtra("send", false)) com.thealgothrim.overworld.report.ProblemReport.send(context.applicationContext, "Debug report.")
        else com.thealgothrim.overworld.report.ProblemReport.write(context).forEach { Log.i("DebugDrive", "report file ${it.name}: ${it.length()} bytes at ${it.absolutePath}") }
      }
      // The route's flyover and underpass choices as worked out (see RoadLevels).
      "com.thealgothrim.overworld.DEBUG_LEVELS" -> {
        val lv = vm.extras.value.levels
        if (lv == null) Log.i("DebugDrive", "levels: none for this route (yet)")
        else {
          Log.i("DebugDrive", "levels: ${lv.cues.size} choices, ${lv.structures.size} structures, ${lv.parallels.size} parallel roads over ${lv.from.toInt()}-${lv.to.toInt()} m of ${lv.length.toInt()} m")
          lv.structures.forEach { (span, level) -> Log.i("DebugDrive", "  route $level ${span.start.toInt()}-${span.endInclusive.toInt()} m") }
          lv.cues.forEach { c -> Log.i("DebugDrive", "  ${c.kind} split at ${c.at.toInt()} m, structure at ${c.structureAt.toInt()} m, other side ${c.otherSide}") }
          lv.parallels.forEach { p -> Log.i("DebugDrive", "  beside: ${p.level} ${p.from.toInt()}-${p.to.toInt()} m, ${p.lines.size} lines") }
        }
        val ui = vm.navigationUiState.value
        Log.i("DebugDrive", "cue now: ${vm.levelCueAhead(ui, vm.extras.value)}")
      }
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
      // Traffic on the trip, as if the traffic service had found it: --ef ahead_from/ahead_to
      // (metres ahead of the car), --ei level (1 slow, 2 heavy, 3 closed). The next refresh replaces it.
      "com.thealgothrim.overworld.DEBUG_TRAFFIC" -> {
        val ui = vm.navigationUiState.value
        val line = ui.routeGeometry?.let { com.thealgothrim.overworld.traffic.RouteLine(it) } ?: return
        val done = line.length - (ui.progress?.distanceRemaining ?: line.length)
        val level = intent.getIntExtra("level", 2)
        val from = done + intent.getFloatExtra("ahead_from", 0f)
        val to = done + intent.getFloatExtra("ahead_to", 400f)
        vm.showTraffic(listOf(com.thealgothrim.overworld.traffic.TrafficSpan(from, to, level)))
        Log.i("DebugDrive", "traffic level $level from ${from.toInt()} to ${to.toInt()} m along the route")
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
                "voice=${AppModule.ttsObserver.tts != null} muted=${AppModule.ttsObserver.isMuted} " +
                "theme=${AppModule.themeStore.theme.value.id} fade=${AppModule.themeStore.theme.value.routeFade} " +
                "palette=${vm.mapVariant.value ?: "day"} carTheme=${AppModule.themeStore.carTheme.value.id} " +
                "carPalette=${vm.carMapVariant.value ?: "day"} left=${ui.progress?.distanceRemaining?.toInt()} m",
        )
      }
      "com.thealgothrim.overworld.DEBUG_DRIVE" -> {
        intent.getStringExtra("theme")?.let {
          // Both screens: the car keeps its own theme since 0.5.9.
          AppModule.themeStore.select(it)
          AppModule.themeStore.selectCar(it)
        }
        // --es time night (auto, day, dusk, night): GTA VI's time-of-day palette, as in Settings.
        intent.getStringExtra("time")?.let { t ->
          runCatching { com.thealgothrim.overworld.theme.MapTime.valueOf(t.uppercase()) }.getOrNull()?.let {
            AppModule.themeStore.setMapTime(it)
            AppModule.themeStore.setCarMapTime(it)
          }
        }
        // --es car_dark true|false|none: the car's day or night as Android Auto would report it,
        // for the car preview on an emulator (Auto in the car follows it; see MapTime.carVariant).
        intent.getStringExtra("car_dark")?.let { vm.setCarDark(it.toBooleanStrictOrNull()) }
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
          // --ef from_heading: the way the car faces at the start, so the route starts on its carriageway.
          val heading = if (intent.hasExtra("from_heading")) intent.getFloatExtra("from_heading", 0f).toDouble() else null
          vm.startNavigation(to, intent.getStringExtra("name")?.replace("%s", " "), from, heading)
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
