package com.thealgothrim.overworld

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.stadiamaps.ferrostar.core.NavigationState
import com.stadiamaps.ferrostar.core.service.FerrostarForegroundService
import com.stadiamaps.ferrostar.core.service.ForegroundNotificationBuilder
import com.stadiamaps.ferrostar.core.service.ForegroundServiceManager

/**
 * Ferrostar's FerrostarForegroundServiceManager (BSD 3-Clause, Stadia Maps) with one change: if
 * Android refuses the location foreground service (Android 12+ does when the app is not in an
 * eligible foreground state, for example a trip started from a car intent with the phone asleep),
 * it logs and carries on instead of crashing the app. Navigation still runs while the phone or
 * the Android Auto screen is showing the app.
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

  private val stopReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          stopNavigating?.invoke()
        }
      }

  @SuppressLint("UnspecifiedRegisterReceiverFlag")
  override fun startService(stopNavigation: () -> Unit) {
    stopService()
    stopNavigating = stopNavigation
    isRequested = true
    notificationBuilder.channelId = CHANNEL_ID

    val intent = Intent(context, FerrostarForegroundService::class.java)
    try {
      context.startForegroundService(intent)
      context.registerReceiver(
          stopReceiver,
          IntentFilter(ForegroundNotificationBuilder.STOP_NAVIGATION_INTENT),
          Context.RECEIVER_EXPORTED,
      )
      receiverRegistered = true
      context.bindService(intent, this, Context.BIND_AUTO_CREATE)
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
    runCatching { context.unbindService(this) }
    service?.stop()
    service = null
    context.stopService(Intent(context, FerrostarForegroundService::class.java))
  }

  override fun onNavigationStateUpdated(state: NavigationState) {
    if (!isRequested) return
    service?.onNavigationStateUpdated(state)
  }

  override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
    val connected = (binder as? FerrostarForegroundService.LocalBinder)?.service ?: return
    if (!isRequested) return
    connected.notificationBuilder = notificationBuilder
    try {
      connected.start()
      service = connected
    } catch (e: Exception) {
      Log.w(TAG, "Could not promote the location service to foreground", e)
      runCatching { context.unbindService(this) }
      context.stopService(Intent(context, FerrostarForegroundService::class.java))
    }
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
