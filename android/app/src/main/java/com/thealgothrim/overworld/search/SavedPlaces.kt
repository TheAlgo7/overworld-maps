package com.thealgothrim.overworld.search

import android.content.Context
import androidx.core.content.edit
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import uniffi.ferrostar.GeographicCoordinate

data class SavedState(
    val home: Place? = null,
    val work: Place? = null,
    val starred: List<Place> = emptyList(),
    val recents: List<Place> = emptyList(),
) {
  fun isStarred(place: Place) = starred.any { it.samePlace(place) }
}

/** Two places within about 30 m are the same spot. */
fun Place.samePlace(other: Place): Boolean =
    abs(coordinate.lat - other.coordinate.lat) < 0.0003 && abs(coordinate.lng - other.coordinate.lng) < 0.0003

/** Home, Work, starred places and recent destinations, kept on the phone. */
class SavedPlaces(context: Context) {
  private val prefs = context.getSharedPreferences("overworld-saved", Context.MODE_PRIVATE)
  private val _state = MutableStateFlow(load())
  val state: StateFlow<SavedState> = _state.asStateFlow()

  fun setHome(place: Place?) = update { it.copy(home = place) }

  fun setWork(place: Place?) = update { it.copy(work = place) }

  fun toggleStar(place: Place) = update { s ->
    if (s.isStarred(place)) s.copy(starred = s.starred.filterNot { it.samePlace(place) })
    else s.copy(starred = listOf(place) + s.starred)
  }

  fun addRecent(place: Place) = update { s ->
    s.copy(recents = (listOf(place) + s.recents.filterNot { it.samePlace(place) }).take(MAX_RECENTS))
  }

  fun clearRecents() = update { it.copy(recents = emptyList()) }

  private fun update(change: (SavedState) -> SavedState) {
    val next = change(_state.value)
    _state.value = next
    prefs.edit {
      putString("home", next.home?.toJson()?.toString())
      putString("work", next.work?.toJson()?.toString())
      putString("starred", JSONArray(next.starred.map { it.toJson() }).toString())
      putString("recents", JSONArray(next.recents.map { it.toJson() }).toString())
    }
  }

  private fun load(): SavedState =
      runCatching {
            SavedState(
                home = prefs.getString("home", null)?.let { JSONObject(it).toPlace() },
                work = prefs.getString("work", null)?.let { JSONObject(it).toPlace() },
                starred = list("starred"),
                recents = list("recents"),
            )
          }
          .getOrDefault(SavedState())

  private fun list(key: String): List<Place> {
    val arr = JSONArray(prefs.getString(key, "[]"))
    return (0 until arr.length()).map { arr.getJSONObject(it).toPlace() }
  }

  private fun Place.toJson() =
      JSONObject().put("name", name).put("detail", detail).put("lat", coordinate.lat).put("lng", coordinate.lng)

  private fun JSONObject.toPlace() =
      Place(getString("name"), optString("detail"), GeographicCoordinate(lat = getDouble("lat"), lng = getDouble("lng")))

  private companion object {
    const val MAX_RECENTS = 8
  }
}
