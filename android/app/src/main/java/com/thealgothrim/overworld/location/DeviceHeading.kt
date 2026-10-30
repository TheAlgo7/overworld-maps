package com.thealgothrim.overworld.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Where the top of the phone points, in degrees from north, from Android's fused rotation sensor
 * (compass steadied by the gyro), for the phone map's compass mode: standing still, GPS has no
 * heading worth the name, and turning the phone should turn the map, as in Google Maps. Only
 * while collected. Empty on a phone without the sensor.
 */
fun deviceHeadings(context: Context): Flow<Float> {
  val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return emptyFlow()
  val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return emptyFlow()
  @Suppress("DEPRECATION") val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
  return callbackFlow {
    val rotation = FloatArray(9)
    val remapped = FloatArray(9)
    val angles = FloatArray(3)
    val listener =
        object : SensorEventListener {
          override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            // Measured from the top of the screen as the user holds it, landscape included.
            val (x, y) =
                when (display.rotation) {
                  Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                  Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                  Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                  else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
            SensorManager.remapCoordinateSystem(rotation, x, y, remapped)
            SensorManager.getOrientation(remapped, angles)
            trySend(((Math.toDegrees(angles[0].toDouble()) + 360.0) % 360.0).toFloat())
          }

          override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
    sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    awaitClose { sensors.unregisterListener(listener) }
  }
}
