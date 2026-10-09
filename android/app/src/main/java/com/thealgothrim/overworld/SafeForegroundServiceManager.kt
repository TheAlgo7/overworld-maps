package com.thealgothrim.overworld

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.stadiamaps.ferrostar.core.NavigationState
import com.stadiamaps.ferrostar.core.service.FerrostarForegroundService
import com.stadiamaps.ferrostar.core.service.ForegroundNotificationBuilder
import com.stadiamaps.ferrostar.core.service.ForegroundServiceManager

/**
 * Ferrostar's FerrostarForegroundServiceManager (BSD 3-Clause, Stadia Maps) with two changes:
 * - If Android refuses the location foreground service (Android 12+ does when the app is not in an
 *   eligible foreground state, for example a trip started from a car intent with the phone asleep),
 *   it logs and carries on instead of crashing the app. Navigation still runs while the phone or
 *   the Android Auto screen is showing the app.
 * - A trip ended while the service is still starting no longer crashes the app. The service only
 *   calls startForeground() once it is bound, and Android kills an app whose
 *   startForegroundService() service goes away before that. So the stop waits for the binding,
 *   promotes the service, then stops it.
 */
class SafeForegroundServiceManager(
    context: Context,
    private val notificationBuilder: ForegroundNotificationBuilder,
) : ForegroundServiceManager, ServiceConnection {

  private val context = context.applicationContext
  private var isRequested = false
  private var receiverRegistered = false
  private var service: FerrostarForegroundService? = null
  private var stopNavigating: (() -> Unit)? = null
  /** startForegroundService() went through and the service has not been stopped since. */
  private var started = false
  /** Stopped before the service was bound: stop it as soon as it has been promoted. */
  private var stopWhenConnected = false

  private val stopReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          stopNavigating?.invoke()
        }
      }

  override fun startService(stopNavigation: () -> Unit) {
    stopService()
    stopNavigating = stopNavigation
    isRequested = true
    // A new trip while the last one's stop is still waiting: keep the service it is starting.
    stopWhenConnected = false
    notificationBuilder.channelId = CHANNEL_ID

    val intent = Intent(context, FerrostarForegroundService::class.java)
    try {
      // Every app built on Ferrostar sends the same Stop action, so any of them (or the other build
      // of this app, side by side on the phone) could end this trip, and so could any app at all.
      // Now only a sender holding END_TRIP can: this app's own notification. It has to stay
      // exported: Ferrostar's Stop intent names no app, and Android 14 delivers such a broadcast to
      // exported receivers only (not exported, the notification's Stop did nothing).
      ContextCompat.registerReceiver(
          context,
          stopReceiver,
          IntentFilter(ForegroundNotificationBuilder.STOP_NAVIGATION_INTENT),
          "${context.packageName}.permission.END_TRIP",
          null,
          ContextCompat.RECEIVER_EXPORTED,
      )
      receiverRegistered = true
      if (!started) {
        context.startForegroundService(intent)
        started = true
        context.bindService(intent, this, Context.BIND_AUTO_CREATE)
      } else {
        // Still bound from the trip that was stopped too early: the pending connection promotes it.
        service?.let { promote(it) }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Location service refused; navigating without it", e)
    }
  }

  override fun stopService() {
    if (!isRequested) return
    isRequested = false
    if (receiverRegistered) {
      runCatching { context.unregisterReceiver(stopReceiver) }
      receiverRegistered = false
    }
    if (started && service == null) {
      stopWhenConnected = true
      return
    }
    tearDown()
  }

  private fun tearDown() {
    runCatching { context.unbindService(this) }
    service?.stop()
    service = null
    started = false
    stopWhenConnected = false
    context.stopService(Intent(context, FerrostarForegroundService::class.java))
  }

  override fun onNavigationStateUpdated(state: NavigationState) {
    if (!isRequested) return
    service?.onNavigationStateUpdated(state)
  }

  override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
    val connected = (binder as? FerrostarForegroundService.LocalBinder)?.service ?: return
    if (!started) return
    connected.notificationBuilder = notificationBuilder
    if (!promote(connected)) return
    if (stopWhenConnected) tearDown()
  }

  /** startForeground(), which Android requires of a service started with startForegroundService(). */
  private fun promote(connected: FerrostarForegroundService): Boolean =
      try {
        connected.start()
        service = connected
        true
      } catch (e: Exception) {
        Log.w(TAG, "Could not promote the location service to foreground", e)
        tearDown()
        false
      }

  override fun onServiceDisconnected(name: ComponentName?) {
    service?.stop()
    service = null
  }

  companion object {
    private const val TAG = "SafeForegroundService"
    private const val CHANNEL_ID = "ferrostar_navigation"
  }
}
