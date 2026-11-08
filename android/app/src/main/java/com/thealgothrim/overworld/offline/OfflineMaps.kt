package com.thealgothrim.overworld.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.notify.Notices
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/** A downloaded (or downloading) map area. */
data class OfflineArea(
    val id: Long,
    val name: String,
    val north: Double,
    val east: Double,
    val south: Double,
    val west: Double,
    val savedAt: Long,
    val bytes: Long = 0,
    val done: Long = 0,
    val required: Long = 0,
    val complete: Boolean = false,
    val downloading: Boolean = false,
    val error: String? = null,
) {
  val megabytes: Long
    get() = bytes / 1_000_000

  val progress: Float
    get() = if (required <= 0) 0f else (done.toFloat() / required).coerceIn(0f, 1f)
}

/**
 * Offline maps, like Google's: the area on screen is downloaded into MapLibre's offline database,
 * and the map then draws there without internet, in every world (they all read the same
 * OpenFreeMap tiles). Routes and search still need a connection. Tiles are fetched to zoom 14, the
 * deepest OpenFreeMap has; closer zooms are drawn from those.
 */
class OfflineMaps(private val context: Context) {
  private val _areas = MutableStateFlow<List<OfflineArea>>(emptyList())
  val areas: StateFlow<List<OfflineArea>> = _areas.asStateFlow()
  private val regions = mutableMapOf<Long, OfflineRegion>()

  private val manager: OfflineManager by lazy {
    MapLibre.getInstance(context)
    OfflineManager.getInstance(context)
  }

  /**
   * A style holding just the map's tile source: enough for MapLibre to know which tiles to fetch.
   * The offline downloader only fetches over the network (a file:// style was refused: "Unable to
   * parse resourceUrl", 8 Nov), so the style is put into MapLibre's own database under an https
   * address first, where the downloader finds it without asking any server.
   */
  private val styleUrl: String = STYLE_URL

  private fun putStyle() {
    val style = JSONObject().put("version", 8).put("sources", JSONObject().put("omt", JSONObject().put("type", "vector").put("url", TILES))).put("layers", org.json.JSONArray()).toString()
    val now = System.currentTimeMillis()
    runCatching { manager.putResourceWithUrl(STYLE_URL, style.toByteArray(), now, now + TEN_YEARS, null, false) }.onFailure { Log.w(TAG, "offline style", it) }
  }

  /** Read the list (call from the main thread; MapLibre answers there). */
  fun refresh() {
    runCatching {
          manager.listOfflineRegions(
              object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                  regions.clear()
                  offlineRegions.orEmpty().forEach { regions[it.id] = it }
                  _areas.value = regions.values.map { area(it) }.sortedByDescending { it.savedAt }
                  regions.values.forEach { r -> r.getStatus(statusCallback(r)) }
                }

                override fun onError(error: String) {
                  Log.w(TAG, "offline list: $error")
                }
              }
          )
        }
        .onFailure { Log.w(TAG, "offline maps unavailable", it) }
  }

  /** Whether downloading now is allowed (Wi-Fi only, unless that setting is off). */
  fun canDownloadNow(): Boolean {
    if (!AppModule.prefs.offlineWifiOnly.value) return true
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
  }

  /** Download the box north/east/south/west as [name]. [onDone] says whether it started. */
  fun download(name: String, north: Double, east: Double, south: Double, west: Double, onDone: (String?) -> Unit = {}) {
    val tiles = tileCount(north, east, south, west)
    if (tiles > MAX_TILES) return onDone("That area is too big to download at once. Zoom in a little.")
    putStyle()
    val definition = OfflineTilePyramidRegionDefinition(styleUrl, LatLngBounds.from(north, east, south, west), MIN_ZOOM, MAX_ZOOM, context.resources.displayMetrics.density)
    val metadata = JSONObject().put("name", name).put("at", System.currentTimeMillis()).toString().toByteArray()
    runCatching {
          manager.createOfflineRegion(
              definition,
              metadata,
              object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                  regions[offlineRegion.id] = offlineRegion
                  watch(offlineRegion)
                  offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                  publish(area(offlineRegion).copy(downloading = true))
                  onDone(null)
                }

                override fun onError(error: String) {
                  Log.w(TAG, "offline create: $error")
                  onDone("Couldn't start the download: $error")
                }
              },
          )
        }
        .onFailure { onDone("Couldn't start the download.") }
  }

  fun pause(id: Long) {
    regions[id]?.setDownloadState(OfflineRegion.STATE_INACTIVE)
    _areas.value.firstOrNull { it.id == id }?.let { publish(it.copy(downloading = false)) }
  }

  /** Fetch the area again from the server (OpenFreeMap updates its tiles every week). */
  fun update(id: Long) {
    val r = regions[id] ?: return
    putStyle()
    r.invalidate(
        object : OfflineRegion.OfflineRegionInvalidateCallback {
          override fun onInvalidate() {
            watch(r)
            r.setDownloadState(OfflineRegion.STATE_ACTIVE)
            val meta = runCatching { JSONObject(String(r.metadata)) }.getOrDefault(JSONObject()).put("at", System.currentTimeMillis())
            r.updateMetadata(meta.toString().toByteArray(), metadataCallback(r))
            _areas.value.firstOrNull { it.id == id }?.let { publish(it.copy(downloading = true, complete = false)) }
          }

          override fun onError(error: String) {
            Log.w(TAG, "offline update: $error")
          }
        }
    )
  }

  fun rename(id: Long, name: String) {
    val r = regions[id] ?: return
    val meta = runCatching { JSONObject(String(r.metadata)) }.getOrDefault(JSONObject()).put("name", name)
    r.updateMetadata(meta.toString().toByteArray(), metadataCallback(r))
  }

  fun delete(id: Long) {
    val r = regions.remove(id) ?: return
    r.setDownloadState(OfflineRegion.STATE_INACTIVE)
    r.delete(
        object : OfflineRegion.OfflineRegionDeleteCallback {
          override fun onDelete() {
            _areas.value = _areas.value.filterNot { it.id == id }
          }

          override fun onError(error: String) {
            Log.w(TAG, "offline delete: $error")
          }
        }
    )
  }

  /** Areas older than a month refresh by themselves, on Wi-Fi when that's set (Settings > Offline). */
  fun autoUpdate() {
    if (!AppModule.prefs.offlineAutoUpdate.value || !canDownloadNow()) return
    val month = 30L * 24 * 3_600_000
    _areas.value.filter { System.currentTimeMillis() - it.savedAt > month && !it.downloading }.forEach { update(it.id) }
  }

  private fun metadataCallback(r: OfflineRegion) =
      object : OfflineRegion.OfflineRegionUpdateMetadataCallback {
        override fun onUpdate(metadata: ByteArray) {
          _areas.value.firstOrNull { it.id == r.id }?.let { old -> publish(area(r).copy(bytes = old.bytes, done = old.done, required = old.required, complete = old.complete, downloading = old.downloading)) }
        }

        override fun onError(error: String) {
          Log.w(TAG, "offline metadata: $error")
        }
      }

  private fun statusCallback(r: OfflineRegion) =
      object : OfflineRegion.OfflineRegionStatusCallback {
        override fun onStatus(status: OfflineRegionStatus?) {
          status?.let { publish(withStatus(area(r), it)) }
        }

        override fun onError(error: String?) {}
      }

  private var notified = mutableSetOf<Long>()

  private fun watch(r: OfflineRegion) {
    r.setObserver(
        object : OfflineRegion.OfflineRegionObserver {
          override fun onStatusChanged(status: OfflineRegionStatus) {
            val a = withStatus(area(r), status)
            publish(a)
            if (status.isComplete) {
              r.setDownloadState(OfflineRegion.STATE_INACTIVE)
              if (notified.add(r.id)) Notices.offlineReady(context, a.name, a.megabytes)
            }
          }

          override fun onError(error: OfflineRegionError) {
            Log.w(TAG, "offline ${r.id}: ${error.reason} ${error.message}")
            _areas.value.firstOrNull { it.id == r.id }?.let { publish(it.copy(error = if (error.reason == OfflineRegionError.REASON_CONNECTION) "Waiting for internet" else "Some of it didn't download")) }
          }

          override fun mapboxTileCountLimitExceeded(limit: Long) {}
        }
    )
  }

  private fun withStatus(a: OfflineArea, s: OfflineRegionStatus) =
      a.copy(bytes = s.completedResourceSize, done = s.completedResourceCount, required = s.requiredResourceCount, complete = s.isComplete, downloading = s.downloadState == OfflineRegion.STATE_ACTIVE && !s.isComplete)

  private fun publish(a: OfflineArea) {
    _areas.value = (listOf(a) + _areas.value.filterNot { it.id == a.id }).sortedByDescending { it.savedAt }
  }

  private fun area(r: OfflineRegion): OfflineArea {
    val meta = runCatching { JSONObject(String(r.metadata)) }.getOrDefault(JSONObject())
    val b = (r.definition as? OfflineTilePyramidRegionDefinition)?.bounds
    return OfflineArea(
        id = r.id,
        name = meta.optString("name").ifBlank { "Map ${r.id}" },
        north = b?.latitudeNorth ?: 0.0,
        east = b?.longitudeEast ?: 0.0,
        south = b?.latitudeSouth ?: 0.0,
        west = b?.longitudeWest ?: 0.0,
        savedAt = meta.optLong("at"),
    )
  }

  companion object {
    private const val TAG = "OfflineMaps"
    const val TILES = "https://tiles.openfreemap.org/planet"
    /** The offline style's address in MapLibre's database (never fetched: see [putStyle]). */
    private const val STYLE_URL = "https://overworld.thealgothrim.com/offline/style.json"
    private const val TEN_YEARS = 10L * 365 * 24 * 3_600_000
    const val MIN_ZOOM = 4.0
    const val MAX_ZOOM = 14.0
    /** About a 200 km square: a city and its surroundings. */
    const val MAX_TILES = 12_000L
    /** OpenFreeMap's city tiles average about this much (rural ones far less). */
    private const val BYTES_PER_TILE = 45_000L

    /** Tiles from zoom 4 to 14 covering the box. */
    fun tileCount(north: Double, east: Double, south: Double, west: Double): Long {
      var total = 0L
      for (z in MIN_ZOOM.toInt()..MAX_ZOOM.toInt()) {
        val n = 1 shl z
        fun x(lng: Double) = floor((lng + 180.0) / 360.0 * n).toLong().coerceIn(0, n - 1L)
        fun y(lat: Double): Long {
          val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
          return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n).toLong().coerceIn(0, n - 1L)
        }
        total += (x(east) - x(west) + 1) * (y(south) - y(north) + 1)
      }
      return total
    }

    /** A rough size for the box, in MB, for the "about N MB" line before downloading. */
    fun estimateMegabytes(north: Double, east: Double, south: Double, west: Double): Long = (tileCount(north, east, south, west) * BYTES_PER_TILE / 1_000_000).coerceAtLeast(1)
  }
}
