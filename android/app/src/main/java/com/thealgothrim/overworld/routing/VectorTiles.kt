package com.thealgothrim.overworld.routing

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import uniffi.ferrostar.GeographicCoordinate

/** Whether a road is on a bridge (a flyover), in a tunnel (an underpass) or on the ground. */
enum class Level { SURFACE, BRIDGE, TUNNEL }

/**
 * A road from the map's own tiles: its class ("motorway" ... "service"), whether it's a flyover or
 * an underpass, its one-way direction (1 along its points, -1 against, 0 both) and its line.
 */
class TileRoad(val cls: String, val level: Level, val oneway: Int, val ramp: Boolean, val line: List<GeographicCoordinate>)

/**
 * The roads around a route from OpenFreeMap's vector tiles, the same tiles the map draws: every road
 * with OpenStreetMap's bridge and tunnel tags, the service road beside a flyover included. The
 * routing server only describes the roads a route uses; whether there's a flyover beside it, or a
 * service road beside the flyover it takes, only the map knows. Zoom 14, the tiles' most detailed
 * level (about half a metre). Cached on disk, so a road driven before costs nothing.
 */
class VectorTiles(http: OkHttpClient, cacheDir: File) {
  private val client =
      http.newBuilder().cache(Cache(File(cacheDir, "road-tiles"), CACHE_BYTES)).build()
  @Volatile private var template: String? = null

  /** The drivable roads within [margin] metres of [line] (one tile fetch per z14 tile it crosses). */
  suspend fun roadsAlong(line: List<GeographicCoordinate>, margin: Double = 80.0): List<TileRoad> =
      withContext(Dispatchers.IO) {
        if (line.size < 2) return@withContext emptyList()
        val url = template ?: fetchTemplate()?.also { template = it } ?: return@withContext emptyList()
        val tiles = tilesAlong(line, margin)
        if (tiles.size > MAX_TILES) Log.w(TAG, "${tiles.size} tiles along the route; using the first $MAX_TILES")
        val gate = Semaphore(PARALLEL)
        coroutineScope {
              tiles.take(MAX_TILES).map { (x, y) ->
                async { gate.withPermit { runCatching { fetch(url, x, y) }.onFailure { Log.w(TAG, "tile $x,$y failed: $it") }.getOrNull().orEmpty() } }
              }.awaitAll()
            }
            .flatten()
      }

  private fun fetchTemplate(): String? =
      runCatching {
            client.newCall(Request.Builder().url(TILEJSON).build()).execute().use { r ->
              if (!r.isSuccessful) return null
              JSONObject(r.body.string()).getJSONArray("tiles").getString(0)
            }
          }
          .onFailure { Log.w(TAG, "tile index failed: $it") }
          .getOrNull()

  private fun fetch(template: String, x: Int, y: Int): List<TileRoad> {
    val url = template.replace("{z}", "$ZOOM").replace("{x}", "$x").replace("{y}", "$y")
    val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body.bytes() else null } ?: return emptyList()
    val raw = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) GZIPInputStream(ByteArrayInputStream(bytes)).readBytes() else bytes
    return roads(Mvt.layer(raw, "transportation") ?: return emptyList(), x, y)
  }

  companion object {
    private const val TAG = "VectorTiles"
    const val ZOOM = 14
    private const val TILEJSON = "https://tiles.openfreemap.org/planet"
    private const val CACHE_BYTES = 60L * 1024 * 1024
    private const val PARALLEL = 4
    /** About 60 km of a city route; longer trips are looked at ahead of the car (see RoadLevels). */
    private const val MAX_TILES = 90

    /** Road classes a car drives on (OpenMapTiles' names); paths, rail and anything being built are left out. */
    private val DRIVABLE = setOf("motorway", "trunk", "primary", "secondary", "tertiary", "minor", "service")
    /** Service roads that are a car park's lanes or someone's drive, not a road beside the main one. */
    private val NOT_A_ROAD = setOf("parking_aisle", "driveway", "drive-through")

    /** Tile (x, y) holding a point at [ZOOM]. */
    fun tileOf(p: GeographicCoordinate): Pair<Int, Int> {
      val n = 1 shl ZOOM
      val x = floor((p.lng + 180.0) / 360.0 * n).toInt()
      val r = Math.toRadians(p.lat)
      val y = floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n).toInt()
      return x to y
    }

    /** Every tile within [margin] metres of [line], in order along it. */
    internal fun tilesAlong(line: List<GeographicCoordinate>, margin: Double): List<Pair<Int, Int>> {
      val out = LinkedHashSet<Pair<Int, Int>>()
      fun around(p: GeographicCoordinate) {
        val dLat = margin / 111_320.0
        val dLng = margin / (111_320.0 * cos(Math.toRadians(p.lat)))
        for (sy in -1..1) for (sx in -1..1) out += tileOf(GeographicCoordinate(p.lat + sy * dLat, p.lng + sx * dLng))
      }
      for (i in 0 until line.lastIndex) {
        val a = line[i]
        val b = line[i + 1]
        val steps = (com.thealgothrim.overworld.traffic.metres(a, b) / SAMPLE_M).toInt().coerceAtLeast(1)
        for (k in 0 until steps) {
          val t = k.toDouble() / steps
          around(GeographicCoordinate(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t))
        }
      }
      around(line.last())
      return out.toList()
    }

    private const val SAMPLE_M = 150.0

    /** The drivable roads of one tile's transportation layer, in lat/lng. */
    internal fun roads(layer: Mvt.Layer, x: Int, y: Int): List<TileRoad> {
      val n = (1 shl ZOOM).toDouble()
      val extent = layer.extent.toDouble()
      fun at(px: Int, py: Int): GeographicCoordinate {
        val lng = (x + px / extent) / n * 360.0 - 180.0
        val lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * (y + py / extent) / n))))
        return GeographicCoordinate(lat, lng)
      }
      val out = ArrayList<TileRoad>()
      for (f in layer.features) {
        if (f.type != Mvt.LINESTRING) continue
        val cls = f.tags["class"] as? String ?: continue
        if (cls !in DRIVABLE) continue
        if ((f.tags["service"] as? String) in NOT_A_ROAD) continue
        val level =
            when (f.tags["brunnel"] as? String) {
              "bridge" -> Level.BRIDGE
              "tunnel" -> Level.TUNNEL
              else -> Level.SURFACE
            }
        val oneway = (f.tags["oneway"] as? Number)?.toInt() ?: 0
        val ramp = (f.tags["ramp"] as? Number)?.toInt() == 1
        for (part in f.lines) if (part.size >= 2) out += TileRoad(cls, level, oneway, ramp, part.map { (px, py) -> at(px, py) })
      }
      return out
    }
  }
}

/**
 * Just enough of the Mapbox Vector Tile format (protobuf) to read one layer's lines and their
 * attributes. Tiles are a few hundred kilobytes and only "transportation" is read, so a small
 * hand-written reader beats pulling in a protobuf library.
 */
object Mvt {
  const val POINT = 1
  const val LINESTRING = 2
  const val POLYGON = 3

  class Feature(val type: Int, val tags: Map<String, Any?>, val lines: List<List<Pair<Int, Int>>>)

  class Layer(val name: String, val extent: Int, val features: List<Feature>)

  /** The layer called [name] in a decoded (not gzipped) tile, or null if it has none. */
  fun layer(tile: ByteArray, name: String): Layer? {
    val r = Reader(tile, 0, tile.size)
    while (r.more()) {
      val key = r.varint().toInt()
      if (key ushr 3 == 3 && key and 7 == 2) {
        val len = r.varint().toInt()
        val start = r.pos
        val layerName = peekName(tile, start, start + len)
        if (layerName == name) return parseLayer(tile, start, start + len)
        r.pos = start + len
      } else r.skip(key and 7)
    }
    return null
  }

  private fun peekName(b: ByteArray, from: Int, to: Int): String? {
    val r = Reader(b, from, to)
    while (r.more()) {
      val key = r.varint().toInt()
      if (key ushr 3 == 1 && key and 7 == 2) return r.string()
      r.skip(key and 7)
    }
    return null
  }

  private fun parseLayer(b: ByteArray, from: Int, to: Int): Layer {
    val r = Reader(b, from, to)
    var name = ""
    var extent = 4096
    val keys = ArrayList<String>()
    val values = ArrayList<Any?>()
    val raw = ArrayList<IntRange>()
    while (r.more()) {
      val key = r.varint().toInt()
      when (key ushr 3) {
        1 -> name = r.string()
        2 -> {
          val len = r.varint().toInt()
          raw += r.pos until r.pos + len
          r.pos += len
        }
        3 -> keys += r.string()
        4 -> {
          val len = r.varint().toInt()
          values += parseValue(b, r.pos, r.pos + len)
          r.pos += len
        }
        5 -> extent = r.varint().toInt()
        else -> r.skip(key and 7)
      }
    }
    val features = raw.map { parseFeature(b, it.first, it.last + 1, keys, values) }
    return Layer(name, extent, features)
  }

  private fun parseValue(b: ByteArray, from: Int, to: Int): Any? {
    val r = Reader(b, from, to)
    var v: Any? = null
    while (r.more()) {
      val key = r.varint().toInt()
      v =
          when (key ushr 3) {
            1 -> r.string()
            2 -> java.lang.Float.intBitsToFloat(r.fixed32())
            3 -> java.lang.Double.longBitsToDouble(r.fixed64())
            4 -> r.varint()
            5 -> r.varint()
            6 -> r.varint().let { (it ushr 1) xor -(it and 1) }
            7 -> r.varint() != 0L
            else -> {
              r.skip(key and 7)
              v
            }
          }
    }
    return v
  }

  private fun parseFeature(b: ByteArray, from: Int, to: Int, keys: List<String>, values: List<Any?>): Feature {
    val r = Reader(b, from, to)
    var type = 0
    val tags = HashMap<String, Any?>()
    var geometry = IntArray(0)
    var geometrySize = 0
    while (r.more()) {
      val key = r.varint().toInt()
      when (key ushr 3) {
        2 -> {
          // The length first: read inside the sum, the start was taken before it and ran short.
          val len = r.varint().toInt()
          val end = r.pos + len
          while (r.pos < end) {
            val k = r.varint().toInt()
            val v = r.varint().toInt()
            keys.getOrNull(k)?.let { tags[it] = values.getOrNull(v) }
          }
        }
        3 -> type = r.varint().toInt()
        4 -> {
          val len = r.varint().toInt()
          val end = r.pos + len
          geometry = IntArray(len)
          while (r.pos < end) geometry[geometrySize++] = r.varint().toInt()
        }
        else -> r.skip(key and 7)
      }
    }
    return Feature(type, tags, if (type == LINESTRING) lines(geometry, geometrySize) else emptyList())
  }

  /** A line feature's command stream as its parts (MoveTo starts each). */
  internal fun lines(g: IntArray, size: Int): List<List<Pair<Int, Int>>> {
    val out = ArrayList<List<Pair<Int, Int>>>()
    var current: ArrayList<Pair<Int, Int>>? = null
    var x = 0
    var y = 0
    var i = 0
    while (i < size) {
      val cmd = g[i++]
      val id = cmd and 7
      val count = cmd ushr 3
      when (id) {
        1, 2 -> repeat(count) {
          // Both of the point's numbers must be there; a cut-off stream ends the feature.
          if (i + 1 >= size) return out.also { current?.takeIf { it.size >= 2 }?.let { c -> out += c } }
          x += zigzag(g[i++])
          y += zigzag(g[i++])
          if (id == 1) {
            current?.takeIf { it.size >= 2 }?.let { out += it }
            current = arrayListOf(x to y)
          } else current?.add(x to y)
        }
        7 -> Unit
        else -> return out
      }
    }
    current?.takeIf { it.size >= 2 }?.let { out += it }
    return out
  }

  private fun zigzag(n: Int) = (n ushr 1) xor -(n and 1)

  private class Reader(val b: ByteArray, var pos: Int, val end: Int) {
    fun more() = pos < end

    fun varint(): Long {
      var shift = 0
      var result = 0L
      while (pos < end) {
        val byte = b[pos++].toInt()
        result = result or ((byte and 0x7f).toLong() shl shift)
        if (byte and 0x80 == 0) return result
        shift += 7
      }
      // Out of bytes mid-number: a damaged tile. Returning here without moving on left the loops
      // that read up to a length written in the tile (a feature's tags) spinning for good.
      throw IllegalArgumentException("vector tile cut off at byte $pos")
    }

    fun fixed32(): Int {
      val v = (b[pos].toInt() and 0xff) or ((b[pos + 1].toInt() and 0xff) shl 8) or ((b[pos + 2].toInt() and 0xff) shl 16) or ((b[pos + 3].toInt() and 0xff) shl 24)
      pos += 4
      return v
    }

    fun fixed64(): Long {
      var v = 0L
      for (k in 0 until 8) v = v or ((b[pos + k].toLong() and 0xff) shl (8 * k))
      pos += 8
      return v
    }

    fun string(): String {
      val len = varint().toInt()
      val s = String(b, pos, len, Charsets.UTF_8)
      pos += len
      return s
    }

    fun skip(wire: Int) {
      when (wire) {
        0 -> varint()
        1 -> pos += 8
        2 -> pos += varint().toInt()
        5 -> pos += 4
        else -> pos = end
      }
    }
  }
}
