package com.thealgothrim.overworld

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.stadiamaps.ferrostar.core.AndroidTtsStatusListener
import com.thealgothrim.overworld.ui.PhoneScreen
import java.util.Locale
import uniffi.ferrostar.createFerrostarLogger

class MainActivity : ComponentActivity(), AndroidTtsStatusListener {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppModule.init(this)
    AppModule.ttsObserver.statusObserver = this
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
    AppModule.ttsObserver.start()
  }

  override fun onDestroy() {
    super.onDestroy()
    AppModule.ttsObserver.shutdown()
  }

  override fun onTtsInitialized(tts: TextToSpeech?, status: Int) {
    // Indian English voice when the phone has one, otherwise the default English voice.
    tts?.language = Locale.Builder().setLanguage("en").setRegion("IN").build()
  }

  override fun onTtsSpeakError(utteranceId: String, status: Int) {
    Log.e("Overworld", "TTS error $status for $utteranceId")
  }

  override fun onTtsShutdownAndRelease() {}
}
