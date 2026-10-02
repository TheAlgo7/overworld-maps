package com.thealgothrim.overworld

import android.Manifest
import android.content.Intent
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
import com.thealgothrim.overworld.car.navigationDestination
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
    // Not again after the screen is rebuilt (dark mode switched, say): the place is already shown.
    if (savedInstanceState == null) openPlace(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    openPlace(intent)
  }

  /** A place shared in (Google Maps' Share > Overworld) or a geo: link opened with Overworld. */
  private fun openPlace(intent: Intent?) {
    val vm = AppModule.viewModel
    when (intent?.action) {
      Intent.ACTION_SEND -> {
        val text =
            listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
                .flatMap { it.lines() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("\n")
        if (text.isNotBlank()) vm.openShared(text)
      }
      Intent.ACTION_VIEW -> vm.openDestination(intent.navigationDestination() ?: return)
    }
  }

  override fun onStart() {
    super.onStart()
    // Voice prompts are shared with the car and live as long as the app (see AppModule), so they
    // are started here but never shut down with this screen.
    AppModule.startVoice()
    AppModule.viewModel.mapInView(true)
  }

  override fun onStop() {
    super.onStop()
    AppModule.viewModel.mapInView(false)
  }
}
