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

  fun json(context: Context, theme: OverworldTheme, car: Boolean): String {
    val asset = theme.styleAsset(car)
    return cache.getOrPut(asset) {
      val json = context.assets.open(asset).bufferedReader().use { it.readText() }
      val localSprite = runCatching { context.assets.list("sprites-local")?.isNotEmpty() == true }.getOrDefault(false)
      if (localSprite) json.replace("asset://sprites/overworld", "asset://sprites-local/overworld") else json
    }
  }
}
