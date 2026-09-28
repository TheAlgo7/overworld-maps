package com.thealgothrim.overworld.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.stadiamaps.ferrostar.car.app.intent.NavigationIntentParser
import com.thealgothrim.overworld.AppModule

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
    AppModule.ttsObserver.start()
    AppModule.ferrostarCore.spokenInstructionObserver = AppModule.ttsObserver
    AppModule.viewModel.refreshLocationPermission()
    val destination = NavigationIntentParser().parse(intent)
    return CarNavigationScreen(carContext, initialDestination = destination)
  }
}
