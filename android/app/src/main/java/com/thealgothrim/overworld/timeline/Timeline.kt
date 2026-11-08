package com.thealgothrim.overworld.timeline

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.thealgothrim.overworld.routing.TravelMode
import com.thealgothrim.overworld.traffic.metres
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import uniffi.ferrostar.GeographicCoordinate

/**
 * One piece of the Timeline: a [move] (a drive, ride or walk from one place to the next, with its
 * line) or a stay at a place. Times are epoch milliseconds.
 */
data class Segment(
    val id: Long = 0,
    val move: Boolean,
    val mode: TravelMode? = null,
    val start: Long,
    val end: Long,
    /** A stay's place, or where a move began. */
    val at: GeographicCoordinate,
    /** Where a move ended. */
    val to: GeographicCoordinate? = null,
    val metres: Double = 0.0,
    /** A stay's place name; a trip's destination for a move. */
    val name: String? = null,
    val area: String? = null,
    val city: String? = null,
    /** A move's line, polyline6. */
    val path: String? = null,
    /** The move was an Overworld trip with directions. */
    val trip: Boolean = false,
    val topKmh: Double = 0.0,
) {
  val millis: Long
    get() = (end - start).coerceAtLeast(0)
}

/** A month's travel per mode: how far and for how long. */
data class ModeTotal(val mode: TravelMode, val metres: Double, val millis: Long, val count: Int)

/** Stays at one place: how many, how long in all, the last one. */
data class PlaceVisits(val name: String, val at: GeographicCoordinate, val area: String?, val visits: Int, val millis: Long, val last: Long)

/** A city and the days you were in it. */
data class CityVisits(val city: String, val days: Int, val first: Long, val last: Long)

/** A journey away from home: consecutive days with a stop far from it, named by where you went. */
data class AwayTrip(val title: String, val from: LocalDate, val to: LocalDate, val metres: Double)

/**
 * The Timeline, kept only on this phone in its own small database. Drives and stops are added by
 * [TimelineRecorder] while Overworld runs; nothing here is ever sent anywhere.
 */
class TimelineStore(context: Context) : SQLiteOpenHelper(context, "timeline.db", null, 1) {
  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
        "CREATE TABLE seg (id INTEGER PRIMARY KEY AUTOINCREMENT, move INTEGER NOT NULL, mode TEXT, start INTEGER NOT NULL, " +
            "finish INTEGER NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, lat2 REAL, lng2 REAL, metres REAL DEFAULT 0, name TEXT, " +
            "area TEXT, city TEXT, path TEXT, trip INTEGER DEFAULT 0, top_kmh REAL DEFAULT 0)"
    )
    db.execSQL("CREATE INDEX seg_start ON seg(start)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

  private fun values(s: Segment) =
      ContentValues().apply {
        put("move", if (s.move) 1 else 0)
        put("mode", s.mode?.name)
        put("start", s.start)
        put("finish", s.end)
        put("lat", s.at.lat)
        put("lng", s.at.lng)
        put("lat2", s.to?.lat)
        put("lng2", s.to?.lng)
        put("metres", s.metres)
        put("name", s.name)
        put("area", s.area)
        put("city", s.city)
        put("path", s.path)
        put("trip", if (s.trip) 1 else 0)
        put("top_kmh", s.topKmh)
      }

  @Synchronized fun insert(s: Segment): Long = writableDatabase.insert("seg", null, values(s))

  @Synchronized fun update(s: Segment) {
    if (s.id > 0) writableDatabase.update("seg", values(s), "id = ?", arrayOf(s.id.toString()))
  }

  @Synchronized fun delete(id: Long) {
    writableDatabase.delete("seg", "id = ?", arrayOf(id.toString()))
  }

  /** Names for a piece, keeping a name it already has. */
  @Synchronized fun setNames(id: Long, name: String?, area: String?, city: String?) {
    writableDatabase.execSQL("UPDATE seg SET name = COALESCE(name, ?), area = COALESCE(?, area), city = COALESCE(?, city) WHERE id = ?", arrayOf<Any?>(name, area, city, id))
  }

  /** Your own name for a stay (Timeline lets you correct what it guessed). */
  @Synchronized fun rename(id: Long, name: String) {
    writableDatabase.execSQL("UPDATE seg SET name = ? WHERE id = ?", arrayOf<Any?>(name, id))
  }

  private fun Cursor.segment(): Segment {
    fun d(c: String) = getDouble(getColumnIndexOrThrow(c))
    fun s(c: String) = getColumnIndexOrThrow(c).let { if (isNull(it)) null else getString(it) }
    val lat2 = getColumnIndexOrThrow("lat2")
    return Segment(
        id = getLong(getColumnIndexOrThrow("id")),
        move = getInt(getColumnIndexOrThrow("move")) == 1,
        mode = s("mode")?.let { runCatching { TravelMode.valueOf(it) }.getOrNull() },
        start = getLong(getColumnIndexOrThrow("start")),
        end = getLong(getColumnIndexOrThrow("finish")),
        at = GeographicCoordinate(d("lat"), d("lng")),
        to = if (isNull(lat2)) null else GeographicCoordinate(getDouble(lat2), d("lng2")),
        metres = d("metres"),
        name = s("name"),
        area = s("area"),
        city = s("city"),
        path = s("path"),
        trip = getInt(getColumnIndexOrThrow("trip")) == 1,
        topKmh = d("top_kmh"),
    )
  }

  private fun query(where: String?, args: Array<String>?, order: String = "start ASC", limit: String? = null): List<Segment> =
      synchronized(this) {
        readableDatabase.query("seg", null, where, args, null, null, order, limit).use { c ->
          val out = ArrayList<Segment>(c.count)
          while (c.moveToNext()) out += c.segment()
          out
        }
      }

  /** The newest piece, to carry on from when the app starts again. */
  fun last(): Segment? = query(null, null, "start DESC", "1").firstOrNull()

  /** Everything overlapping [from]..[to] (ms), oldest first. */
  fun between(from: Long, to: Long): List<Segment> = query("start < ? AND finish > ?", arrayOf(to.toString(), from.toString()))

  fun day(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Segment> {
    val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
    return between(from, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
  }

  fun month(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): List<Segment> {
    val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return between(from, month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli())
  }

  /** The days of [month] with anything recorded, for the calendar. */
  fun daysWithData(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Set<Int> =
      month(month, zone).flatMap { s ->
            val a = Instant.ofEpochMilli(s.start).atZone(zone).toLocalDate()
            val b = Instant.ofEpochMilli(s.end).atZone(zone).toLocalDate()
            generateSequence(a) { d -> if (d < b) d.plusDays(1) else null }.filter { YearMonth.from(it) == month }.map { it.dayOfMonth }.toList()
          }
          .toSet()

  /** How far and how long per mode in [month]. */
  fun totals(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): List<ModeTotal> =
      month(month, zone).filter { it.move }.groupBy { it.mode ?: TravelMode.CAR }.map { (mode, list) -> ModeTotal(mode, list.sumOf { it.metres }, list.sumOf { it.millis }, list.size) }
          .sortedByDescending { it.metres }

  /** Stays grouped by place (name, else spot), most visited first, from [segments] or everything. */
  fun places(segments: List<Segment>? = null): List<PlaceVisits> {
    val stays = (segments ?: query("move = 0", null)).filter { !it.move }
    val groups = mutableListOf<MutableList<Segment>>()
    for (s in stays) {
      val g = groups.firstOrNull { g -> val first = g.first(); (s.name != null && s.name == first.name && metres(first.at, s.at) < 400) || metres(first.at, s.at) < 60 }
      if (g != null) g += s else groups += mutableListOf(s)
    }
    return groups.map { g ->
          val named = g.firstOrNull { it.name != null } ?: g.first()
          PlaceVisits(named.name ?: "Unnamed place", named.at, named.area, g.size, g.sumOf { it.millis }, g.maxOf { it.end })
        }
        .sortedWith(compareByDescending<PlaceVisits> { it.visits }.thenByDescending { it.millis })
  }

  fun cities(zone: ZoneId = ZoneId.systemDefault()): List<CityVisits> =
      query("city IS NOT NULL", null)
          .groupBy { it.city!! }
          .map { (city, list) ->
            val days = list.map { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }.toSet().size
            CityVisits(city, days, list.minOf { it.start }, list.maxOf { it.end })
          }
          .sortedByDescending { it.last }

  /**
   * Journeys away from [home] (or, with no Home set, from the place stayed at longest): runs of
   * consecutive days with a stop more than [AWAY_METRES] from it, named by the city stayed in longest.
   */
  fun awayTrips(home: GeographicCoordinate?, zone: ZoneId = ZoneId.systemDefault()): List<AwayTrip> {
    val all = query(null, null)
    val base = home ?: places(all).maxByOrNull { it.millis }?.at ?: return emptyList()
    val awayDays = sortedMapOf<LocalDate, MutableList<Segment>>()
    for (s in all) {
      val far = metres(base, s.at) > AWAY_METRES || (s.to != null && metres(base, s.to) > AWAY_METRES)
      if (far) awayDays.getOrPut(Instant.ofEpochMilli(s.start).atZone(zone).toLocalDate()) { mutableListOf() } += s
    }
    val trips = mutableListOf<AwayTrip>()
    var run = mutableListOf<LocalDate>()
    fun flush() {
      if (run.isEmpty()) return
      val segs = run.flatMap { awayDays[it].orEmpty() }
      val title =
          segs.filter { !it.move && it.city != null }.groupBy { it.city!! }.maxByOrNull { (_, l) -> l.sumOf { it.millis } }?.key
              ?: segs.firstNotNullOfOrNull { it.city ?: it.area ?: it.name }
              ?: "A trip away"
      trips += AwayTrip(title, run.first(), run.last(), segs.filter { it.move }.sumOf { it.metres })
      run = mutableListOf()
    }
    for (d in awayDays.keys) {
      if (run.isNotEmpty() && d != run.last().plusDays(1)) flush()
      run += d
    }
    flush()
    return trips.reversed()
  }

  @Synchronized fun deleteBetween(from: Long, to: Long) {
    writableDatabase.delete("seg", "start >= ? AND start < ?", arrayOf(from.toString(), to.toString()))
  }

  fun deleteDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()) =
      deleteBetween(date.atStartOfDay(zone).toInstant().toEpochMilli(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())

  @Synchronized fun deleteAll() {
    writableDatabase.delete("seg", null, null)
  }

  /** Auto-delete: everything that ended before [before] (ms). */
  @Synchronized fun deleteOlderThan(before: Long) {
    writableDatabase.delete("seg", "finish < ?", arrayOf(before.toString()))
  }

  @Synchronized fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM seg", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

  companion object {
    /** A stop this far from home counts as being away. */
    const val AWAY_METRES = 40_000.0
  }
}
