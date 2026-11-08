package com.thealgothrim.overworld.search

import android.content.Context
import androidx.core.content.edit
import com.thealgothrim.overworld.routing.TravelMode
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import uniffi.ferrostar.GeographicCoordinate

/** Google's default lists, and the ones you make yourself. */
enum class ListKind {
  FAVOURITES,
  WANT_TO_GO,
  TRAVEL_PLANS,
  STARRED,
  CUSTOM,
}

/** A list of saved places: one of Google's four defaults, or your own with an optional emoji. */
data class SavedList(
    val id: String,
    val name: String,
    val kind: ListKind = ListKind.CUSTOM,
    val emoji: String? = null,
    val createdAt: Long = 0,
) {
  val builtIn: Boolean
    get() = kind != ListKind.CUSTOM
}

/**
 * A place you saved: as it was found ([place]), under your own name if you gave it one ([label],
 * Google's "Labelled"), with a [note], in any number of [lists].
 */
data class SavedPlace(
    val id: String,
    val place: Place,
    val label: String? = null,
    val note: String? = null,
    val lists: Set<String> = emptySet(),
    val savedAt: Long = 0,
) {
  /** What it's called: your label, else the name it was found under. */
  val name: String
    get() = label?.takeIf { it.isNotBlank() } ?: place.name

  /** As a [Place] under your name, its own name and address underneath. */
  fun asPlace(): Place {
    val own = label?.takeIf { it.isNotBlank() && it != place.name }
    val detail = if (own != null) listOf(place.name, place.detail).filter { it.isNotBlank() && it != "Dropped pin" }.joinToString(" · ") else place.detail
    return Place(name, detail, place.coordinate)
  }
}

/** A trip you saved to start again with one tap: from your location (or [from]) to [to], by [mode]. */
data class SavedTrip(
    val id: String,
    val to: Place,
    val mode: TravelMode = TravelMode.CAR,
    val from: Place? = null,
    val savedAt: Long = 0,
) {
  val title: String
    get() = if (from != null) "${from.name} to ${to.name}" else "To ${to.name}"
}

/** Where you parked: like Google's "You parked here", with a note and an optional time limit. */
data class ParkingSpot(
    val at: GeographicCoordinate,
    val savedAt: Long,
    /** The street or area it's in, filled in when known. */
    val near: String? = null,
    val note: String? = null,
    /** When the parking time runs out (ms), for the reminder. */
    val until: Long? = null,
)

data class SavedState(
    val home: Place? = null,
    val work: Place? = null,
    val places: List<SavedPlace> = emptyList(),
    val lists: List<SavedList> = SavedPlaces.DEFAULT_LISTS,
    val trips: List<SavedTrip> = emptyList(),
    val parking: ParkingSpot? = null,
    val recents: List<Place> = emptyList(),
) {
  fun savedFor(place: Place): SavedPlace? = places.firstOrNull { it.place.samePlace(place) }

  /** Saved in any way: in a list, or given your own name. */
  fun isStarred(place: Place) = savedFor(place) != null

  /**
   * Every saved place under your name, labelled ones first then newest saved: the car's Saved
   * menu, the map's saved blips and Search read this.
   */
  val starred: List<Place>
    get() = places.sortedWith(compareBy<SavedPlace> { it.label == null }.thenByDescending { it.savedAt }).map { it.asPlace() }

  fun inList(id: String): List<SavedPlace> = places.filter { id in it.lists }.sortedByDescending { it.savedAt }

  /** Places with your own name for them (Home and Work are shown beside these). */
  val labelled: List<SavedPlace>
    get() = places.filter { !it.label.isNullOrBlank() }.sortedBy { it.name.lowercase() }

  fun listsOf(place: Place): List<SavedList> =
      savedFor(place)?.lists?.mapNotNull { id -> lists.firstOrNull { it.id == id } }.orEmpty()

  fun list(id: String): SavedList? = lists.firstOrNull { it.id == id }

  /** Saved places whose name, label, note or address has every word of [query] in it. */
  fun matching(query: String): List<SavedPlace> {
    val words = fold(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return places.filter { p ->
      val hay = fold(listOfNotNull(p.label, p.place.name, p.place.detail, p.note).joinToString(" "))
      words.all { it in hay }
    }
  }
}

/** Two places within about 30 m are the same spot. */
fun Place.samePlace(other: Place): Boolean = coordinate.samePoint(other.coordinate)

fun GeographicCoordinate.samePoint(other: GeographicCoordinate): Boolean = abs(lat - other.lat) < 0.0003 && abs(lng - other.lng) < 0.0003

/**
 * Home, Work, saved places in lists, saved trips, the parked car and recent destinations, kept on
 * the phone. Before 0.6 this was Home, Work, one starred list and recents; those carry over (the
 * starred places into Starred places) the first time it loads.
 */
class SavedPlaces(context: Context) {
  private val prefs = context.getSharedPreferences("overworld-saved", Context.MODE_PRIVATE)
  private val _state = MutableStateFlow(load())
  val state: StateFlow<SavedState> = _state.asStateFlow()

  fun setHome(place: Place?) = update { it.copy(home = place) }

  fun setWork(place: Place?) = update { it.copy(work = place) }

  /** Saved or not: off takes it out of every list and forgets its name and note. */
  fun toggleStar(place: Place) = update { s ->
    if (s.isStarred(place)) s.copy(places = s.places.filterNot { it.place.samePlace(place) }) else Ops.inList(s, place, STARRED, true)
  }

  /** In [listId] or not. A place left in no list, with no name or note of its own, is unsaved. */
  fun setInList(place: Place, listId: String, inList: Boolean) = update { Ops.inList(it, place, listId, inList) }

  /** Your own name for [place] (blank or null: back to its own). */
  fun setLabel(place: Place, label: String?) = update { Ops.label(it, place, label) }

  fun setNote(place: Place, note: String?) = update { Ops.note(it, place, note) }

  /** Forget [place] entirely: every list, its name and note. */
  fun remove(place: Place) = update { s -> s.copy(places = s.places.filterNot { it.place.samePlace(place) }) }

  /** A new list of your own; returns its id. */
  fun createList(name: String, emoji: String?): String {
    val id = "list-" + UUID.randomUUID().toString().take(8)
    update { it.copy(lists = it.lists + SavedList(id, name.trim(), ListKind.CUSTOM, emoji?.takeIf { e -> e.isNotBlank() }, System.currentTimeMillis())) }
    return id
  }

  fun editList(id: String, name: String, emoji: String?) = update { s ->
    s.copy(lists = s.lists.map { if (it.id == id && !it.builtIn) it.copy(name = name.trim(), emoji = emoji?.takeIf { e -> e.isNotBlank() }) else it })
  }

  /** Deletes your own list; its places stay saved if they're in another list or have a name or note. */
  fun deleteList(id: String) = update { s ->
    if (s.list(id)?.builtIn != false) return@update s
    Ops.tidy(s.copy(lists = s.lists.filterNot { it.id == id }, places = s.places.map { it.copy(lists = it.lists - id) }))
  }

  fun saveTrip(to: Place, mode: TravelMode, from: Place? = null) = update { s ->
    if (s.trips.any { it.to.samePlace(to) && it.mode == mode && it.from?.coordinate == from?.coordinate }) s
    else s.copy(trips = listOf(SavedTrip("trip-" + UUID.randomUUID().toString().take(8), to, mode, from, System.currentTimeMillis())) + s.trips)
  }

  fun removeTrip(id: String) = update { s -> s.copy(trips = s.trips.filterNot { it.id == id }) }

  fun park(at: GeographicCoordinate, near: String? = null) = update { it.copy(parking = ParkingSpot(at, System.currentTimeMillis(), near)) }

  fun updateParking(change: (ParkingSpot) -> ParkingSpot) = update { s -> s.parking?.let { s.copy(parking = change(it)) } ?: s }

  fun clearParking() = update { it.copy(parking = null) }

  fun addRecent(place: Place) = update { s ->
    s.copy(recents = (listOf(place) + s.recents.filterNot { it.samePlace(place) }).take(MAX_RECENTS))
  }

  fun clearRecents() = update { it.copy(recents = emptyList()) }

  private fun update(change: (SavedState) -> SavedState) {
    synchronized(this) {
      val next = change(_state.value)
      if (next == _state.value) return
      _state.value = next
      prefs.edit { putString(KEY, Json.write(next).toString()) }
    }
  }

  private fun load(): SavedState {
    prefs.getString(KEY, null)?.let { raw -> runCatching { return Json.read(JSONObject(raw)) } }
    // Before 0.6: Home, Work, starred and recents under their own keys. They stay where they were
    // too, so an older build installed over this one still finds them.
    return runCatching {
          val now = System.currentTimeMillis()
          val starred = Json.places(JSONArray(prefs.getString("starred", "[]")))
          SavedState(
              home = prefs.getString("home", null)?.let { Json.place(JSONObject(it)) },
              work = prefs.getString("work", null)?.let { Json.place(JSONObject(it)) },
              places = starred.mapIndexed { i, p -> SavedPlace("old-$i", p, lists = setOf(STARRED), savedAt = now - i) },
              recents = Json.places(JSONArray(prefs.getString("recents", "[]"))),
          )
        }
        .getOrDefault(SavedState())
  }

  /** The changes to a [SavedState], kept apart from storage so tests can run them. */
  object Ops {
    fun inList(s: SavedState, place: Place, listId: String, inList: Boolean): SavedState {
      val existing = s.savedFor(place)
      if (existing == null) {
        if (!inList) return s
        val saved = SavedPlace(newId(), place, lists = setOf(listId), savedAt = System.currentTimeMillis())
        return s.copy(places = listOf(saved) + s.places)
      }
      val lists = if (inList) existing.lists + listId else existing.lists - listId
      return tidy(s.copy(places = s.places.map { if (it.id == existing.id) it.copy(lists = lists) else it }))
    }

    fun label(s: SavedState, place: Place, label: String?): SavedState {
      val clean = label?.trim()?.takeIf { it.isNotEmpty() }
      val existing = s.savedFor(place)
      if (existing == null) {
        if (clean == null) return s
        return s.copy(places = listOf(SavedPlace(newId(), place, label = clean, savedAt = System.currentTimeMillis())) + s.places)
      }
      return tidy(s.copy(places = s.places.map { if (it.id == existing.id) it.copy(label = clean) else it }))
    }

    fun note(s: SavedState, place: Place, note: String?): SavedState {
      val clean = note?.trim()?.takeIf { it.isNotEmpty() }
      val existing = s.savedFor(place)
      if (existing == null) {
        if (clean == null) return s
        // A note alone saves the place, in Saved like Google's "just save".
        return s.copy(places = listOf(SavedPlace(newId(), place, note = clean, lists = setOf(STARRED), savedAt = System.currentTimeMillis())) + s.places)
      }
      return s.copy(places = s.places.map { if (it.id == existing.id) it.copy(note = clean) else it })
    }

    /** Places in no list with no name or note of their own aren't saved any more. */
    fun tidy(s: SavedState): SavedState =
        s.copy(places = s.places.filter { it.lists.any { id -> s.list(id) != null } || !it.label.isNullOrBlank() || !it.note.isNullOrBlank() })

    private fun newId() = "p-" + UUID.randomUUID().toString().take(12)
  }

  /** Storage as JSON. */
  internal object Json {
    fun write(s: SavedState): JSONObject =
        JSONObject()
            .put("version", 2)
            .put("home", s.home?.let(::place))
            .put("work", s.work?.let(::place))
            .put("lists", JSONArray(s.lists.map { l -> JSONObject().put("id", l.id).put("name", l.name).put("kind", l.kind.name).put("emoji", l.emoji).put("at", l.createdAt) }))
            .put(
                "places",
                JSONArray(
                    s.places.map { p ->
                      JSONObject().put("id", p.id).put("place", place(p.place)).put("label", p.label).put("note", p.note).put("lists", JSONArray(p.lists.toList())).put("at", p.savedAt)
                    }
                ),
            )
            .put("trips", JSONArray(s.trips.map { t -> JSONObject().put("id", t.id).put("to", place(t.to)).put("from", t.from?.let(::place)).put("mode", t.mode.name).put("at", t.savedAt) }))
            .put(
                "parking",
                s.parking?.let { k -> JSONObject().put("lat", k.at.lat).put("lng", k.at.lng).put("at", k.savedAt).put("near", k.near).put("note", k.note).put("until", k.until) },
            )
            .put("recents", JSONArray(s.recents.map(::place)))

    fun read(o: JSONObject): SavedState {
      val lists =
          o.optJSONArray("lists")?.let { a ->
            (0 until a.length()).map { i ->
              val l = a.getJSONObject(i)
              SavedList(l.getString("id"), l.getString("name"), runCatching { ListKind.valueOf(l.optString("kind")) }.getOrDefault(ListKind.CUSTOM), l.str("emoji"), l.optLong("at"))
            }
          } ?: SavedPlaces.DEFAULT_LISTS
      // A default list added in a later version shows up for people whose lists were saved before it.
      val all = lists + SavedPlaces.DEFAULT_LISTS.filter { d -> lists.none { it.id == d.id } }
      return SavedState(
          home = o.optJSONObject("home")?.let(::place),
          work = o.optJSONObject("work")?.let(::place),
          lists = all,
          places =
              o.optJSONArray("places")?.let { a ->
                (0 until a.length()).map { i ->
                  val p = a.getJSONObject(i)
                  val ids = p.optJSONArray("lists")?.let { l -> (0 until l.length()).map { l.getString(it) }.toSet() }.orEmpty()
                  SavedPlace(p.getString("id"), place(p.getJSONObject("place")), p.str("label"), p.str("note"), ids, p.optLong("at"))
                }
              }.orEmpty(),
          trips =
              o.optJSONArray("trips")?.let { a ->
                (0 until a.length()).map { i ->
                  val t = a.getJSONObject(i)
                  SavedTrip(t.getString("id"), place(t.getJSONObject("to")), runCatching { TravelMode.valueOf(t.optString("mode")) }.getOrDefault(TravelMode.CAR), t.optJSONObject("from")?.let(::place), t.optLong("at"))
                }
              }.orEmpty(),
          parking =
              o.optJSONObject("parking")?.let { k ->
                ParkingSpot(GeographicCoordinate(k.getDouble("lat"), k.getDouble("lng")), k.optLong("at"), k.str("near"), k.str("note"), k.optLong("until").takeIf { it > 0 })
              },
          recents = o.optJSONArray("recents")?.let(::places).orEmpty(),
      )
    }

    fun place(p: Place): JSONObject = JSONObject().put("name", p.name).put("detail", p.detail).put("lat", p.coordinate.lat).put("lng", p.coordinate.lng)

    fun place(o: JSONObject): Place = Place(o.getString("name"), o.optString("detail"), GeographicCoordinate(lat = o.getDouble("lat"), lng = o.getDouble("lng")))

    fun places(a: JSONArray): List<Place> = (0 until a.length()).map { place(a.getJSONObject(it)) }

    /** A string field, null when missing or JSON null (optString gives "null" for those). */
    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
  }

  companion object {
    private const val KEY = "saved_v2"
    private const val MAX_RECENTS = 8
    const val FAVOURITES = "favourites"
    const val WANT_TO_GO = "want_to_go"
    const val TRAVEL_PLANS = "travel_plans"
    const val STARRED = "starred"

    /** Google's default lists, in its order. */
    val DEFAULT_LISTS =
        listOf(
            SavedList(FAVOURITES, "Favourites", ListKind.FAVOURITES),
            SavedList(WANT_TO_GO, "Want to go", ListKind.WANT_TO_GO),
            SavedList(TRAVEL_PLANS, "Travel plans", ListKind.TRAVEL_PLANS),
            SavedList(STARRED, "Starred places", ListKind.STARRED),
        )
  }
}
