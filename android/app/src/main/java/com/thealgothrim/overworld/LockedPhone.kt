package com.thealgothrim.overworld

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Driving with the phone locked in a pocket. "While using the app" location stops when the phone
 * locks and nothing of ours is in the foreground (no trip yet, or a trip started from the car
 * while the phone was locked, which Android refuses a location service for), and Samsung's battery
 * saver puts the app to sleep. Both need the driver's yes: location "Allow all the time", and the
 * app's battery use "Unrestricted".
 */
object LockedPhone {
  fun locationAllTheTime(context: Context): Boolean =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

  fun batteryUnrestricted(context: Context): Boolean =
      context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

  fun ready(context: Context): Boolean = locationAllTheTime(context) && batteryUnrestricted(context)

  fun appInfo(context: Context): Intent =
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}

/**
 * Whether the app works with the phone locked, and the next step to get there: location first
 * (Android opens its own page with "Allow all the time"), then the battery prompt. Read again
 * whenever the screen comes back, since both are changed in Android's settings.
 */
@SuppressLint("BatteryLife")
@Composable
fun rememberLockedPhoneSetup(): Pair<Boolean, () -> Unit> {
  val context = LocalContext.current
  var ready by remember { mutableStateOf(LockedPhone.ready(context)) }
  LifecycleResumeEffect(Unit) {
    ready = LockedPhone.ready(context)
    onPauseOrDispose {}
  }
  val battery =
      rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { ready = LockedPhone.ready(context) }
  val location =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Asked and refused before, Android shows nothing: the app's own page has the switch.
        if (!granted) context.startActivity(LockedPhone.appInfo(context))
        ready = LockedPhone.ready(context)
      }
  return ready to {
    when {
      !LockedPhone.locationAllTheTime(context) -> location.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
      !LockedPhone.batteryUnrestricted(context) ->
          battery.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
      // Already set: the app's page, to change it back.
      else -> context.startActivity(LockedPhone.appInfo(context))
    }
  }
}
