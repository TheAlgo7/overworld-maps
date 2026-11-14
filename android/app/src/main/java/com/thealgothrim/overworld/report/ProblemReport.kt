package com.thealgothrim.overworld.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.BuildConfig
import com.thealgothrim.overworld.DriveLog
import com.thealgothrim.overworld.location.Barometer
import com.thealgothrim.overworld.map.FrameStats
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Report a problem": everything needed to see what went wrong on a drive, sent to the developer by
 * the tester's own email app. Nothing leaves the phone until they press Send there, and they can take
 * the track (where they drove) off the email first. Gaurav asked for it after the 7 Nov drive, where
 * all the app had kept was trip times every 2.5 minutes, "for us to learn and debug the app for
 * others too".
 *
 * The email carries: report.txt (the phone, the app and every setting, the car's screens as Android
 * Auto described them, the trip, marked moments, how smoothly the maps drew), drive-log.txt (the
 * app's diary of drives: routes, reroutes, what the car reported) and track.csv (see Trace).
 */
object ProblemReport {
  /** The car's screens as Android Auto handed them over, for the report (set by the car screens). */
  @Volatile var carScreen: String? = null
  @Volatile var clusterScreen: String? = null
  @Volatile var carApi: String? = null

  private val marks = ArrayDeque<String>()

  /** Marks this moment for the next report (the car's "Mark this moment"): where, when, what showed. */
  fun mark(where: String) {
    val vm = AppModule.viewModel
    val state = vm.navigationUiState.value
    val at = state.location?.coordinates
    val turn = state.visualInstruction?.primaryContent?.text
    val note =
        "marked on the $where at ${at?.let { "%.5f,%.5f".format(Locale.US, it.lat, it.lng) } ?: "no fix"}" +
            (if (state.isNavigating()) ", trip to ${state.destination ?: "?"}, next turn \"${turn ?: "?"}\" in ${state.progress?.distanceToNextManeuver?.toInt()} m" else ", no trip")
    synchronized(marks) {
      marks.addLast("${DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").format(ZonedDateTime.now())}  $note")
      while (marks.size > MAX_MARKS) marks.removeFirst()
    }
    DriveLog.note(note)
    Trace.mark(at, note)
  }

  /**
   * Writes the report's files and opens the email app with them attached, to [EMAIL]. [note] is put
   * at the top of the email for the tester to add to. Main thread (it starts an activity); the
   * writing is small and quick.
   */
  fun send(context: Context, note: String = "") {
    val files = write(context)
    val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.reports", it) }
    val intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
          putExtra(Intent.EXTRA_SUBJECT, "Overworld ${BuildConfig.VERSION_NAME}: problem report")
          putExtra(
              Intent.EXTRA_TEXT,
              (if (note.isNotBlank()) "$note\n\n" else "") +
                  "What went wrong, and roughly when and where:\n\n\n\n" +
                  "Sent from Overworld's Report a problem. track.csv is where you drove in the last few hours; " +
                  "remove it from this email if you'd rather not share that.",
          )
          putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
          // Read access for whichever email app is picked, through the chooser too.
          clipData = ClipData.newRawUri("report", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooser = Intent.createChooser(intent, "Send the report by email").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
  }

  /** The report's files in the app's cache, newest copies of the drive log and track. */
  fun write(context: Context): List<File> {
    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val out = ArrayList<File>()
    out += File(dir, "report.txt").apply { writeText(summary(context)) }
    context.getExternalFilesDir(null)?.let { File(it, "drive-log.txt") }?.takeIf { it.exists() }?.let { log ->
      out += File(dir, "drive-log.txt").apply { writeBytes(log.readBytes()) }
    }
    runCatching { Trace.flushNow() }
    Trace.current()?.let { track -> out += File(dir, "track.csv").apply { writeBytes(track.readBytes()) } }
    return out
  }

  private fun summary(context: Context): String {
    val vm = AppModule.viewModel
    val store = AppModule.themeStore
    val state = vm.navigationUiState.value
    val extras = vm.extras.value
    val installer =
        runCatching {
              if (Build.VERSION.SDK_INT >= 30) context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
              else @Suppress("DEPRECATION") context.packageManager.getInstallerPackageName(context.packageName)
            }
            .getOrNull()
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val details = store.details.value
    val carDetails = store.carDetails.value
    return buildString {
      appendLine("Overworld problem report")
      appendLine("Made ${DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss z").format(ZonedDateTime.now())}")
      appendLine()
      appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.BUILD_TYPE}, ${context.packageName}, installed by ${installer ?: "sideload"}")
      appendLine("TomTom calls from this phone, ${com.thealgothrim.overworld.traffic.TomTomUsage.summary()}")
      appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
      appendLine("Location: fine ${granted(android.Manifest.permission.ACCESS_FINE_LOCATION)}, coarse ${granted(android.Manifest.permission.ACCESS_COARSE_LOCATION)}; barometer ${Barometer.present ?: "not asked yet"}")
      appendLine()
      appendLine("Car: Car App API ${carApi ?: "?"}, screen ${carScreen ?: "never connected"}, cluster ${clusterScreen ?: "none"}, says ${when (vm.carDarkNow) { true -> "night"; false -> "day"; null -> "nothing" }}")
      appendLine("Car theme ${store.carTheme.value.id}, time of day ${store.carMapTime.value}, palette now ${vm.carMapVariant.value ?: "day"}, game HUD ${store.carGameHud.value}, Overworld's own lists ${store.carThemedLists.value}")
      appendLine("Car details: place icons ${carDetails.placeIcons}, 3D ${carDetails.buildings3d}, traffic everywhere ${carDetails.traffic}, lights ${carDetails.signals}, incidents ${carDetails.incidents}, + Cam ${store.carCamButton.value}")
      appendLine("Phone theme ${store.theme.value.id}, time of day ${store.mapTime.value}; details: place icons ${details.placeIcons}, 3D ${details.buildings3d}, traffic ${details.traffic}, lights ${details.signals}, incidents ${details.incidents}")
      appendLine("Google routes ${store.useGoogle.value}, voice ${!AppModule.ttsObserver.isMuted}, camera beep ${vm.cameraBeep.value}, live traffic ${AppModule.traffic.hasLiveTraffic}, routes last from ${AppModule.routes.lastSource}")
      appendLine()
      if (state.isNavigating()) {
        val off = if (state.routeDeviation is uniffi.ferrostar.RouteDeviation.Deviation) "yes" else "no"
        appendLine("Trip now: to ${state.destination ?: "?"}, ${state.progress?.distanceRemaining?.toInt()} m left, next \"${state.visualInstruction?.primaryContent?.text}\" in ${state.progress?.distanceToNextManeuver?.toInt()} m, off route: $off")
        state.location?.let { appendLine("At ${"%.5f,%.5f".format(Locale.US, it.coordinates.lat, it.coordinates.lng)} ±${it.horizontalAccuracy.toInt()} m") }
        extras.levels?.let { lv ->
          appendLine("Flyovers on this route: ${lv.cues.size} choices, ${lv.structures.size} on it, ${lv.parallels.size} roads beside it")
          lv.cues.forEach { c -> appendLine("  ${c.kind.text} at ${"%.2f".format(Locale.US, c.at / 1000)} km (structure at ${"%.2f".format(Locale.US, c.structureAt / 1000)} km)") }
        }
      } else appendLine("No trip running.")
      appendLine()
      val marked = synchronized(marks) { marks.toList() }
      appendLine("Marked moments: ${if (marked.isEmpty()) "none (they also stay in drive-log.txt)" else ""}")
      marked.forEach { appendLine("  $it") }
      appendLine()
      val frames = FrameStats.recentLines()
      appendLine("Map smoothness, a line a minute (also in drive-log.txt): ${if (frames.isEmpty()) "none recorded since the app started" else ""}")
      frames.takeLast(30).forEach { appendLine("  $it") }
    }
  }

  /** Overworld's public contact address. */
  const val EMAIL = "gaurav@thealgothrim.com"
  private const val MAX_MARKS = 30
}
