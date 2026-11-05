package com.thealgothrim.overworld.car

import android.content.Intent
import android.content.res.Configuration
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.thealgothrim.overworld.AppModule
import uniffi.ferrostar.GeographicCoordinate

/**
 * Android Auto entry point. Declared in the manifest under the NAVIGATION category, and
 * FEATURE_CLUSTER for the map between the dials in cars with an instrument cluster display.
 */
class OverworldCarAppService : CarAppService() {
  // Personal app: accept any Android Auto host (the phone's own Android Auto, or the DHU).
  override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

  override fun onCreateSession(sessionInfo: SessionInfo): Session =
      if (sessionInfo.displayType == SessionInfo.DISPLAY_TYPE_CLUSTER) OverworldClusterSession() else OverworldCarSession()
}

class OverworldCarSession : Session() {
  init {
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
          // Car gone: Auto on the car's map goes back to following the sun until the next one reports.
          override fun onDestroy(owner: LifecycleOwner) = AppModule.viewModel.setCarDark(null)
        }
    )
  }

  override fun onCreateScreen(intent: Intent): Screen {
    AppModule.init(carContext)
    // Voice prompts must work even if the phone app was never opened this session.
    AppModule.startVoice()
    AppModule.ferrostarCore.spokenInstructionObserver = AppModule.spokenInstructions
    AppModule.viewModel.refreshLocationPermission()
    AppModule.viewModel.setCarDark(carContext.isDarkMode)
    navigateTo(intent)
    val map = CarNavigationScreen(carContext)
    if (AppModule.viewModel.locationAllowed.value) return map
    // Opened in the car before location was ever allowed (a fresh install): say so on the car
    // screen, over the map, which waits underneath (see CarLocationScreen).
    carContext.getCarService(ScreenManager::class.java).push(map)
    return CarLocationScreen(carContext)
  }

  /** The car switched between day and night (dusk, a tunnel, headlights): the map follows. */
  override fun onCarConfigurationChanged(newConfiguration: Configuration) {
    AppModule.viewModel.setCarDark(carContext.isDarkMode)
  }

  /**
   * A navigation request while the app is already open, like "Hey Google, navigate to India Gate".
   * Before this it was dropped: only the request that opened the app was followed.
   */
  override fun onNewIntent(intent: Intent) {
    if (navigateTo(intent)) carContext.getCarService(ScreenManager::class.java).popToRoot()
  }

  /** Starts a trip to the place in a navigation intent (a geo: or google.navigation: link). */
  private fun navigateTo(intent: Intent): Boolean {
    val destination = intent.navigationDestination() ?: return false
    val at = destination.location
    val query = destination.query?.takeIf { it.isNotBlank() }
    when {
      at != null -> AppModule.viewModel.startNavigation(GeographicCoordinate(at.latitude, at.longitude), query)
      // A place by name only ("geo:0,0?q=India Gate"): look it up like the phone's search does.
      query != null -> AppModule.viewModel.navigateToQuery(query)
      else -> return false
    }
    return true
  }
}
