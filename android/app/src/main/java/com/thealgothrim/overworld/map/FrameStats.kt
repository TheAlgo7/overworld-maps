package com.thealgothrim.overworld.map

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.concurrent.ConcurrentHashMap
import org.maplibre.android.maps.MapView

/**
 * How smoothly each map draws, from MapLibre's own callback after every frame: frames a second,
 * the stutters (a frame more than [STUTTER_MS] after the last while the map was moving), and
 * MapLibre's own time per frame on the CPU and GPU. One line a minute goes into the drive log while
 * driving, so after a drive that "lagged" the log says which screen dropped frames and why, instead
 * of a guess (the 7 Nov drive left nothing but trip times).
 */
object FrameStats {
  /** One map's counts since its last summary. Touched only on the main thread. */
  class Meter internal constructor(val name: String) {
    internal var frames = 0
    internal var activeMs = 0L
    internal var stutters = 0
    internal var longestStutterMs = 0L
    internal var cpuSum = 0.0
    internal var cpuMax = 0.0
    internal var gpuSum = 0.0
    internal var gpuMax = 0.0
    internal var lastAt = 0L
    /** The longest wait between any two frames, rest included (for a scripted pan, where there is none). */
    internal var longestGapMs = 0L

    @Synchronized
    internal fun frame(encodingSeconds: Double, renderingSeconds: Double) {
      val now = SystemClock.uptimeMillis()
      val gap = if (lastAt == 0L) 0L else now - lastAt
      lastAt = now
      // Each gap on the system trace's timeline (a no-op unless a trace is recording), so a stutter
      // lines up with what every thread was doing at that moment.
      if (android.os.Trace.isEnabled()) android.os.Trace.setCounter("ow.$name.gap", gap)
      longestGapMs = maxOf(longestGapMs, gap)
      // A map at rest draws nothing; a long pause is the map standing still, not a stutter.
      if (gap in 1..IDLE_MS) {
        activeMs += gap
        if (gap > STUTTER_MS) {
          stutters++
          longestStutterMs = maxOf(longestStutterMs, gap)
        }
      }
      frames++
      val cpu = encodingSeconds * 1000
      val gpu = renderingSeconds * 1000
      cpuSum += cpu
      cpuMax = maxOf(cpuMax, cpu)
      gpuSum += gpu
      gpuMax = maxOf(gpuMax, gpu)
    }

    /** The summary since the last one, or null if this map drew too little to say; then starts again. */
    @Synchronized
    internal fun take(minFrames: Int = MIN_FRAMES): String? {
      val line =
          if (frames < minFrames || activeMs <= 0) null
          else {
            val fps = frames * 1000.0 / activeMs
            "$name ${"%.0f".format(fps)} fps, $stutters stutters" +
                (if (stutters > 0) " (longest $longestStutterMs ms)" else "") +
                ", CPU ${"%.1f".format(cpuSum / frames)}/${"%.0f".format(cpuMax)} ms, GPU ${"%.1f".format(gpuSum / frames)}/${"%.0f".format(gpuMax)} ms"
          }
      frames = 0
      activeMs = 0
      stutters = 0
      longestStutterMs = 0
      longestGapMs = 0
      // The next frame starts the new count: the wait before it (the map at rest) isn't a gap.
      lastAt = 0L
      cpuSum = 0.0
      cpuMax = 0.0
      gpuSum = 0.0
      gpuMax = 0.0
      return line
    }
  }

  private val meters = ConcurrentHashMap<String, Meter>()
  /** The last summaries, newest last, for the report. */
  private val recent = ArrayDeque<String>()

  fun meter(name: String): Meter = meters.getOrPut(name) { Meter(name) }

  /**
   * One line for every map that drew since the last call ("car 31 fps, 4 stutters (longest 180
   * ms), CPU 6.2/19 ms, GPU 8.0/23 ms; cluster ..."), or null when none did. Main thread.
   */
  fun summary(minFrames: Int = MIN_FRAMES): String? {
    val parts = meters.values.sortedBy { it.name }.mapNotNull { it.take(minFrames) }
    if (parts.isEmpty()) return null
    val line = parts.joinToString("; ")
    synchronized(recent) {
      recent.addLast(line)
      while (recent.size > KEEP) recent.removeFirst()
    }
    return line
  }

  /** The last half hour or so of summaries, oldest first. */
  fun recentLines(): List<String> = synchronized(recent) { recent.toList() }

  /** Between frames while moving, longer than this is a visible stutter (two frames missed at 60 Hz, one at 30). */
  private const val STUTTER_MS = 50L
  /** A pause longer than this is the map at rest, not drawing slowly. */
  private const val IDLE_MS = 400L
  private const val MIN_FRAMES = 30
  private const val KEEP = 40
}

/**
 * Counts this screen's map frames into [FrameStats] under [name] ("car", "cluster", "phone"). The map
 * view is made a moment after the composition that holds it, so it's looked for in the window until
 * it's there.
 */
@Composable
fun FrameStatsProbe(name: String) {
  val view = LocalView.current
  DisposableEffect(view, name) {
    val meter = FrameStats.meter(name)
    val listener = MapView.OnDidFinishRenderingFrameListener { _, encoding, rendering -> meter.frame(encoding, rendering) }
    var map: MapView? = null
    var tries = 0
    val finder =
        object : Runnable {
          override fun run() {
            map = findMapView(view.rootView)
            map?.addOnDidFinishRenderingFrameListener(listener) ?: run { if (++tries < 40) view.postDelayed(this, 500) }
          }
        }
    view.post(finder)
    onDispose {
      view.removeCallbacks(finder)
      map?.removeOnDidFinishRenderingFrameListener(listener)
    }
  }
}

private fun findMapView(view: View): MapView? {
  if (view is MapView) return view
  if (view is ViewGroup) for (i in 0 until view.childCount) findMapView(view.getChildAt(i))?.let { return it }
  return null
}
