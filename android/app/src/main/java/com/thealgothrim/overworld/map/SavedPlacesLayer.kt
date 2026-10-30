package com.thealgothrim.overworld.map

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.search.samePlace
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.drawGameIcon
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import uniffi.ferrostar.GeographicCoordinate

/** Your own places on the map, each with its game's blip, and the map layer each is drawn in. */
enum class SavedKind(val icon: GameIcon) {
  HOME(GameIcon.HOME),
  WORK(GameIcon.WORK),
  STAR(GameIcon.STAR_FILLED);

  val layer get() = "ow-saved-${name.lowercase()}"
}

/** The layers [SavedPlacesLayer] draws, for picking a place under a finger. */
val SAVED_LAYERS = SavedKind.entries.map { it.layer }.toSet()

/**
 * Home, Work and starred places on the map, the way the games mark a safehouse: GTA V's house and
 * briefcase, Red Dead's home, GTA VI's pink house. Hidden where the waypoint already stands.
 */
@Composable
@MaplibreComposable
fun SavedPlacesLayer(theme: OverworldTheme, car: Boolean, end: GeographicCoordinate?) {
  val saved by AppModule.saved.state.collectAsState()
  val context = LocalContext.current
  val waypoint = end?.let { Place("", "", it) }
  fun shown(p: Place?) = p?.takeIf { waypoint == null || !it.samePlace(waypoint) }
  val home = shown(saved.home)
  val work = shown(saved.work)?.takeIf { home == null || !it.samePlace(home) }
  val stars = saved.starred.mapNotNull { shown(it) }.filter { s -> listOfNotNull(home, work).none { it.samePlace(s) } }
  for (kind in SavedKind.entries) {
    val places =
        when (kind) {
          SavedKind.HOME -> listOfNotNull(home)
          SavedKind.WORK -> listOfNotNull(work)
          SavedKind.STAR -> stars
        }
    val source = rememberGeoJsonSource(remember(places) { features(places) })
    val painter = remember(theme.skin, kind) { savedBlip(context, theme.skin, kind) }
    // A touch bigger than the shop icons: they're yours, and the game's safehouse stands out too.
    val size = iconSize(if (car) 30.dp else 27.dp)
    SymbolLayer(
        id = kind.layer,
        source = source,
        iconImage = image(painter, size = DpSize(size, size), drawAsSdf = false),
        iconAnchor = const(SymbolAnchor.Center),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
  }
}

private fun features(places: List<Place>) =
    GeoJsonData.Features(
        FeatureCollection(
            places.map {
              Feature(
                  geometry = Point(it.coordinate.lng, it.coordinate.lat),
                  properties = buildJsonObject {
                    put("name", it.name)
                    put("detail", it.detail)
                  },
              )
            }
        )
    )

/**
 * The game's own blip where we have it (a personal build's local art, see tools/make_sprite.py),
 * else the app's icon drawn in that game's blip style.
 */
private fun savedBlip(context: Context, skin: Skin, kind: SavedKind): Painter {
  val game =
      when (skin) {
        Skin.GTA -> "gta5"
        Skin.RDR -> "rdr2"
        Skin.GTA6 -> "gta6"
      }
  val art =
      runCatching { context.assets.open("saved-blips/$game-${kind.name.lowercase()}.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
  return if (art != null) ArtPainter(art.asImageBitmap()) else DrawnBlipPainter(skin, kind)
}

/**
 * A saved-place blip drawn in code: GTA V's green pictogram with a black edge (GTA Online's colour
 * for what you own), Red Dead's ivory pictogram on a red-rimmed black disc, GTA VI's dark pictogram on the pink
 * tile its own house blip has.
 */
private class DrawnBlipPainter(private val skin: Skin, private val kind: SavedKind) : Painter() {
  override val intrinsicSize: Size = Size.Unspecified

  override fun DrawScope.onDraw() {
    val s = size.minDimension
    when (skin) {
      Skin.GTA -> {
        val pad = s * 0.12f
        val edge = s * 0.035f
        val ink = Color(0xFF359A47)
        for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to 1, -1 to 1, 1 to -1)) {
          translate(dx * edge, dy * edge) { inset(pad) { drawGameIcon(kind.icon, Color.Black) } }
        }
        inset(pad) { drawGameIcon(kind.icon, ink) }
      }
      Skin.RDR -> {
        drawCircle(Color(0xFF1B1A1A), s * 0.46f, Offset(size.width / 2, size.height / 2))
        // A waypoint-red rim sets it apart from the shop discs around it.
        drawCircle(Color(0xFFCC0000), s * 0.43f, Offset(size.width / 2, size.height / 2), style = Stroke(s * 0.07f))
        inset(s * 0.24f) { drawGameIcon(kind.icon, Color(0xFFE6E6E6)) }
      }
      Skin.GTA6 -> {
        val tile = Color(0xFFD487B4)
        val corner = CornerRadius(s * 0.2f)
        drawRoundRect(tile, Offset(s * 0.06f, s * 0.06f), Size(s * 0.88f, s * 0.88f), corner)
        drawRoundRect(Color(0xFF4A4650), Offset(s * 0.06f, s * 0.06f), Size(s * 0.88f, s * 0.88f), corner, style = Stroke(s * 0.06f))
        inset(s * 0.25f) { drawGameIcon(kind.icon, Color(0xFF26232B)) }
      }
    }
  }
}
