package com.thealgothrim.overworld.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.GameIconView
import com.thealgothrim.overworld.ui.skin.SkinDivider
import com.thealgothrim.overworld.ui.skin.SkinPanel
import com.thealgothrim.overworld.ui.skin.SkinRoundButton
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.spec
import kotlinx.serialization.json.JsonPrimitive
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Point
import uniffi.ferrostar.GeographicCoordinate

/** The kinds the Index sorts places into, each with its world icon. */
enum class IndexKind(val label: String, val icon: GameIcon, val classes: Set<String>) {
  FOOD("Food", GameIcon.FOOD, setOf("restaurant", "fast_food", "food_court", "cafe", "ice_cream", "bakery", "bar", "pub", "beer")),
  FUEL("Fuel", GameIcon.FUEL, setOf("fuel", "charging_station")),
  HEALTH("Health", GameIcon.HOSPITAL, setOf("hospital", "doctors", "clinic", "dentist", "pharmacy", "chemist")),
  STAY("Stay", GameIcon.HOTEL, setOf("lodging", "hotel")),
  SHOPS("Shops", GameIcon.SUITCASE, setOf("shop", "grocery", "supermarket", "convenience", "department_store", "clothing_store", "shoes", "fashion", "mall", "marketplace", "alcohol_shop")),
  PARKING("Parking", GameIcon.PARKING, setOf("parking")),
  TOILETS("Toilets", GameIcon.TOILET, setOf("toilets")),
  OTHER("Other", GameIcon.PIN, emptySet());

  companion object {
    fun of(cls: String?, sub: String?): IndexKind = entries.firstOrNull { k -> sub in k.classes || cls in k.classes } ?: OTHER
  }
}

data class IndexEntry(val place: Place, val kind: IndexKind)

/**
 * Every place the map is showing right now, by kind: Red Dead's map Index (Gaurav's captures 18
 * and 19: a list beside the map, tabs for All and each kind, the picked row lighting its place on
 * the map) and GTA V's legend on the pause map. Read from the place icons on screen, so it lists
 * exactly what the map shows; zoom in for more.
 */
fun indexEntries(camera: CameraState, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, here: GeographicCoordinate?): List<IndexEntry> {
  val projection = camera.projection ?: return emptyList()
  val hits =
      runCatching { projection.queryRenderedFeatures(DpRect(0.dp, 0.dp, width, height), setOf("poi-blips", "poi-blips-near", "poi-blips-close")) }
          .getOrNull()
          .orEmpty()
  val seen = HashSet<String>()
  val out = ArrayList<IndexEntry>()
  for (f in hits) {
    val props = f.properties ?: continue
    fun text(key: String) = (props[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    val name = text("name:en") ?: text("name_en") ?: text("name") ?: continue
    val point = f.geometry as? Point ?: continue
    val at = GeographicCoordinate(point.latitude, point.longitude)
    if (!seen.add("$name@${"%.4f".format(at.lat)},${"%.4f".format(at.lng)}")) continue
    val cls = text("class")
    val sub = text("subclass")
    val detail = (sub ?: cls)?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Place"
    out += IndexEntry(Place(name, detail, at), IndexKind.of(cls, sub))
  }
  return if (here == null) out.sortedBy { it.place.name } else out.sortedBy { com.thealgothrim.overworld.traffic.metres(here, it.place.coordinate) }
}

@Composable
fun MapIndexPanel(theme: OverworldTheme, camera: CameraState, here: GeographicCoordinate?, onChoose: (Place) -> Unit, onClose: () -> Unit) {
  val spec = theme.spec
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val entries = remember(camera.position) { indexEntries(camera, maxWidth, maxHeight, here) }
    val kinds = remember(entries) { IndexKind.entries.filter { k -> entries.any { it.kind == k } } }
    var tab by rememberSaveable { mutableStateOf(0) }
    val shown = if (tab == 0) entries else entries.filter { it.kind == kinds.getOrNull(tab - 1) }
    // Taps on the open map beside the panel close it, the way the game's map stays live beside its Index.
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose))
    SkinPanel(
        spec,
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
            .width(minOf(maxWidth * 0.78f, 360.dp))
            .fillMaxHeight()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
      Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SkinText(spec.title("Index"), spec.title, spec.titleSize(26.sp, 28.sp), spec.fg, Modifier.weight(1f), spacing = spec.titleSpacing)
        SkinRoundButton(spec, GameIcon.CLOSE, onClose, Modifier.size(44.dp))
      }
      Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
        GameTabs(spec, listOf("All") + kinds.map { it.label }, tab.coerceAtMost(kinds.size), { tab = it }, scroll = true)
      }
      if (shown.isEmpty()) {
        SkinHelp(spec, "No places on the map here. Zoom in and the map shows more, and so does this list.", Modifier.padding(horizontal = 12.dp))
      }
      LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
        items(shown, key = { "${it.place.name}${it.place.coordinate.lat}${it.place.coordinate.lng}" }) { e ->
          Row(
              Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChoose(e.place) }.padding(horizontal = 16.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            GameIconView(e.kind.icon, spec.fg, Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
              SkinText(e.place.name, spec.body, 16.sp, spec.fg)
              val away = here?.let { formatDistance(com.thealgothrim.overworld.traffic.metres(it, e.place.coordinate)) }
              SkinText(listOfNotNull(e.place.detail, away).joinToString("  ·  "), spec.body, 13.sp, spec.sub)
            }
          }
          SkinDivider(spec, Modifier.padding(horizontal = 16.dp))
        }
      }
    }
  }
}
