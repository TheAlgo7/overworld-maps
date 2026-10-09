package com.thealgothrim.overworld.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.edit
import com.thealgothrim.overworld.R
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HudFont { CONDENSED, SERIF }

/** Which game's interface the app wears: GTA V's menus and HUD, Red Dead's, or GTA VI's. */
enum class Skin { GTA, RDR, GTA6 }

/** A time-of-day palette of a theme's map (GTA VI's dusk and night): its own style files. */
data class MapVariant(val page: Color, val land: Color, val dark: Boolean)

/** One map identity. The values come from prototype/themes.js via tools/export-android.mjs. */
data class OverworldTheme(
    val id: String,
    val name: String,
    val skin: Skin,
    val blurb: String,
    val dark: Boolean,
    val font: HudFont,
    val uppercase: Boolean,
    val land: Color,
    val routeLine: Color,
    val routeCasing: Color,
    val routeGlow: Color?,
    /** Metres over which the route fades in ahead of the arrow (GTA VI); 0 starts it solid. */
    val routeFade: Double,
    /**
     * GTA VI's route has a brighter edge each side, [routeCasing] in colour and this share of the
     * route's width; 0 draws the casing as a classic outline around the line.
     */
    val routeEdge: Float,
    /** Waypoint marker shape: "diamond", "quatrefoil" (GTA V), "crossring" (RDR2) or "dot" (GTA VI). */
    val blipShape: String,
    val blipFill: Color,
    val blipCenter: Color,
    val blipStroke: Color,
    val hudBg: Color,
    val hudFg: Color,
    val hudSub: Color,
    val hudAccent: Color,
    val hudGood: Color,
    val hudBorder: Color,
    val carCard: Color,
    val page: Color,
    val paperOverlay: Boolean,
    /** Time-of-day palettes by name ("dusk", "night"); the base palette is the day. */
    val variants: Map<String, MapVariant>,
    /** The map raises its buildings (GTA VI); the Layers sheet can lay them flat. */
    val buildings3d: Boolean,
) {
  /**
   * The phone style, or the calmer car cut (wider roads, no texture or glow, fewer labels), in the
   * palette for [variant] when the theme has one.
   */
  fun styleAsset(car: Boolean, variant: String? = null): String {
    val v = variant?.takeIf { it in variants }
    return "styles/$id${v?.let { "-$it" }.orEmpty()}${if (car) "-car" else ""}.json"
  }

  /** Whether the map is dark in [variant] (light text over it, not dark). */
  fun isDark(variant: String?) = variant?.let { variants[it]?.dark } ?: dark

  /** What shows behind the map while it loads, in [variant]. */
  fun pageFor(variant: String?) = variant?.let { variants[it]?.page } ?: page

  fun display(text: String) = if (uppercase) text.uppercase() else text
}

val HudFont.family: FontFamily
  get() =
      when (this) {
        HudFont.CONDENSED -> GameFonts.condensed
        HudFont.SERIF -> GameFonts.lino
      }

val HudFont.weight: FontWeight
  get() = if (this == HudFont.SERIF) FontWeight.Normal else FontWeight.SemiBold

val HudFont.style: FontStyle
  get() = FontStyle.Normal

/** Plain UI text around the map. */
val UiFont =
    FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
    )

/**
 * Map details the Layers sheet switches. [buildings3d]: raised buildings in themes that have them
 * (GTA VI); off draws them flat, which is lighter work for the phone.
 */
data class MapDetails(val traffic: Boolean, val signals: Boolean, val incidents: Boolean, val buildings3d: Boolean = true)

/** The time-of-day setting for themes with day, dusk and night palettes. */
enum class MapTime(val label: String) {
  AUTO("Auto"),
  DAY("Day"),
  DUSK("Dusk"),
  NIGHT("Night");

  /** The palette to use, given what the sun says ([bySun]): null is the day palette. */
  fun variant(bySun: String?): String? =
      when (this) {
        AUTO -> bySun
        DAY -> null
        DUSK -> "dusk"
        NIGHT -> "night"
      }

  fun next() = entries[(ordinal + 1) % entries.size]
}

/** The selected theme, shared by the phone and the Android Auto screen. */
class ThemeStore(context: Context) {
  private val prefs = context.getSharedPreferences("overworld", Context.MODE_PRIVATE)
  private val _theme = MutableStateFlow(byId(prefs.getString(KEY, null)))
  val theme: StateFlow<OverworldTheme> = _theme.asStateFlow()

  fun select(id: String) {
    _theme.value = byId(id)
    prefs.edit { putString(KEY, id) }
  }

  fun cycle() {
    val i = THEMES.indexOfFirst { it.id == _theme.value.id }
    select(THEMES[(i + 1) % THEMES.size].id)
  }

  private val _carGameHud = MutableStateFlow(prefs.getBoolean(KEY_CAR_HUD, true))
  /**
   * Car screen style while navigating. true: our own game HUD drawn on the map (Android Auto's
   * turn and ETA cards are not sent). false: Android Auto's standard cards, themed where allowed.
   */
  val carGameHud: StateFlow<Boolean> = _carGameHud.asStateFlow()

  fun setCarGameHud(on: Boolean) {
    _carGameHud.value = on
    prefs.edit { putBoolean(KEY_CAR_HUD, on) }
  }

  private val _mapTime =
      MutableStateFlow(runCatching { MapTime.valueOf(prefs.getString(KEY_MAP_TIME, null) ?: "") }.getOrDefault(MapTime.AUTO))
  /** Which time-of-day palette themes like GTA VI use: by the sun, or held at one. */
  val mapTime: StateFlow<MapTime> = _mapTime.asStateFlow()

  fun setMapTime(time: MapTime) {
    _mapTime.value = time
    prefs.edit { putString(KEY_MAP_TIME, time.name) }
  }

  private val _details = MutableStateFlow(
      MapDetails(
          traffic = prefs.getBoolean("detail_traffic", false),
          signals = prefs.getBoolean("detail_signals", true),
          incidents = prefs.getBoolean("detail_incidents", true),
          buildings3d = prefs.getBoolean("detail_buildings3d", true),
      )
  )
  /** Map details, like Google Maps' layers: live traffic, traffic lights and incidents. */
  val details: StateFlow<MapDetails> = _details.asStateFlow()

  fun setDetails(d: MapDetails) {
    _details.value = d
    prefs.edit {
      putBoolean("detail_traffic", d.traffic)
      putBoolean("detail_signals", d.signals)
      putBoolean("detail_incidents", d.incidents)
      putBoolean("detail_buildings3d", d.buildings3d)
    }
  }

  companion object {
    private const val KEY = "theme"
    private const val KEY_CAR_HUD = "car_game_hud"
    private const val KEY_MAP_TIME = "map_time"

    /** Ids before the 2026-09-28 rename. */
    private val LEGACY = mapOf("metro" to "gta5", "frontier" to "rdr2", "vice" to "gta6")

    fun byId(id: String?): OverworldTheme {
      val key = LEGACY[id] ?: id
      return THEMES.firstOrNull { it.id == key } ?: THEMES.first()
    }
  }
}

/**
 * Style JSON is bundled in assets and read once per variant. Personal builds may carry a local
 * sprite with the RDR2 POI blips (src/local, gitignored); when it is there the styles use it.
 */
object StyleCache {
  private val cache = ConcurrentHashMap<String, String>()

  /**
   * [trafficTiles]: TomTom flow tiles URL. When set, a live-traffic layer is added above the roads
   * and below the route: amber where roads are slower than usual, red where they are jammed, in
   * tones that suit the theme. [variant]: the time-of-day palette (see MapTime), if the theme has it.
   * [flat]: draw the theme's raised buildings flat (the Layers sheet's 3D buildings switch).
   */
  fun json(
      context: Context,
      theme: OverworldTheme,
      car: Boolean,
      trafficTiles: String? = null,
      variant: String? = null,
      flat: Boolean = false,
  ): String {
    val asset = theme.styleAsset(car, variant)
    val base =
        cache.getOrPut(asset) {
          val json = context.assets.open(asset).bufferedReader().use { it.readText() }
          val localSprite = runCatching { context.assets.list("sprites-local")?.isNotEmpty() == true }.getOrDefault(false)
          if (localSprite) json.replace("asset://sprites/overworld", "asset://sprites-local/overworld") else json
        }
    val flatten = flat && theme.buildings3d
    val laid = if (flatten) cache.getOrPut("$asset+flat") { flatBuildings(base) } else base
    if (trafficTiles == null) return laid
    return cache.getOrPut("$asset${if (flatten) "+flat" else ""}+traffic") { withTraffic(laid, theme, car, trafficTiles) }
  }

  /** The style without its raised buildings: the flat footprints stay at every zoom instead. */
  private fun flatBuildings(style: String): String {
    val root = JSONObject(style)
    val layers = root.getJSONArray("layers")
    val out = JSONArray()
    for (i in 0 until layers.length()) {
      val layer = layers.getJSONObject(i)
      when (layer.optString("id")) {
        "building-3d" -> continue
        "building" -> layer.remove("maxzoom")
      }
      out.put(layer)
    }
    root.put("layers", out)
    return root.toString()
  }

  private fun withTraffic(style: String, theme: OverworldTheme, car: Boolean, tiles: String): String {
    val root = JSONObject(style)
    root.getJSONObject("sources").put(
        "traffic",
        JSONObject().put("type", "vector").put("tiles", JSONArray().put(tiles)).put("minzoom", 0).put("maxzoom", 22),
    )
    val (jam, slow, busy) =
        if (theme.skin == Skin.RDR) listOf("#5a0a14", "#b3120c", "#c47a12")
        else listOf("#8f0e1a", "#ff3b30", "#ffb020")
    val level = JSONArray("[\"to-number\", [\"get\", \"traffic_level\"], 1]")
    // Like Google: highways from city zoom, arterials and then streets as you zoom in. All of them
    // at once turned the whole city orange. Only roads slower than usual are drawn: free-flowing
    // green on every road buried the game look, and the point is to see where the traffic is.
    val highways = listOf("International road", "Major road")
    val arterials = listOf("Secondary road", "Connecting road")
    /** [types]: the road types this layer draws, or null for every type not listed in [others]. */
    fun trafficLayer(id: String, minzoom: Double, types: List<String>?, others: List<String> = emptyList()): JSONObject =
        JSONObject()
            .put("id", id)
            .put("type", "line")
            .put("source", "traffic")
            .put("source-layer", "Traffic flow")
            .put("minzoom", minzoom)
            .put(
                "filter",
                JSONArray()
                    .put("all")
                    .put(JSONArray().put("<").put(level).put(0.8))
                    .put(
                        JSONArray()
                            .put("match")
                            .put(JSONArray("[\"get\", \"road_type\"]"))
                            .put(JSONArray(types ?: others))
                            .put(types != null)
                            .put(types == null)
                    ),
            )
            .put("layout", JSONObject().put("line-cap", "round").put("line-join", "round"))
            .put(
                "paint",
                JSONObject()
                    .put("line-color", JSONArray().put("step").put(level).put(jam).put(0.25).put(slow).put(0.5).put(busy))
                    .put("line-width", JSONArray("[\"interpolate\", [\"linear\"], [\"zoom\"], 10, 1, 13, 2, 15, ${if (car) 4 else 3.5}, 18, ${if (car) 9 else 7}]"))
                    .put("line-opacity", JSONArray("[\"interpolate\", [\"linear\"], [\"zoom\"], 10, 0.7, 14, 0.9]"))
                    .put("line-offset", JSONArray("[\"interpolate\", [\"linear\"], [\"zoom\"], 12, 0, 18, 3]")),
            )
    val added =
        listOf(
            trafficLayer("traffic-flow", 10.0, highways),
            trafficLayer("traffic-flow-arterial", 13.0, arterials),
            trafficLayer("traffic-flow-local", 14.5, null, others = highways + arterials),
        )
    val layers = root.getJSONArray("layers")
    var insertAt = layers.length()
    for (i in 0 until layers.length()) if (layers.getJSONObject(i).optString("id") == "label-water") insertAt = i
    val out = JSONArray()
    for (i in 0 until layers.length()) {
      if (i == insertAt) added.forEach { out.put(it) }
      out.put(layers.get(i))
    }
    if (insertAt == layers.length()) added.forEach { out.put(it) }
    root.put("layers", out)
    return root.toString()
  }
}

