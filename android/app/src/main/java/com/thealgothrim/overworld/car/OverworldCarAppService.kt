package com.thealgothrim.overworld.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.thealgothrim.overworld.AppModule
import uniffi.ferrostar.GeographicCoordinate

/** Android Auto entry point. Declared in the manifest under the NAVIGATION category. */
class OverworldCarAppService : CarAppService() {
  // Personal app: accept any Android Auto host (the phone's own Android Auto, or the DHU).
  override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

  override fun onCreateSession(sessionInfo: SessionInfo): Session = OverworldCarSession()
}

class OverworldCarSession : Session() {
  override fun onCreateScreen(intent: Intent): Screen {
    AppModule.init(carContext)
    // Voice prompts must work even if the phone app was never opened this session.
    AppModule.startVoice()
    AppModule.ferrostarCore.spokenInstructionObserver = AppModule.ttsObserver
    AppModule.viewModel.refreshLocationPermission()
    navigateTo(intent)
    return CarNavigationScreen(carContext)
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
