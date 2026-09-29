package com.thealgothrim.overworld.ui

import android.app.Activity
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

/**
 * Runs [onReady] when the phone's location is on. When it is off, shows Android's own "Turn on
 * location" dialog first (the one Google Maps shows from its locate button) and runs [onReady]
 * once it is turned on.
 */
@Composable
fun rememberWithLocationOn(onReady: () -> Unit): () -> Unit {
  val context = LocalContext.current
  val ready by rememberUpdatedState(onReady)
  val dialog =
      rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) ready()
      }
  return remember(context) {
    {
      val manager = context.getSystemService(LocationManager::class.java)
      if (manager != null && LocationManagerCompat.isLocationEnabled(manager)) {
        ready()
      } else {
        val request =
            LocationSettingsRequest.Builder()
                .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build())
                .setAlwaysShow(true)
                .build()
        LocationServices.getSettingsClient(context)
            .checkLocationSettings(request)
            .addOnSuccessListener { ready() }
            .addOnFailureListener { e ->
              if (e is ResolvableApiException) {
                dialog.launch(IntentSenderRequest.Builder(e.resolution).build())
              } else {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
              }
            }
      }
    }
  }
}
