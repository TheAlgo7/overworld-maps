package com.thealgothrim.overworld.map

import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.thealgothrim.overworld.AppModule
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Position

/**
 * Red Dead's contour lines from real ground heights: the game's map draws fine contours all over
 * (Gaurav's captures 13, 17, 25), OpenFreeMap has none, so they're traced on the phone the way
 * maplibre-contour (github.com/onthegomap/maplibre-contour) does it in a browser: elevation tiles
 * decoded to heights, smoothed, traced into isolines with marching squares, joined into lines.
 *
 * Heights: AWS's open Terrain Tiles (Mapzen's Terrarium format, SRTM over India; free, no key),
 * at zoom 12, about 33 m a pixel in Delhi. One block of 3 x 3 of those tiles round the camera is
 * traced at a time, in the background, and handed to the map as plain data. (MapLibre's computed
 * sources crashed the app when the style changed while a tile was still being traced.) Delhi is
 * flat, so the lines gather where the ground really rises: the Ridge, the Yamuna's banks, the
 * Aravalli edge towards Gurgaon.
 */
object Contours {
  private const val TAG = "Contours"
  const val DEM_ZOOM = 12
  private const val TILE = 256
  private const val URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/%d/%d/%d.png"
  /** Pixels of the next tiles' heights kept round the block, so the smoothing runs on past its edge. */
  private const val MARGIN = 6
  /** Metres between lines; every second one is a "major" line, the only ones drawn zoomed out. */
  private const val STEP = 5.0

  /**
   * The traced lines of one block as GeoJSON text, major (every 10 m) and minor (the 5 m between).
   * Written here, in the background: handed to the map as objects, they were turned into GeoJSON
   * on the main thread, a stall the car screen showed as a hitch.
   */
  class Block(val major: String, val minor: String)

  private val tiles = LruCache<Long, FloatArray>(40)
  private val failedAt = ConcurrentHashMap<Long, Long>()
  private val blocks = LruCache<Long, Block>(4)

  /** The elevation tile (x, y) at [DEM_ZOOM] under [lng], [lat]. */
  fun tileAt(lng: Double, lat: Double): Pair<Int, Int> {
    val n = 1 shl DEM_ZOOM
    return Pair((xOf(lng) * n).toInt().coerceIn(0, n - 1), (yOf(lat) * n).toInt().coerceIn(0, n - 1))
  }

  /** The lines of the 3 x 3 block round elevation tile ([cx], [cy]), or null if its heights can't load. */
  fun block(cx: Int, cy: Int): Block? {
    val key = (cx.toLong() shl 32) or cy.toLong()
    blocks.get(key)?.let { return it }
    return runCatching { trace(cx, cy) }
        .onFailure { Log.w(TAG, "contours failed", it) }
        .getOrNull()
        ?.also { blocks.put(key, it) }
  }

  private fun trace(cx: Int, cy: Int): Block? {
    val x0 = (cx - 1) * TILE - MARGIN
    val y0 = (cy - 1) * TILE - MARGIN
    val w = 3 * TILE + 2 * MARGIN
    val h = w
    val grid = FloatArray(w * h)
    for (ty in cy - 2..cy + 2) for (tx in cx - 2..cx + 2) {
      val inner = tx in cx - 1..cx + 1 && ty in cy - 1..cy + 1
      // The block's own nine are needed; the ring round it only lends a few pixels at the edge.
      val dem = tile(tx, ty) ?: if (inner) return null else continue
      for (gy in maxOf(y0, ty * TILE)..minOf(y0 + h - 1, ty * TILE + TILE - 1)) {
        for (gx in maxOf(x0, tx * TILE)..minOf(x0 + w - 1, tx * TILE + TILE - 1)) {
          grid[(gy - y0) * w + (gx - x0)] = dem[(gy - ty * TILE) * TILE + (gx - tx * TILE)]
        }
      }
    }
    fillMissingRing(grid, w, h, cx, cy, x0, y0)
    // Smoothed, so the radar data's speckle over buildings doesn't turn into rings.
    val g = blur(grid, w, h, 1.8)
    var lo = Float.MAX_VALUE
    var hi = -Float.MAX_VALUE
    for (v in g) { if (v < lo) lo = v; if (v > hi) hi = v }
    val world = (TILE shl DEM_ZOOM).toDouble()
    val major = StringBuilder()
    val minor = StringBuilder()
    var level = ceil(lo / STEP) * STEP
    while (level <= hi) {
      val isMajor = Math.floorMod(Math.round(level).toInt(), 10) == 0
      for (line in isolines(g, w, h, level.toFloat())) {
        val pts = chaikin(simplify(line, 0.12f), 2)
        if (pts.size < 4) continue
        val out = if (isMajor) major else minor
        if (out.isNotEmpty()) out.append(',')
        out.append("{\"type\":\"Feature\",\"properties\":{\"ele\":").append(level).append("},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        var i = 0
        while (i < pts.size) {
          val p = position(x0 + pts[i], y0 + pts[i + 1], world)
          if (i > 0) out.append(',')
          out.append('[').append("%.6f".format(java.util.Locale.ROOT, p.longitude)).append(',').append("%.6f".format(java.util.Locale.ROOT, p.latitude)).append(']')
          i += 2
        }
        out.append("]}}")
      }
      level += STEP
    }
    fun collection(features: StringBuilder) = "{\"type\":\"FeatureCollection\",\"features\":[$features]}"
    return Block(collection(major), collection(minor))
  }

  /** Where a ring tile didn't load, its margin copies the block's nearest edge. */
  private fun fillMissingRing(g: FloatArray, w: Int, h: Int, cx: Int, cy: Int, x0: Int, y0: Int) {
    val lo = MARGIN
    val hiX = w - MARGIN - 1
    val hiY = h - MARGIN - 1
    for (y in 0 until h) for (x in 0 until w) {
      if (x in lo..hiX && y in lo..hiY) continue
      if (g[y * w + x] != 0f) continue
      g[y * w + x] = g[y.coerceIn(lo, hiY) * w + x.coerceIn(lo, hiX)]
    }
  }

  /** One elevation tile's heights in metres, from the cache or the network. */
  private fun tile(tx: Int, ty: Int): FloatArray? {
    val n = 1 shl DEM_ZOOM
    if (ty < 0 || ty >= n) return null
    val x = Math.floorMod(tx, n)
    val key = (x.toLong() shl 32) or ty.toLong()
    tiles.get(key)?.let { return it }
    failedAt[key]?.let { if (System.currentTimeMillis() - it < 60_000) return null }
    return runCatching {
      val request = Request.Builder().url(URL.format(DEM_ZOOM, x, ty)).build()
      AppModule.httpClient.newCall(request).execute().use { response ->
        check(response.isSuccessful) { "HTTP ${response.code}" }
        val bytes = response.body!!.bytes()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val px = IntArray(TILE * TILE)
        bitmap.getPixels(px, 0, TILE, 0, 0, TILE, TILE)
        bitmap.recycle()
        // Terrarium: height = R * 256 + G + B / 256 - 32768.
        FloatArray(TILE * TILE) { i ->
          val c = px[i]
          ((c shr 16 and 0xFF) * 256f + (c shr 8 and 0xFF) + (c and 0xFF) / 256f) - 32768f
        }
      }
    }.onSuccess { tiles.put(key, it); failedAt.remove(key) }
        .onFailure { failedAt[key] = System.currentTimeMillis(); Log.w(TAG, "elevation tile $x/$ty: ${it.message}") }
        .getOrNull()
  }

  /**
   * Every line where the ground crosses [level], in grid pixels (x, y, x, y, ...). Marching squares
   * puts one segment in each cell the level passes through; segments that share a cell edge are
   * joined end to end into lines.
   */
  private fun isolines(g: FloatArray, w: Int, h: Int, level: Float): List<FloatArray> {
    // Cell edges: horizontal (i, j)-(i+1, j) is 2 * (j * w + i), vertical (i, j)-(i, j+1) one more.
    fun hEdge(i: Int, j: Int) = 2L * (j.toLong() * w + i)
    fun vEdge(i: Int, j: Int) = 2L * (j.toLong() * w + i) + 1
    val point = HashMap<Long, FloatArray>()
    val segA = ArrayList<Long>()
    val segB = ArrayList<Long>()
    fun at(x: Int, y: Int) = g[y * w + x]
    for (j in 0 until h - 1) {
      for (i in 0 until w - 1) {
        val a = at(i, j)
        val b = at(i + 1, j)
        val c = at(i + 1, j + 1)
        val d = at(i, j + 1)
        val case = (if (a > level) 8 else 0) or (if (b > level) 4 else 0) or (if (c > level) 2 else 0) or (if (d > level) 1 else 0)
        if (case == 0 || case == 15) continue
        fun t(p: Float, q: Float) = if (q == p) 0.5f else ((level - p) / (q - p)).coerceIn(0f, 1f)
        val top = hEdge(i, j)
        val bottom = hEdge(i, j + 1)
        val left = vEdge(i, j)
        val right = vEdge(i + 1, j)
        fun seg(p: Long, q: Long) {
          for (e in longArrayOf(p, q)) {
            if (e !in point) {
              point[e] =
                  when (e) {
                    top -> floatArrayOf(i + t(a, b), j.toFloat())
                    bottom -> floatArrayOf(i + t(d, c), j + 1f)
                    left -> floatArrayOf(i.toFloat(), j + t(a, d))
                    else -> floatArrayOf(i + 1f, j + t(b, c))
                  }
            }
          }
          segA += p
          segB += q
        }
        val centreAbove = (a + b + c + d) / 4f > level
        when (case) {
          1, 14 -> seg(left, bottom)
          2, 13 -> seg(bottom, right)
          3, 12 -> seg(left, right)
          4, 11 -> seg(top, right)
          6, 9 -> seg(top, bottom)
          7, 8 -> seg(left, top)
          5 -> if (centreAbove) { seg(left, top); seg(bottom, right) } else { seg(top, right); seg(left, bottom) }
          10 -> if (centreAbove) { seg(top, right); seg(left, bottom) } else { seg(left, top); seg(bottom, right) }
        }
      }
    }
    // Join: each edge point has at most two segments.
    val byEdge = HashMap<Long, IntArray>()
    for (s in segA.indices) {
      for (e in longArrayOf(segA[s], segB[s])) {
        val slots = byEdge.getOrPut(e) { intArrayOf(-1, -1) }
        if (slots[0] == -1) slots[0] = s else slots[1] = s
      }
    }
    val used = BooleanArray(segA.size)
    val lines = ArrayList<FloatArray>()
    for (s in segA.indices) {
      if (used[s]) continue
      used[s] = true
      val chain = ArrayDeque<Long>()
      chain.addLast(segA[s])
      chain.addLast(segB[s])
      // Forwards from B, then backwards from A.
      for (forward in listOf(true, false)) {
        var edge = if (forward) chain.last() else chain.first()
        while (true) {
          val next = byEdge[edge]?.firstOrNull { it != -1 && !used[it] } ?: break
          used[next] = true
          val other = if (segA[next] == edge) segB[next] else segA[next]
          if (forward) chain.addLast(other) else chain.addFirst(other)
          edge = other
        }
      }
      val out = FloatArray(chain.size * 2)
      chain.forEachIndexed { k, e -> point[e]!!.let { out[2 * k] = it[0]; out[2 * k + 1] = it[1] } }
      lines += out
    }
    return lines
  }

  /** Douglas-Peucker: drops points closer than [tolerance] grid pixels to the line. */
  private fun simplify(p: FloatArray, tolerance: Float): FloatArray {
    val n = p.size / 2
    if (n < 3) return p
    val keep = BooleanArray(n)
    keep[0] = true
    keep[n - 1] = true
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to n - 1)
    while (stack.isNotEmpty()) {
      val (s, e) = stack.removeLast()
      var far = -1
      var dist = tolerance
      val ax = p[2 * s]; val ay = p[2 * s + 1]; val bx = p[2 * e]; val by = p[2 * e + 1]
      val len = hypot(bx - ax, by - ay)
      for (k in s + 1 until e) {
        val px = p[2 * k]; val py = p[2 * k + 1]
        val d = if (len < 1e-6f) hypot(px - ax, py - ay) else kotlin.math.abs((bx - ax) * (ay - py) - (ax - px) * (by - ay)) / len
        if (d > dist) { dist = d; far = k }
      }
      if (far != -1) {
        keep[far] = true
        stack.addLast(s to far)
        stack.addLast(far to e)
      }
    }
    val out = FloatArray(keep.count { it } * 2)
    var o = 0
    for (k in 0 until n) if (keep[k]) { out[o++] = p[2 * k]; out[o++] = p[2 * k + 1] }
    return out
  }

  /** Chaikin's corner cutting, [rounds] times: a line drawn by hand, not along the grid. */
  private fun chaikin(p: FloatArray, rounds: Int): FloatArray {
    var cur = p
    repeat(rounds) {
      val n = cur.size / 2
      if (n < 3) return cur
      val closed = cur[0] == cur[cur.size - 2] && cur[1] == cur[cur.size - 1]
      val out = ArrayList<Float>(n * 4)
      if (!closed) { out += cur[0]; out += cur[1] }
      for (k in 0 until n - 1) {
        val x0 = cur[2 * k]; val y0 = cur[2 * k + 1]; val x1 = cur[2 * k + 2]; val y1 = cur[2 * k + 3]
        out += 0.75f * x0 + 0.25f * x1; out += 0.75f * y0 + 0.25f * y1
        out += 0.25f * x0 + 0.75f * x1; out += 0.25f * y0 + 0.75f * y1
      }
      if (closed) { out += out[0]; out += out[1] } else { out += cur[cur.size - 2]; out += cur[cur.size - 1] }
      cur = out.toFloatArray()
    }
    return cur
  }

  private fun blur(g: FloatArray, w: Int, h: Int, sigma: Double): FloatArray {
    val r = ceil(sigma * 2.5).toInt()
    val k = FloatArray(2 * r + 1) { i -> exp(-((i - r) * (i - r)) / (2 * sigma * sigma)).toFloat() }
    val sum = k.sum()
    for (i in k.indices) k[i] /= sum
    val tmp = FloatArray(w * h)
    val out = FloatArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
      var s = 0f
      for (i in -r..r) s += k[i + r] * g[y * w + (x + i).coerceIn(0, w - 1)]
      tmp[y * w + x] = s
    }
    for (y in 0 until h) for (x in 0 until w) {
      var s = 0f
      for (i in -r..r) s += k[i + r] * tmp[(y + i).coerceIn(0, h - 1) * w + x]
      out[y * w + x] = s
    }
    return out
  }

  private fun xOf(lng: Double) = (lng + 180.0) / 360.0

  private fun yOf(lat: Double): Double {
    val r = Math.toRadians(lat)
    return (1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2
  }

  private fun position(gx: Float, gy: Float, world: Double): Position {
    val lng = gx / world * 360.0 - 180.0
    val lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * gy / world))))
    return Position(lng, lat)
  }
}

private const val NO_LINES = "{\"type\":\"FeatureCollection\",\"features\":[]}"

/**
 * Red Dead's contours on the map, under the water and every road: fine lines a shade darker than
 * the paper (#c4ab8a on #dbc099, measured from captures 13 and 17), fainter still on the radar.
 * Every 10 m from zoom 12, the 5 m between them from zoom 14.
 */
@Composable
@MaplibreComposable
fun ContourLayer(camera: CameraState, radar: Boolean, car: Boolean) {
  var block by remember { mutableStateOf<Contours.Block?>(null) }
  LaunchedEffect(camera) {
    snapshotFlow { camera.position.let { p -> if (p.zoom < 11.5) null else Contours.tileAt(p.target.longitude, p.target.latitude) } }
        .distinctUntilChanged()
        .collectLatest { tile -> if (tile != null) withContext(Dispatchers.Default) { Contours.block(tile.first, tile.second) }?.let { block = it } }
  }
  val major = rememberGeoJsonSource(remember(block) { GeoJsonData.JsonString(block?.major ?: NO_LINES) })
  val minor = rememberGeoJsonSource(remember(block) { GeoJsonData.JsonString(block?.minor ?: NO_LINES) })
  val color = const(if (radar) Color(0xFF5E5748) else Color(0xFFC4AB8A))
  val width = interpolate(linear(), zoom(), 12 to const(Dp(if (car) 0.8f else 0.6f)), 17 to const(Dp(if (car) 1.6f else 1.3f)))
  Anchor.Below("water") {
    LineLayer(id = "ow-contours-minor", source = minor, minZoom = 14f, color = color, width = width, cap = const(LineCap.Round), join = const(LineJoin.Round))
    LineLayer(id = "ow-contours", source = major, minZoom = 12f, color = color, width = width, cap = const(LineCap.Round), join = const(LineJoin.Round))
  }
}
