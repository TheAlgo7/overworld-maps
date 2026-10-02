package com.thealgothrim.overworld.traffic

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import uniffi.ferrostar.GeographicCoordinate

/** A camera marked by hand, with the way the car was heading (a camera watches one direction). */
data class MarkedCamera(val at: GeographicCoordinate, val heading: Double?)

/**
 * Cameras marked with the Camera button while driving, kept on the phone. OpenStreetMap knows few
 * of Delhi's cameras and Radarbot's list can't be used, so the roads driven most fill in from here.
 */
class MyCameras(context: Context) {
  private val prefs = context.getSharedPreferences("overworld-cameras", Context.MODE_PRIVATE)
  private val _all = MutableStateFlow(load())
  val all: StateFlow<List<MarkedCamera>> = _all.asStateFlow()

  /** Marks a camera here. One already within 40 m is moved to this spot instead of doubled. */
  fun mark(at: GeographicCoordinate, heading: Double?) {
    save(_all.value.filterNot { metres(it.at, at) < 40.0 } + MarkedCamera(at, heading))
  }

  fun clear() = save(emptyList())

  private fun save(cameras: List<MarkedCamera>) {
    _all.value = cameras
    val json =
        JSONArray(
            cameras.map { c ->
              JSONObject().put("lat", c.at.lat).put("lng", c.at.lng).apply { c.heading?.let { put("heading", it) } }
            }
        )
    prefs.edit { putString("cameras", json.toString()) }
  }

  private fun load(): List<MarkedCamera> =
      runCatching {
            val arr = JSONArray(prefs.getString("cameras", "[]"))
            (0 until arr.length()).map { i ->
              val o = arr.getJSONObject(i)
              MarkedCamera(GeographicCoordinate(o.getDouble("lat"), o.getDouble("lng")), o.optDouble("heading").takeUnless { it.isNaN() })
            }
          }
          .getOrDefault(emptyList())
}
