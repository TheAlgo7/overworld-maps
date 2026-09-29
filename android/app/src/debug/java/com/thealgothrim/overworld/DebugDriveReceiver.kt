package com.thealgothrim.overworld

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import uniffi.ferrostar.GeographicCoordinate

/**
 * Debug builds only: start or stop a simulated trip from the laptop, so the Android Auto screen can
 * be tested in the Desktop Head Unit without touching (or unlocking) the phone.
 *
 *   adb shell am broadcast -n com.thealgothrim.overworld/.DebugDriveReceiver \
 *     -a com.thealgothrim.overworld.DEBUG_DRIVE --ef lat 28.6129 --ef lng 77.2295 --es name "India Gate"
 *   adb shell am broadcast -n com.thealgothrim.overworld/.DebugDriveReceiver \
 *     -a com.thealgothrim.overworld.DEBUG_DRIVE --es theme rdr2
 *   adb shell am broadcast -n com.thealgothrim.overworld/.DebugDriveReceiver \
 *     -a com.thealgothrim.overworld.DEBUG_STOP
 */
class DebugDriveReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    AppModule.init(context)
    val vm = AppModule.viewModel
    when (intent.action) {
      "com.thealgothrim.overworld.DEBUG_STOP" -> vm.stopNavigation()
      "com.thealgothrim.overworld.DEBUG_DRIVE" -> {
        intent.getStringExtra("theme")?.let { AppModule.themeStore.select(it) }
        if (intent.hasExtra("lat") && intent.hasExtra("lng")) {
          val to = GeographicCoordinate(intent.getFloatExtra("lat", 0f).toDouble(), intent.getFloatExtra("lng", 0f).toDouble())
          vm.setTestDrive(true)
          // "%s" for spaces, the same convention as `adb shell input text`.
          // Optional start ("from_lat"/"from_lng"), so a drive can begin anywhere, not where the phone is.
          val from =
              if (intent.hasExtra("from_lat") && intent.hasExtra("from_lng"))
                  GeographicCoordinate(intent.getFloatExtra("from_lat", 0f).toDouble(), intent.getFloatExtra("from_lng", 0f).toDouble())
              else null
          vm.startNavigation(to, intent.getStringExtra("name")?.replace("%s", " "), from)
          Log.i("DebugDrive", "test drive to $to")
        }
      }
    }
  }
}
