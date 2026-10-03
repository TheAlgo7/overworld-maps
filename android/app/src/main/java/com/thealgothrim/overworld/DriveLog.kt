package com.thealgothrim.overworld

import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import uniffi.ferrostar.GeographicCoordinate

/**
 * A short diary of each drive on the phone itself: where trips started and how long the routing
 * server and TomTom said they would take, every time-to-go refresh, searches and Nearby lists. After
 * a drive where something looked wrong ("10 min here, 30 in Google Maps") it shows what the app
 * actually had. Kept to about half a megabyte; read it with
 *   adb pull /sdcard/Android/data/com.thealgothrim.overworld/files/drive-log.txt
 * Points are rounded to about 100 m.
 */
object DriveLog {
  private val writer = Executors.newSingleThreadExecutor()
  private val time = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

  fun note(message: String) {
    Log.i("DriveLog", message)
    val line = "${LocalDateTime.now().format(time)}  $message\n"
    writer.execute {
      runCatching {
        val file = File(AppModule.context.getExternalFilesDir(null) ?: return@runCatching, "drive-log.txt")
        if (file.length() > MAX_BYTES) file.writeText(file.readText().takeLast(MAX_BYTES.toInt() / 2).substringAfter('\n'))
        file.appendText(line)
      }
    }
  }

  fun at(p: GeographicCoordinate?) = p?.let { "%.3f,%.3f".format(it.lat, it.lng) } ?: "none"

  fun minutes(seconds: Double?) = seconds?.let { "%.1f min".format(it / 60) } ?: "none"

  private const val MAX_BYTES = 512_000L
}
