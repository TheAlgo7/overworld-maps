package com.thealgothrim.overworld

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.stadiamaps.ferrostar.composeui.notification.DefaultForegroundNotificationBuilder
import com.stadiamaps.ferrostar.core.AlternativeRouteProcessor
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.AndroidTtsStatusListener
import com.stadiamaps.ferrostar.core.CorrectiveAction
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.RouteDeviationHandler
import com.stadiamaps.ferrostar.core.SpokenInstructionObserver
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.thealgothrim.overworld.location.LocationGate
import com.thealgothrim.overworld.location.SustainedDeviation
import com.thealgothrim.overworld.routing.CurrentLevels
import com.thealgothrim.overworld.routing.GoogleMaps
import com.thealgothrim.overworld.routing.OverworldRoutes
import com.thealgothrim.overworld.routing.VectorTiles
import uniffi.ferrostar.RouteAdapter
import com.stadiamaps.ferrostar.core.location.SimulatedLocationProvider
import com.stadiamaps.ferrostar.core.location.toAndroidLocation
import com.stadiamaps.ferrostar.core.withJsonOptions
import com.stadiamaps.ferrostar.googleplayservices.FusedNavigationLocationProvider
import com.stadiamaps.ferrostar.ui.formatters.DistanceMeasurementSystem
import com.stadiamaps.ferrostar.ui.formatters.LocalizedDistanceFormatter
import com.thealgothrim.overworld.offline.OfflineMaps
import com.thealgothrim.overworld.prefs.NavPrefs
import com.thealgothrim.overworld.routing.RouteOptions
import com.thealgothrim.overworld.search.PlaceSearch
import com.thealgothrim.overworld.search.SavedPlaces
import com.thealgothrim.overworld.search.SearchHistory
import com.thealgothrim.overworld.timeline.TimelineKeeper
import com.thealgothrim.overworld.timeline.TimelineStore
import com.thealgothrim.overworld.traffic.MyCameras
import com.thealgothrim.overworld.traffic.TrafficService
import com.thealgothrim.overworld.theme.GameFonts
import com.thealgothrim.overworld.theme.ThemeStore
import java.time.Duration
import java.time.Instant
import java.util.Locale
import okhttp3.OkHttpClient
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.SpokenInstruction
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WellKnownRouteProvider
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep

/**
 * Everything the phone and the car share: one navigation core, one location source, one theme.
 *
 * All services are free and keyless: OpenFreeMap tiles (in the styles), Valhalla routing on the
 * FOSSGIS public server, Photon search by komoot. Fine for one person's driving; a public release
 * would need its own or paid endpoints.
 */
object AppModule {
  const val VALHALLA_URL = "https://valhalla1.openstreetmap.de/route"
  const val USER_AGENT = "Overworld/0.1 (personal navigation app; github.com/TheAlgo7)"

  /** Where a test drive starts when there is no GPS fix yet: Chhatarpur, Delhi. */
  val defaultStart = GeographicCoordinate(lat = 28.5068, lng = 77.1749)

  private lateinit var appContext: Context

  fun init(context: Context) {
    if (!::appContext.isInitialized) appContext = context.applicationContext
    GameFonts.init(appContext)
    com.thealgothrim.overworld.traffic.TomTomUsage.init(appContext)
  }

  val context: Context
    get() = appContext

  private val defaultStartLocation =
      UserLocation(defaultStart, 6.0, null, Instant.now(), null)

  /** Whether a newer Overworld is out (see UpdateCheck). */
  val updates: UpdateCheck by lazy { UpdateCheck(context, httpClient) }

  val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(20))
        .addInterceptor(com.thealgothrim.overworld.traffic.TomTomUsage)
        .addInterceptor { chain ->
          chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()
  }

  val locationProvider: NavigationLocationProvider by lazy {
    NavigationLocationProvider(
        // Real GPS goes through the gate (a bad fix at a light can't move the car, on a trip or
        // not); the test drive's simulator is exact and goes straight in.
        liveProviding = LocationGate(FusedNavigationLocationProvider(appContext)),
        simulatedProvider =
            SimulatedLocationProvider(
                warpFactor = 3u,
                initialLocation = defaultStartLocation.toAndroidLocation(),
            ),
    )
  }

  /** Google's drive laid onto OpenStreetMap's roads, or Valhalla's own route (see OverworldRoutes). */
  val routes: OverworldRoutes by lazy {
    val options = mapOf("units" to "kilometers")
    OverworldRoutes(
        RouteAdapter.fromWellKnownRouteProvider(WellKnownRouteProvider.Valhalla(VALHALLA_URL, "auto").withJsonOptions(options)),
        httpClient,
        GoogleMaps(httpClient),
        googleOn = { themeStore.useGoogle.value },
        options = {
          RouteOptions(
              avoidTolls = prefs.avoidTolls.value,
              avoidHighways = prefs.avoidHighways.value,
              avoidFerries = prefs.avoidFerries.value,
              fuelSaver = prefs.fuelSaver.value,
              walkKmh = prefs.walkPace.value.kmh,
          )
        },
    )
  }

  /** Settings Google keeps under Navigation, Your vehicles, Privacy, Offline maps and Notifications. */
  val prefs: NavPrefs by lazy { NavPrefs(appContext) }

  /** Maps history: searches, places looked at, directions (You > History). */
  val history: SearchHistory by lazy { SearchHistory(appContext) { prefs.keepHistory.value } }

  /** Drives and stops, kept on the phone (You > Timeline). */
  val timeline: TimelineKeeper by lazy { TimelineKeeper(TimelineStore(appContext)) }

  val offline: OfflineMaps by lazy { OfflineMaps(appContext) }

  /**
   * A walk's trip settings: turns advance and a detour is noticed sooner than a car's, at walking
   * distances (a car's 30 m to advance a step is half a street on foot).
   */
  val walkingConfig: NavigationControllerConfig by lazy {
    NavigationControllerConfig(
        WaypointAdvanceMode.WaypointWithinRange(30.0),
        stepAdvanceDistanceEntryAndExit(12u, 3u, 15u),
        stepAdvanceDistanceToEndOfStep(5u, 15u),
        // Off the path by 35 m with a fix sure to 25 m: city GPS on foot is rarely sharper.
        RouteDeviationTracking.StaticThreshold(25u, 35.0),
        CourseFiltering.RAW,
    )
  }

  val ferrostarCore: FerrostarCore by lazy {
    val core =
        FerrostarCore(
            routes as CustomRouteProvider,
            httpClient = httpClient.toOkHttpClientProvider(),
            locationProvider = locationProvider,
            foregroundServiceManager =
                SafeForegroundServiceManager(
                    appContext,
                    // In km like the rest of the app: by default it follows the phone's language,
                    // and with English (US) the trip notification counted miles.
                    DefaultForegroundNotificationBuilder(
                        appContext,
                        distanceFormatter = com.thealgothrim.overworld.car.ChosenUnitsFormatter,
                    ),
                ),
            navigationControllerConfig =
                NavigationControllerConfig(
                    WaypointAdvanceMode.WaypointWithinRange(100.0),
                    stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
                    stepAdvanceDistanceToEndOfStep(10u, 32u),
                    // A detour once the car has been off long enough (30 m for 8 s, 60 m for 2 s),
                    // not one fix past 50 m: that missed a service road 40 m away for good. And
                    // within seconds when GPS puts the car on the flyover (or the road under it)
                    // beside the route, which is nearer than that (see LevelCheck).
                    RouteDeviationTracking.Custom(SustainedDeviation(levels = { r -> CurrentLevels.value?.takeIf { it.matches(r.geometry) } })),
                    CourseFiltering.SNAP_TO_ROUTE,
                ),
        )
    core.deviationHandler = RouteDeviationHandler { _, _, remainingWaypoints ->
      CorrectiveAction.GetNewRoutes(remainingWaypoints)
    }
    // Ferrostar only calls this while off the route. Same as its default (take the first new
    // route), but made safe for Android Auto first: a reroute often starts on a roundabout.
    core.alternativeRouteProcessor = AlternativeRouteProcessor { c, routes ->
      routes.firstOrNull()?.let { r ->
        // A walk keeps its walking distances after a reroute.
        if (AppModule.routes.tripMode == com.thealgothrim.overworld.routing.TravelMode.WALK) c.replaceRoute(r.safeForCar(), walkingConfig) else c.replaceRoute(r.safeForCar())
      }
    }
    core
  }

  /**
   * Spoken turn prompts. Off until turned on (Settings > Voice guidance); the choice is kept. The
   * phone and the car share it, so it lives as long as the app: shutting it down with the phone
   * screen silenced the car's prompts until Android Auto was reconnected.
   */
  val ttsObserver: AndroidTtsObserver by lazy {
    AndroidTtsObserver(appContext, statusObserver = TtsVoice).apply {
      setMuted(!appContext.getSharedPreferences("overworld", Context.MODE_PRIVATE).getBoolean("voice", false))
    }
  }

  /**
   * What Ferrostar is given instead of [ttsObserver]: muted prompts never reach it. Ferrostar asks
   * for audio focus (music ducked) BEFORE it checks mute, and only gives it back when an utterance
   * finishes; a muted prompt never speaks, so the music stayed ducked for the whole trip.
   */
  val spokenInstructions: SpokenInstructionObserver by lazy {
    object : SpokenInstructionObserver by ttsObserver {
      override fun onSpokenInstructionTrigger(spokenInstruction: SpokenInstruction) {
        if (ttsObserver.isMuted) return
        // Settings > Navigation > Guidance volume: Softer speaks quieter; Normal and Louder are
        // Ferrostar's own full-volume voice, as before.
        if (prefs.volume.value == com.thealgothrim.overworld.prefs.GuidanceVolume.SOFTER) say(spokenInstruction.text, spokenInstruction.utteranceId.toString(), 0.45f)
        else ttsObserver.onSpokenInstructionTrigger(spokenInstruction)
      }
    }
  }

  /**
   * Speaks [text] at [gain] on the navigation channel, ducking music while it talks, the way
   * Ferrostar does (its listener is the voice's only one, so the duck ends once it stops talking).
   */
  fun say(text: String, id: String = "overworld-${System.nanoTime()}", gain: Float = 1f) {
    startVoice()
    val tts = ttsObserver.tts ?: return
    val attributes =
        android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    val audio = appContext.getSystemService(android.media.AudioManager::class.java)
    val focus = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
    audio?.requestAudioFocus(focus)
    tts.speak(text, TextToSpeech.QUEUE_ADD, android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, gain) }, id)
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    handler.postDelayed(
        object : Runnable {
          override fun run() {
            if (tts.isSpeaking) handler.postDelayed(this, 300) else audio?.abandonAudioFocusRequest(focus)
          }
        },
        700,
    )
  }

  /** Settings' "Play test sound": the camera beep, then a turn in the voice, at the chosen volume. */
  fun testSound() {
    com.thealgothrim.overworld.traffic.CameraChime.play(appContext)
    val gain = if (prefs.volume.value == com.thealgothrim.overworld.prefs.GuidanceVolume.SOFTER) 0.45f else 1f
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ say("In 300 metres, turn left.", gain = gain) }, 600)
  }

  /** Starts the voice engine once; later calls (every phone or car screen start) do nothing. */
  fun startVoice() {
    if (ttsObserver.tts == null) ttsObserver.start()
  }

  /** Indian English when the phone has it, otherwise the default English voice. */
  private object TtsVoice : AndroidTtsStatusListener {
    override fun onTtsInitialized(tts: TextToSpeech?, status: Int) {
      tts?.language = Locale.Builder().setLanguage("en").setRegion("IN").build()
    }

    override fun onTtsSpeakError(utteranceId: String, status: Int) {
      Log.e("Overworld", "TTS error $status for $utteranceId")
    }

    override fun onTtsShutdownAndRelease() {}
  }

  val themeStore: ThemeStore by lazy { ThemeStore(appContext) }

  /** The launcher icon: the world's, or one picked in Settings > Display > App icon. */
  val appIcons: AppIcons by lazy { AppIcons(appContext) }

  val search: PlaceSearch by lazy {
    PlaceSearch(httpClient, BuildConfig.TOMTOM_KEY, google = { q, near -> if (themeStore.useGoogle.value) routes.google.search(q, near) else emptyList() })
  }

  val saved: SavedPlaces by lazy { SavedPlaces(appContext) }

  val traffic: TrafficService by lazy { TrafficService(httpClient).also { t -> t.google = { routes.routeTraffic(it) } } }

  /** The map's own road tiles along a route: flyovers, underpasses and what runs beside them (see RoadLevels). */
  val tiles: VectorTiles by lazy { VectorTiles(httpClient, appContext.cacheDir) }

  val cameras: MyCameras by lazy { MyCameras(appContext) }

  val viewModel: OverworldViewModel by lazy { OverworldViewModel() }
}
