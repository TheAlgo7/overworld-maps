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

enum class HudFont { CONDENSED, SERIF, TECH }

/** Which game's interface the app wears: GTA menus and HUD, or Red Dead's. */
enum class Skin { GTA, RDR }

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
    val puckFill: Color,
    /** Player marker shape: "chevron", "radar" (GTA V two-tone arrow) or "teardrop" (RDR2). */
    val puckShape: String,
    /** Right half of the GTA V radar arrow; null draws a single fill. */
    val puckShade: Color?,
    val puckStroke: Color,
    /** Waypoint marker shape: "diamond", "quatrefoil" (GTA V) or "crossring" (RDR2). */
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
) {
  /** The phone style, or the calmer car cut (wider roads, no texture or glow, fewer labels). */
  fun styleAsset(car: Boolean) = "styles/$id${if (car) "-car" else ""}.json"

  fun display(text: String) = if (uppercase) text.uppercase() else text
}

val HudFont.family: FontFamily
  get() =
      when (this) {
        HudFont.CONDENSED -> GameFonts.condensed
        HudFont.SERIF -> GameFonts.lino
        HudFont.TECH ->
            FontFamily(
                Font(R.font.chakra_petch_semibold_italic, FontWeight.SemiBold, FontStyle.Italic)
            )
      }

val HudFont.weight: FontWeight
  get() = if (this == HudFont.SERIF) FontWeight.Normal else FontWeight.SemiBold

val HudFont.style: FontStyle
  get() = if (this == HudFont.TECH) FontStyle.Italic else FontStyle.Normal

/** Plain UI text around the map. */
val UiFont =
    FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
    )

data class MapDetails(val traffic: Boolean, val signals: Boolean, val incidents: Boolean)

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

  private val _details = MutableStateFlow(
      MapDetails(
          traffic = prefs.getBoolean("detail_traffic", true),
          signals = prefs.getBoolean("detail_signals", true),
          incidents = prefs.getBoolean("detail_incidents", true),
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
    }
  }

  companion object {
    private const val KEY = "theme"
    private const val KEY_CAR_HUD = "car_game_hud"

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
   * and below the route, coloured like Google's (green free-flowing, amber slow, red jammed) in
   * tones that suit the theme.
   */
  fun json(context: Context, theme: OverworldTheme, car: Boolean, trafficTiles: String? = null): String {
    val asset = theme.styleAsset(car)
    val base =
        cache.getOrPut(asset) {
          val json = context.assets.open(asset).bufferedReader().use { it.readText() }
          val localSprite = runCatching { context.assets.list("sprites-local")?.isNotEmpty() == true }.getOrDefault(false)
          if (localSprite) json.replace("asset://sprites/overworld", "asset://sprites-local/overworld") else json
        }
    if (trafficTiles == null) return base
    return cache.getOrPut("$asset+traffic") { withTraffic(base, theme, car, trafficTiles) }
  }

  private fun withTraffic(style: String, theme: OverworldTheme, car: Boolean, tiles: String): String {
    val root = JSONObject(style)
    root.getJSONObject("sources").put(
        "traffic",
        JSONObject().put("type", "vector").put("tiles", JSONArray().put(tiles)).put("minzoom", 0).put("maxzoom", 22),
    )
    val (jam, slow, busy, free) =
        if (theme.skin == Skin.RDR) listOf("#5a0a14", "#b3120c", "#c47a12", "#5b7a38")
        else listOf("#8f0e1a", "#ff3b30", "#ffb020", "#34c759")
    val level = JSONArray("[\"to-number\", [\"get\", \"traffic_level\"], 1]")
    val layer =
        JSONObject()
            .put("id", "traffic-flow")
            .put("type", "line")
            .put("source", "traffic")
            .put("source-layer", "Traffic flow")
            .put("minzoom", 10)
            .put("layout", JSONObject().put("line-cap", "round").put("line-join", "round"))
            .put(
                "paint",
                JSONObject()
                    .put("line-color", JSONArray().put("step").put(level).put(jam).put(0.25).put(slow).put(0.5).put(busy).put(0.8).put(free))
                    .put("line-width", JSONArray("[\"interpolate\", [\"linear\"], [\"zoom\"], 10, 1.2, 14, ${if (car) 3.5 else 3}, 18, ${if (car) 9 else 7}]"))
                    // Free-flowing roads stay subtle, slow ones stand out.
                    .put("line-opacity", JSONArray().put("step").put(level).put(0.95).put(0.8).put(0.55))
                    .put("line-offset", JSONArray("[\"interpolate\", [\"linear\"], [\"zoom\"], 12, 0, 18, 3]")),
            )
    val layers = root.getJSONArray("layers")
    var insertAt = layers.length()
    for (i in 0 until layers.length()) if (layers.getJSONObject(i).optString("id") == "label-water") insertAt = i
    val out = JSONArray()
    for (i in 0 until layers.length()) {
      if (i == insertAt) out.put(layer)
      out.put(layers.get(i))
    }
    if (insertAt == layers.length()) out.put(layer)
    root.put("layers", out)
    return root.toString()
  }
}

