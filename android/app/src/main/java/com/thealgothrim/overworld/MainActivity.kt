package com.thealgothrim.overworld

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.thealgothrim.overworld.ui.PhoneScreen
import uniffi.ferrostar.createFerrostarLogger

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppModule.init(this)
    AppModule.ferrostarCore.spokenInstructionObserver = AppModule.ttsObserver
    createFerrostarLogger()

    setContent {
      val theme by AppModule.themeStore.theme.collectAsState()
      LaunchedEffect(theme.dark) {
        val bars =
            if (theme.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
      }

      val permissions =
          rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            AppModule.viewModel.setLocationPermission(
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            )
          }
      LaunchedEffect(Unit) {
        val wanted =
            buildList {
              add(Manifest.permission.ACCESS_FINE_LOCATION)
              add(Manifest.permission.ACCESS_COARSE_LOCATION)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            }
        permissions.launch(wanted.toTypedArray())
      }

      PhoneScreen()
    }
  }

  override fun onStart() {
    super.onStart()
    // Voice prompts are shared with the car and live as long as the app (see AppModule), so they
    // are started here but never shut down with this screen.
    AppModule.startVoice()
  }
}
