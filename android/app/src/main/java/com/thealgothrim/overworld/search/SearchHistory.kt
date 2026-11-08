package com.thealgothrim.overworld.search

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** What happened, as Google's Maps history filters name it. */
enum class HistoryKind(val label: String) {
  SEARCHED("Searched"),
  VIEWED("Viewed"),
  DIRECTIONS("Got directions"),
}

/** One thing in Maps history: a search typed ([text]), or a place looked at or driven to. */
data class HistoryEntry(val kind: HistoryKind, val text: String, val place: Place?, val at: Long)

/**
 * Maps history, kept on the phone: searches, places looked at and places asked directions to,
 * newest first. Search shows the latest when its box is empty; You > History lists them all.
 * Nothing is kept while Settings > Privacy > "Keep search history" is off.
 */
class SearchHistory(context: Context, private val keep: () -> Boolean = { true }) {
  private val prefs = context.getSharedPreferences("overworld-history", Context.MODE_PRIVATE)
  private val _entries = MutableStateFlow(load())
  val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

  fun searched(query: String) {
    val q = query.trim()
    if (q.length < 2) return
    add(HistoryEntry(HistoryKind.SEARCHED, q, null, System.currentTimeMillis())) { it.kind == HistoryKind.SEARCHED && it.text.equals(q, ignoreCase = true) }
  }

  fun viewed(place: Place) {
    if (place.name in UNNAMED) return
    add(HistoryEntry(HistoryKind.VIEWED, place.name, place, System.currentTimeMillis())) { it.kind == HistoryKind.VIEWED && it.place?.samePlace(place) == true }
  }

  fun directions(place: Place) {
    add(HistoryEntry(HistoryKind.DIRECTIONS, place.name, place, System.currentTimeMillis())) { it.kind == HistoryKind.DIRECTIONS && it.place?.samePlace(place) == true }
  }

  fun remove(entry: HistoryEntry) = set(_entries.value - entry)

  fun clear() = set(emptyList())

  /**
   * What an empty search box lists: the latest searches and places, one row per search or place
   * (a place looked at and then driven to shows once).
   */
  fun recentForSearch(limit: Int = 8): List<HistoryEntry> {
    val out = mutableListOf<HistoryEntry>()
    for (e in _entries.value) {
      val dup = out.any { o ->
        if (e.place != null && o.place != null) o.place.samePlace(e.place)
        else e.place == null && o.place == null && o.text.equals(e.text, ignoreCase = true)
      }
      if (!dup) out += e
      if (out.size == limit) break
    }
    return out
  }

  private fun add(entry: HistoryEntry, same: (HistoryEntry) -> Boolean) {
    if (!keep()) return
    set((listOf(entry) + _entries.value.filterNot(same)).take(MAX))
  }

  private fun set(list: List<HistoryEntry>) {
    synchronized(this) {
      _entries.value = list
      prefs.edit {
        putString(
            KEY,
            JSONArray(
                    list.map { e ->
                      JSONObject().put("kind", e.kind.name).put("text", e.text).put("at", e.at).put("place", e.place?.let { SavedPlaces.Json.place(it) })
                    }
                )
                .toString(),
        )
      }
    }
  }

  private fun load(): List<HistoryEntry> =
      runCatching {
            val a = JSONArray(prefs.getString(KEY, "[]"))
            (0 until a.length()).map { i ->
              val o = a.getJSONObject(i)
              HistoryEntry(HistoryKind.valueOf(o.getString("kind")), o.getString("text"), o.optJSONObject("place")?.let { SavedPlaces.Json.place(it) }, o.getLong("at"))
            }
          }
          .getOrDefault(emptyList())

  companion object {
    private const val KEY = "history"
    private const val MAX = 300
    /** Pins and points with no name of their own don't go into history. */
    private val UNNAMED = setOf("Dropped pin", "Pinned point", "Destination")
  }
}
