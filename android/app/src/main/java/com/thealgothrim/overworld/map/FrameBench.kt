package com.thealgothrim.overworld.map

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.thealgothrim.overworld.ui.DebugUi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

/**
 * A scripted pan over one spot for measuring how smoothly the phone map draws, started from adb
 * in a debug build (DebugDriveReceiver --es bench "lat,lng,zoom,tilt,seconds"). The same run over
 * the same streets lets two builds or two settings be compared like for like, without asking
 * anyone to scroll. Writes one FrameStats line to logcat under DebugDrive.
 */
@Composable
fun FrameBenchEffect(mapState: NavigationMapState) {
  // Collected rather than keyed on: clearing the request would otherwise cancel the run it started.
  LaunchedEffect(mapState) { DebugUi.bench.collect { b -> if (b != null) { DebugUi.bench.value = null; benchRun(mapState, b) } } }
}

private suspend fun benchRun(mapState: NavigationMapState, b: DoubleArray) {
  val lat = b[0]
  val lng = b[1]
  val zoom = b.getOrElse(2) { 17.0 }
  val tilt = b.getOrElse(3) { 0.0 }
  val seconds = b.getOrElse(4) { 12.0 }
  mapState.cameraMode = NavigationCameraMode.FREE
  val camera = mapState.cameraState
  camera.position = CameraPosition(target = Position(lng, lat), zoom = zoom, tilt = tilt, bearing = 0.0)
  // Tiles load and the first labels settle before counting.
  delay(4_000)
  FrameStats.summary()
  // A circle 250 m round the spot, a finger's steady drag: about a screen every two seconds at
  // zoom 17.5.
  val steps = (seconds / 0.75).toInt().coerceAtLeast(4)
  val started = android.os.SystemClock.uptimeMillis()
  for (i in 1..steps) {
    val a = 2 * PI * i / 8
    val dLat = 250.0 * cos(a) / 111_320.0
    val dLng = 250.0 * sin(a) / (111_320.0 * cos(Math.toRadians(lat)))
    camera.animateTo(camera.position.copy(target = Position(lng + dLng, lat + dLat)), duration = 750.milliseconds)
  }
  val took = android.os.SystemClock.uptimeMillis() - started
  // Counted straight from the meter: at a frame a second the stutter count calls it "at rest".
  val m = FrameStats.meter("phone")
  val line =
      synchronized(m) {
        val n = m.frames.coerceAtLeast(1)
        "${m.frames} frames in $took ms = ${"%.1f".format(m.frames * 1000.0 / took)} fps, CPU avg ${"%.1f".format(m.cpuSum / n)} max ${"%.0f".format(m.cpuMax)} ms, longest gap ${m.longestGapMs} ms"
      }
  FrameStats.summary(minFrames = 1)
  Log.i("DebugDrive", "bench z$zoom tilt $tilt: $line")
}
