package com.thealgothrim.overworld.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.MainActivity
import com.thealgothrim.overworld.R
import com.thealgothrim.overworld.ui.formatDistance
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Overworld's own notifications, each kind on its own Android channel and Settings > Alerts switch:
 * the parked car's time limit, last month's Timeline recap, an offline map finished downloading.
 * The trip's own notification is Ferrostar's and stays while driving.
 */
object Notices {
  const val PARKING = "parking"
  const val RECAP = "timeline"
  const val OFFLINE = "offline"

  private const val PARKING_SOON_ID = 4101
  private const val PARKING_DONE_ID = 4102
  private const val RECAP_ID = 4201
  private const val OFFLINE_ID = 4301

  fun channels(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java) ?: return
    nm.createNotificationChannel(NotificationChannel(PARKING, "Parking reminders", NotificationManager.IMPORTANCE_HIGH).apply { description = "When the time on your parked car is almost up" })
    nm.createNotificationChannel(NotificationChannel(RECAP, "Timeline recap", NotificationManager.IMPORTANCE_LOW).apply { description = "Your month in Overworld, once a month" })
    nm.createNotificationChannel(NotificationChannel(OFFLINE, "Offline maps", NotificationManager.IMPORTANCE_LOW).apply { description = "When a map you downloaded is ready" })
  }

  /** Whether Android will show them at all (the permission on Android 13+, and the app's switch). */
  fun allowed(context: Context): Boolean =
      NotificationManagerCompat.from(context).areNotificationsEnabled() &&
          (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

  private fun open(context: Context, request: Int): PendingIntent =
      PendingIntent.getActivity(
          context,
          request,
          Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

  @android.annotation.SuppressLint("MissingPermission") // allowed() checks it
  private fun post(context: Context, id: Int, channel: String, title: String, text: String) {
    if (!allowed(context)) return
    channels(context)
    val n =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_navigation)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(context, id))
            .setAutoCancel(true)
            .build()
    NotificationManagerCompat.from(context).notify(id, n)
  }

  fun recap(context: Context, month: YearMonth, metres: Double, trips: Int, places: Int) {
    val name = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    post(context, RECAP_ID, RECAP, "Your $name in Overworld", "${formatDistance(metres)} over $trips trip${if (trips == 1) "" else "s"}, $places place${if (places == 1) "" else "s"}. See it in You > Timeline.")
  }

  fun offlineReady(context: Context, area: String, megabytes: Long) {
    if (!AppModule.prefs.notifyOffline.value) return
    post(context, OFFLINE_ID, OFFLINE, "Offline map ready", "$area ($megabytes MB) now draws without internet.")
  }

  /** Reminders for the parked car's time limit: 10 minutes before, and when it runs out. */
  fun scheduleParking(context: Context, until: Long?) {
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return
    fun intent(final: Boolean) =
        PendingIntent.getBroadcast(
            context,
            if (final) PARKING_DONE_ID else PARKING_SOON_ID,
            Intent(context, ParkingAlarmReceiver::class.java).putExtra("final", final),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    alarms.cancel(intent(false))
    alarms.cancel(intent(true))
    if (until == null || !AppModule.prefs.notifyParking.value) return
    val soon = until - 10 * 60_000L
    // Inexact but allowed while dozing: within a few minutes, without the exact-alarm permission.
    if (soon > System.currentTimeMillis()) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, soon, intent(false))
    if (until > System.currentTimeMillis()) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, intent(true))
  }

  fun parkingAlert(context: Context, final: Boolean) {
    val spot = AppModule.saved.state.value.parking ?: return
    val where = spot.near?.let { " near $it" }.orEmpty()
    if (final) post(context, PARKING_DONE_ID, PARKING, "Parking: time's up", "The time on your car$where has run out.")
    else post(context, PARKING_SOON_ID, PARKING, "Parking: 10 minutes left", "The time on your car$where is almost up.")
  }
}

/** Fires the parked car's reminders (see [Notices.scheduleParking]). */
class ParkingAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    AppModule.init(context)
    Notices.parkingAlert(context, intent.getBooleanExtra("final", false))
  }
}
