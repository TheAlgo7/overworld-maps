package com.thealgothrim.overworld.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.traffic.RoadFeature
import com.thealgothrim.overworld.traffic.RoadFeatureKind
import kotlinx.serialization.json.buildJsonObject
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

/** Traffic lights, cameras and live incidents on the map, one layer per kind. */
@Composable
@MaplibreComposable
fun RoadFeatureLayers(features: List<RoadFeature>, theme: OverworldTheme, car: Boolean) {
  val byKind = remember(features) { features.groupBy { it.kind } }
  RoadFeatureKind.entries.forEach { kind ->
    val list = byKind[kind] ?: return@forEach
    val source =
        rememberGeoJsonSource(
            GeoJsonData.Features(
                FeatureCollection(list.map { Feature(geometry = Point(it.at.lng, it.at.lat), properties = buildJsonObject {}) })
            )
        )
    val painter = remember(theme.id, kind) { RoadFeaturePainter(kind, theme.skin) }
    val size = iconSize(if (kind == RoadFeatureKind.TRAFFIC_LIGHT) (if (car) 22.dp else 18.dp) else if (car) 34.dp else 30.dp)
    SymbolLayer(
        id = "ow-feature-${kind.name.lowercase()}",
        source = source,
        // Lights only once streets are readable; incidents always.
        minZoom = if (kind == RoadFeatureKind.TRAFFIC_LIGHT) 13.5f else 8f,
        iconImage = image(painter, size = DpSize(size, if (kind == RoadFeatureKind.TRAFFIC_LIGHT) size * 1.6f else size), drawAsSdf = false),
        iconAnchor = const(if (kind == RoadFeatureKind.TRAFFIC_LIGHT) SymbolAnchor.Center else SymbolAnchor.Bottom),
        iconAllowOverlap = const(kind.incident),
        iconIgnorePlacement = const(kind.incident),
    )
  }
}

/** Badge colours for incident kinds, per game. */
private fun badge(kind: RoadFeatureKind, skin: Skin): Pair<Color, Color> {
  // GTA V: HUD colours. Red Dead: dark discs like its blips, with the rim telling the kind.
  return if (skin == Skin.GTA) {
    when (kind) {
      RoadFeatureKind.ACCIDENT, RoadFeatureKind.CLOSURE -> Color(0xFFEB2427) to Color.White
      RoadFeatureKind.ROADWORKS, RoadFeatureKind.LANE_CLOSED, RoadFeatureKind.HAZARD -> Color(0xFFF0C850) to Color.Black
      RoadFeatureKind.JAM, RoadFeatureKind.BROKEN_DOWN -> Color(0xFFFF8A00) to Color.Black
      RoadFeatureKind.FLOODING -> Color(0xFF5DB6E5) to Color.Black
      RoadFeatureKind.SPEED_CAMERA -> Color(0xFF202020) to Color.White
      RoadFeatureKind.TRAFFIC_LIGHT -> Color(0xFF111111) to Color.White
    }
  } else {
    Color(0xFF1B1A1A) to Color(0xFFE6E6E6)
  }
}

private fun rim(kind: RoadFeatureKind, skin: Skin): Color =
    if (skin == Skin.GTA) Color.Black
    else
        when (kind) {
          RoadFeatureKind.ACCIDENT, RoadFeatureKind.CLOSURE -> Color(0xFFCC0000)
          RoadFeatureKind.ROADWORKS, RoadFeatureKind.LANE_CLOSED, RoadFeatureKind.HAZARD, RoadFeatureKind.JAM, RoadFeatureKind.BROKEN_DOWN -> Color(0xFFF9C804)
          RoadFeatureKind.FLOODING -> Color(0xFF56A8D3)
          else -> Color(0xFFACA8A6)
        }

/** Draws one road-feature icon: a round badge with the glyph, or a signal head for lights. */
class RoadFeaturePainter(private val kind: RoadFeatureKind, private val skin: Skin) : Painter() {
  override val intrinsicSize: Size = Size.Unspecified

  override fun DrawScope.onDraw() {
    if (kind == RoadFeatureKind.TRAFFIC_LIGHT) {
      drawTrafficLight()
      return
    }
    val (fill, glyph) = badge(kind, skin)
    val s = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(Color.Black.copy(alpha = 0.3f), s * 0.48f, c + Offset(0f, s * 0.03f))
    drawCircle(rim(kind, skin), s * 0.47f, c)
    drawCircle(fill, s * 0.40f, c)
    // Glyphs are drawn on a 24-unit grid inside the badge.
    val g = s * 0.62f
    translate(c.x - g / 2f, c.y - g / 2f) { drawRoadGlyph(kind, glyph, g) }
  }

  private fun DrawScope.drawTrafficLight() {
    val w = size.width
    val h = size.height
    val body = Color(0xFF151515)
    drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(w * 0.12f, h * 0.06f), Size(w * 0.8f, h * 0.9f), androidx.compose.ui.geometry.CornerRadius(w * 0.25f))
    drawRoundRect(if (skin == Skin.RDR) Color(0xFFACA8A6) else Color.White, Offset(w * 0.08f, h * 0.02f), Size(w * 0.84f, h * 0.9f), androidx.compose.ui.geometry.CornerRadius(w * 0.28f))
    drawRoundRect(body, Offset(w * 0.16f, h * 0.06f), Size(w * 0.68f, h * 0.82f), androidx.compose.ui.geometry.CornerRadius(w * 0.22f))
    val r = w * 0.17f
    drawCircle(Color(0xFFFF3B30), r, Offset(w / 2, h * 0.22f))
    drawCircle(Color(0xFFFFB020), r, Offset(w / 2, h * 0.47f))
    drawCircle(Color(0xFF34C759), r, Offset(w / 2, h * 0.72f))
  }
}

/** The glyph for a road-feature kind on a [s]-sized square, in [color]. Shared with the HUD alert. */
fun DrawScope.drawRoadGlyph(kind: RoadFeatureKind, color: Color, s: Float) {
  val u = s / 24f
  fun p(x: Float, y: Float) = Offset(x * u, y * u)
  val stroke = Stroke(width = 2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
  fun carFront(cx: Float, cy: Float, scale: Float) {
    // Front view of a car: roof, windscreen, body, wheels.
    val k = scale
    val body = Path().apply {
      moveTo((cx - 7f * k) * u, (cy + 1f * k) * u)
      lineTo((cx - 5.5f * k) * u, (cy - 4.5f * k) * u)
      lineTo((cx + 5.5f * k) * u, (cy - 4.5f * k) * u)
      lineTo((cx + 7f * k) * u, (cy + 1f * k) * u)
      lineTo((cx + 7.5f * k) * u, (cy + 5f * k) * u)
      lineTo((cx - 7.5f * k) * u, (cy + 5f * k) * u)
      close()
    }
    drawPath(body, color)
    drawRect(color, p(cx - 7f * k, cy + 5f * k), Size(3f * k * u, 2.5f * k * u))
    drawRect(color, p(cx + 4f * k, cy + 5f * k), Size(3f * k * u, 2.5f * k * u))
  }
  when (kind) {
    RoadFeatureKind.ACCIDENT -> {
      // Two cars meeting at an angle, with an impact mark above.
      rotate(-24f, p(8f, 15f)) { carFront(8f, 15f, 0.62f) }
      rotate(24f, p(16f, 15f)) { carFront(16f, 15f, 0.62f) }
      drawLine(color, p(12f, 2.5f), p(12f, 6f), 2f * u, StrokeCap.Round)
      drawLine(color, p(7.5f, 4f), p(9.5f, 7f), 2f * u, StrokeCap.Round)
      drawLine(color, p(16.5f, 4f), p(14.5f, 7f), 2f * u, StrokeCap.Round)
    }
    RoadFeatureKind.ROADWORKS -> {
      // Traffic cone with two bands.
      val cone = Path().apply { moveTo(12f * u, 2.5f * u); lineTo(18f * u, 19f * u); lineTo(6f * u, 19f * u); close() }
      drawPath(cone, color)
      drawRect(color, p(3.5f, 19f), Size(17f * u, 2.6f * u))
      // Bands in the badge colour, cut across the cone.
      val band = if (color == Color.Black) Color(0xFFF0C850) else Color(0xFF1B1A1A)
      drawLine(band, p(9.6f, 9.5f), p(14.4f, 9.5f), 1.8f * u)
      drawLine(band, p(8.3f, 14f), p(15.7f, 14f), 1.8f * u)
    }
    RoadFeatureKind.CLOSURE -> {
      // No entry: a bar across.
      drawRoundRect(color, p(4f, 10f), Size(16f * u, 4.2f * u), androidx.compose.ui.geometry.CornerRadius(1f * u))
    }
    RoadFeatureKind.LANE_CLOSED -> {
      // Barrier: striped board on two legs.
      drawRect(color, p(3f, 7f), Size(18f * u, 6f * u), style = Stroke(width = 1.8f * u))
      for (i in 0 until 3) drawLine(color, p(5f + i * 5.5f, 13f), p(9f + i * 5.5f, 7f), 2f * u)
      drawLine(color, p(6f, 13f), p(6f, 20f), 2f * u, StrokeCap.Round)
      drawLine(color, p(18f, 13f), p(18f, 20f), 2f * u, StrokeCap.Round)
    }
    RoadFeatureKind.JAM -> {
      // Cars queued: one in front, one behind.
      carFront(8.5f, 9.5f, 0.55f)
      carFront(14f, 15.5f, 0.75f)
    }
    RoadFeatureKind.BROKEN_DOWN -> {
      carFront(10f, 13f, 0.8f)
      val tri = Path().apply { moveTo(18.5f * u, 12f * u); lineTo(22.5f * u, 20f * u); lineTo(14.5f * u, 20f * u); close() }
      drawPath(tri, color, style = Stroke(width = 1.6f * u, join = StrokeJoin.Round))
    }
    RoadFeatureKind.FLOODING -> {
      for (row in 0 until 3) {
        val y = 7f + row * 5f
        val wave = Path().apply {
          moveTo(3f * u, y * u)
          cubicTo(6f * u, (y - 2.5f) * u, 9f * u, (y + 2.5f) * u, 12f * u, y * u)
          cubicTo(15f * u, (y - 2.5f) * u, 18f * u, (y + 2.5f) * u, 21f * u, y * u)
        }
        drawPath(wave, color, style = stroke)
      }
    }
    RoadFeatureKind.HAZARD -> {
      val tri = Path().apply { moveTo(12f * u, 3f * u); lineTo(22f * u, 20.5f * u); lineTo(2f * u, 20.5f * u); close() }
      drawPath(tri, color, style = Stroke(width = 2f * u, join = StrokeJoin.Round))
      drawLine(color, p(12f, 9f), p(12f, 14.5f), 2.4f * u, StrokeCap.Round)
      drawCircle(color, 1.3f * u, p(12f, 17.6f))
    }
    RoadFeatureKind.SPEED_CAMERA -> {
      drawRoundRect(color, p(3f, 7f), Size(15f * u, 9f * u), androidx.compose.ui.geometry.CornerRadius(1.5f * u))
      val lens = Path().apply { moveTo(18f * u, 9f * u); lineTo(22f * u, 7.5f * u); lineTo(22f * u, 15.5f * u); lineTo(18f * u, 14f * u); close() }
      drawPath(lens, color)
      drawLine(color, p(9f, 16f), p(9f, 21f), 2.2f * u, StrokeCap.Round)
    }
    RoadFeatureKind.TRAFFIC_LIGHT -> {
      drawRoundRect(color, p(8f, 2f), Size(8f * u, 20f * u), androidx.compose.ui.geometry.CornerRadius(3f * u), style = Stroke(width = 1.8f * u))
      drawCircle(color, 2f * u, p(12f, 6.5f)); drawCircle(color, 2f * u, p(12f, 12f)); drawCircle(color, 2f * u, p(12f, 17.5f))
    }
  }
}
