package com.thealgothrim.overworld

import android.content.Context
import com.stadiamaps.ferrostar.composeui.notification.DefaultForegroundNotificationBuilder
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.CorrectiveAction
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.RouteDeviationHandler
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.stadiamaps.ferrostar.core.location.SimulatedLocationProvider
import com.stadiamaps.ferrostar.core.location.toAndroidLocation
import com.stadiamaps.ferrostar.core.withJsonOptions
import com.stadiamaps.ferrostar.googleplayservices.FusedNavigationLocationProvider
import com.thealgothrim.overworld.search.PlaceSearch
import com.thealgothrim.overworld.search.SavedPlaces
import com.thealgothrim.overworld.theme.GameFonts
import com.thealgothrim.overworld.theme.ThemeStore
import java.time.Duration
import java.time.Instant
import okhttp3.OkHttpClient
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.RouteDeviationTracking
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
  }

  val context: Context
    get() = appContext

  private val defaultStartLocation =
      UserLocation(defaultStart, 6.0, null, Instant.now(), null)

  val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(20))
        .addInterceptor { chain ->
          chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()
  }

  val locationProvider: NavigationLocationProvider by lazy {
    NavigationLocationProvider(
        liveProviding = FusedNavigationLocationProvider(appContext),
        simulatedProvider =
            SimulatedLocationProvider(
                warpFactor = 3u,
                initialLocation = defaultStartLocation.toAndroidLocation(),
            ),
    )
  }

  val ferrostarCore: FerrostarCore by lazy {
    val options = mapOf("units" to "kilometers")
    val core =
        FerrostarCore(
            WellKnownRouteProvider.Valhalla(VALHALLA_URL, "auto").withJsonOptions(options),
            httpClient = httpClient.toOkHttpClientProvider(),
            locationProvider = locationProvider,
            foregroundServiceManager =
                SafeForegroundServiceManager(
                    appContext,
                    DefaultForegroundNotificationBuilder(appContext),
                ),
            navigationControllerConfig =
                NavigationControllerConfig(
                    WaypointAdvanceMode.WaypointWithinRange(100.0),
                    stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
                    stepAdvanceDistanceToEndOfStep(10u, 32u),
                    // Delhi GPS drifts near flyovers; allow 50 m before calling it a detour.
                    RouteDeviationTracking.StaticThreshold(15U, 50.0),
                    CourseFiltering.SNAP_TO_ROUTE,
                ),
        )
    core.deviationHandler = RouteDeviationHandler { _, _, remainingWaypoints ->
      CorrectiveAction.GetNewRoutes(remainingWaypoints)
    }
    core
  }

  val ttsObserver: AndroidTtsObserver by lazy { AndroidTtsObserver(appContext) }

  val themeStore: ThemeStore by lazy { ThemeStore(appContext) }

  val search: PlaceSearch by lazy { PlaceSearch(httpClient) }

  val saved: SavedPlaces by lazy { SavedPlaces(appContext) }

  val viewModel: OverworldViewModel by lazy { OverworldViewModel() }
}
