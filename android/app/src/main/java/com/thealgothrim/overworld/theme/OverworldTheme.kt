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

/** One map identity. The values come from prototype/themes.js via tools/export-android.mjs. */
data class OverworldTheme(
    val id: String,
    val name: String,
    val blurb: String,
    val dark: Boolean,
    val font: HudFont,
    val uppercase: Boolean,
    val land: Color,
    val routeLine: Color,
    val routeCasing: Color,
    val routeGlow: Color?,
    val puckFill: Color,
    /** Right half of the arrow, for the two-tone radar arrow; null draws a single fill. */
    val puckShade: Color?,
    val puckStroke: Color,
    /** Waypoint marker: four-lobed blip when true, otherwise a diamond. */
    val blipQuatrefoil: Boolean,
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
        HudFont.CONDENSED -> FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold))
        HudFont.SERIF -> FontFamily(Font(R.font.im_fell_english, FontWeight.Normal))
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

  companion object {
    private const val KEY = "theme"

    fun byId(id: String?): OverworldTheme = THEMES.firstOrNull { it.id == id } ?: THEMES.first()
  }
}

/** Style JSON is bundled in assets and read once per variant. */
object StyleCache {
  private val cache = ConcurrentHashMap<String, String>()

  fun json(context: Context, theme: OverworldTheme, car: Boolean): String {
    val asset = theme.styleAsset(car)
    return cache.getOrPut(asset) {
      context.assets.open(asset).bufferedReader().use { it.readText() }
    }
  }
}
