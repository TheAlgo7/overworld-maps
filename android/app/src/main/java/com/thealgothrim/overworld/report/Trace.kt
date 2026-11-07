package com.thealgothrim.overworld.report

import android.location.Location
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.location.Barometer
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import uniffi.ferrostar.GeographicCoordinate

/**
 * The drive as it happened, second by second, kept on the phone for a problem report (see
 * ProblemReport): every GPS fix as it came (with its accuracy, speed, heading and height, the
 * barometer's height, and what the GPS gate did with it), where the trip put the car each second,
 * the routes, and moments marked from the car screen. The 7 Nov drive left only trip times every
 * 2.5 minutes at 100 m, which couldn't show where the arrow went wrong. Rows:
 *
 *   G,time,lat,lng,accuracy_m,speed_kmh,bearing,gps_alt_m,baro_m,gate
 *   T,time,lat,lng,along_m,off_route,to_turn_m,turn,flyover_cue
 *   R,time,route as a 6-digit polyline
 *   M,time,lat,lng,note
 *
 * About 4 MB at most (the last few hours of driving), then the older half goes. Never sent anywhere
 * by the app itself: only attached to a report the driver sends.
 */
object Trace {
  private val writer = Executors.newSingleThreadExecutor()
  private val pending = StringBuilder()
  private var lastFixNanos = 0L
  private var lastFlushMs = 0L
  private val stamp = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.S").withZone(ZoneId.systemDefault())

  private fun file(): File? = AppModule.context.getExternalFilesDir(null)?.let { File(it, FILE) }

  /** A GPS fix as it came ([raw]) and the gate's word on it, if it changed or doubted it. */
  fun fix(raw: Location, gate: String?) {
    synchronized(this) {
      // The trip and the free-drive map each collect the same fixes.
      if (raw.elapsedRealtimeNanos == lastFixNanos) return
      lastFixNanos = raw.elapsedRealtimeNanos
    }
    add(
        "G,${stamp.format(Instant.ofEpochMilli(raw.time))},${"%.6f".format(Locale.US, raw.latitude)},${"%.6f".format(Locale.US, raw.longitude)}," +
            "${if (raw.hasAccuracy()) "%.1f".format(Locale.US, raw.accuracy) else ""},${if (raw.hasSpeed()) "%.1f".format(Locale.US, raw.speed * 3.6f) else ""}," +
            "${if (raw.hasBearing()) "%.0f".format(Locale.US, raw.bearing) else ""},${if (raw.hasAltitude()) "%.1f".format(Locale.US, raw.altitude) else ""}," +
            "${Barometer.heightNow()?.let { "%.1f".format(Locale.US, it) } ?: ""},${gate?.let(::field) ?: ""}"
    )
  }

  /** Where the trip put the car (the route-snapped point), how far along, and what the card said. */
  fun trip(at: GeographicCoordinate, along: Double?, offRoute: Boolean, toTurn: Double?, turn: String?, cue: String?) {
    add(
        "T,${stamp.format(Instant.now())},${"%.6f".format(Locale.US, at.lat)},${"%.6f".format(Locale.US, at.lng)},${along?.let { "%.0f".format(Locale.US, it) } ?: ""}," +
            "${if (offRoute) 1 else 0},${toTurn?.let { "%.0f".format(Locale.US, it) } ?: ""},${turn?.let(::field) ?: ""},${cue?.let(::field) ?: ""}"
    )
  }

  /** A new route (a trip, a reroute), as a polyline. */
  fun route(encoded: String) = add("R,${stamp.format(Instant.now())},$encoded")

  /** A moment the driver marked. */
  fun mark(at: GeographicCoordinate?, note: String) =
      add("M,${stamp.format(Instant.now())},${at?.let { "%.6f".format(Locale.US, it.lat) } ?: ""},${at?.let { "%.6f".format(Locale.US, it.lng) } ?: ""},${field(note)}")

  /** Everything recorded so far, written out (for a report). */
  fun flushNow() {
    val text = synchronized(this) { pending.toString().also { pending.setLength(0) } }
    writer.submit { write(text) }.get()
  }

  /** The track file, after [flushNow]; null when there's none. */
  fun current(): File? = file()?.takeIf { it.exists() && it.length() > 0 }

  private fun add(line: String) {
    val now = System.currentTimeMillis()
    val text =
        synchronized(this) {
          pending.append(line).append('\n')
          if (pending.length < FLUSH_CHARS && now - lastFlushMs < FLUSH_MS) return
          lastFlushMs = now
          pending.toString().also { pending.setLength(0) }
        }
    writer.execute { write(text) }
  }

  private fun write(text: String) {
    if (text.isEmpty()) return
    runCatching {
      val f = file() ?: return
      if (!f.exists()) f.writeText(HEADER)
      if (f.length() > MAX_BYTES) {
        val keep = f.readText().takeLast((MAX_BYTES / 2).toInt()).substringAfter('\n')
        f.writeText(HEADER + keep)
      }
      f.appendText(text)
    }
  }

  /** A CSV field: commas and quotes kept inside quotes. */
  private fun field(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"").replace('\n', ' ') + "\"" else s

  private const val FILE = "track.csv"
  private const val MAX_BYTES = 4_000_000L
  private const val FLUSH_CHARS = 8_000
  private const val FLUSH_MS = 10_000L
  private const val HEADER =
      "# G,time,lat,lng,accuracy_m,speed_kmh,bearing,gps_alt_m,baro_m,gate | T,time,lat,lng,along_m,off_route,to_turn_m,turn,flyover_cue | R,time,polyline6 | M,time,lat,lng,note\n"
}
