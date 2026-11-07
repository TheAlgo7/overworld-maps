package com.thealgothrim.overworld.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/**
 * The phone's air-pressure sensor, read as height changes. A flyover's deck is 6 to 8 m above the
 * road beside it and an underpass about as far below, which GPS can't tell apart (its height is
 * good to 10 m at best) but pressure can: about 0.12 hPa a metre, steady to a fraction of that over
 * half a minute in a car. Runs while GPS does (see LocationGate); phones without the sensor simply
 * never answer.
 */
object Barometer : SensorEventListener {
  private var manager: SensorManager? = null
  private var users = 0
  /** (elapsed ms, metres) a couple of times a second, the last [KEEP_MS]. */
  private val samples = ArrayDeque<Pair<Long, Float>>()
  private var smoothed = Float.NaN
  private var lastKept = 0L

  /** Whether the phone has the sensor; null until asked. */
  @Volatile var present: Boolean? = null
    private set

  @Synchronized
  fun acquire(context: Context) {
    if (users++ > 0) return
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    val sensor = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    present = sensor != null
    if (sm == null || sensor == null) return
    manager = sm
    sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
  }

  @Synchronized
  fun release() {
    if (users == 0 || --users > 0) return
    manager?.unregisterListener(this)
    manager = null
    samples.clear()
    smoothed = Float.NaN
  }

  override fun onSensorChanged(event: SensorEvent) {
    val metres = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0])
    synchronized(this) {
      // About a second of smoothing at the normal rate: a door or the fan nudges pressure for a moment.
      smoothed = if (smoothed.isNaN()) metres else smoothed + (metres - smoothed) * SMOOTHING
      val now = SystemClock.elapsedRealtime()
      if (now - lastKept < KEEP_EVERY_MS) return
      lastKept = now
      samples.addLast(now to smoothed)
      while (samples.isNotEmpty() && now - samples.first().first > KEEP_MS) samples.removeFirst()
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  /** Metres risen over the last [seconds]: now above the lowest point since. Null without readings. */
  @Synchronized
  fun rise(seconds: Int): Float? = window(seconds)?.let { (now, w) -> now - w.minOf { it.second } }

  /** Metres fallen over the last [seconds]: the highest point since above now. */
  @Synchronized
  fun drop(seconds: Int): Float? = window(seconds)?.let { (now, w) -> w.maxOf { it.second } - now }

  /** The smoothed height now (metres on a standard atmosphere), for the drive's track. */
  @Synchronized
  fun heightNow(): Float? = samples.lastOrNull()?.second

  private fun window(seconds: Int): Pair<Float, List<Pair<Long, Float>>>? {
    val last = samples.lastOrNull() ?: return null
    val since = last.first - seconds * 1000L
    val w = samples.filter { it.first >= since }
    // Too short a history says nothing yet (the sensor just started).
    if (w.size < 4 || last.first - w.first().first < seconds * 500L) return null
    return last.second to w
  }

  private const val SMOOTHING = 0.2f
  private const val KEEP_EVERY_MS = 400L
  private const val KEEP_MS = 120_000L
}
