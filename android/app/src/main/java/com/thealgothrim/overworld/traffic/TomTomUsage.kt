package com.thealgothrim.overworld.traffic

import android.content.Context
import androidx.core.content.edit
import okhttp3.Interceptor
import okhttp3.Response

/**
 * How many calls this phone makes to TomTom, by kind and by day. Every copy of the app shares one
 * key and its free monthly allowance, so before the app grows the real numbers per driver have to be
 * known: this counts them on the phone (nothing is sent anywhere) and a problem report carries the
 * totals. Counted where the app's own HTTP client sends them; the traffic layer's map tiles are
 * fetched by the map itself and are not in here.
 */
object TomTomUsage : Interceptor {
  private const val DAYS_KEPT = 31L
  @Volatile private var store: android.content.SharedPreferences? = null

  fun init(context: Context) {
    val prefs = context.getSharedPreferences("tomtom_usage", Context.MODE_PRIVATE)
    store = prefs
    // Old days go, so the file stays a month long.
    val oldest = today() - DAYS_KEPT
    val stale = prefs.all.keys.filter { (it.substringBefore('|').toLongOrNull() ?: 0L) < oldest }
    if (stale.isNotEmpty()) prefs.edit { stale.forEach(::remove) }
  }

  private fun today() = System.currentTimeMillis() / 86_400_000L

  /** The kind of TomTom call a request is, or null when it isn't TomTom's. */
  fun kind(host: String, path: String): String? =
      when {
        host != "api.tomtom.com" -> null
        path.contains("/incidentDetails") -> "incidents"
        path.startsWith("/routing/") -> "route times"
        path.contains("/searchAlongRoute/") -> "search along route"
        path.contains("/nearbySearch/") -> "nearby"
        path.startsWith("/search/") -> "search"
        path.startsWith("/traffic/map/") -> "traffic tiles"
        path.startsWith("/traffic/") -> "traffic"
        else -> "other"
      }

  override fun intercept(chain: Interceptor.Chain): Response {
    val url = chain.request().url
    kind(url.host, url.encodedPath)?.let(::count)
    return chain.proceed(chain.request())
  }

  @Synchronized
  private fun count(kind: String) {
    val prefs = store ?: return
    val key = "${today()}|$kind"
    prefs.edit { putInt(key, prefs.getInt(key, 0) + 1) }
  }

  /** "today: incidents 12, search 4; last 30 days: incidents 310, ..." for a problem report. */
  fun summary(): String {
    val all = store?.all.orEmpty().mapNotNull { (k, v) -> (v as? Int)?.let { Triple(k.substringBefore('|').toLongOrNull() ?: 0L, k.substringAfter('|'), it) } }
    fun line(rows: List<Triple<Long, String, Int>>) =
        rows.groupBy({ it.second }, { it.third }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" }.ifEmpty { "none" }
    return "today: ${line(all.filter { it.first == today() })}; last 30 days: ${line(all)}"
  }
}
