package com.thealgothrim.overworld.map

import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.maplibreui.routeline.RouteOverlayBuilder
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.Skin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.IconPitchAlignment
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.TripState
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.traffic.RouteLine
import com.thealgothrim.overworld.traffic.TrafficSpan
import androidx.compose.runtime.collectAsState
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.switch

/** First label layer in every Overworld style; the route is drawn just under it. */
private const val FIRST_LABEL_LAYER = "label-water"

/**
 * MapLibre reads an icon's pixel ratio from the bitmap's density, which Android sets to the phone
 * screen's density, while MapLibre Compose draws the bitmap at the current display's density. On
 * the Android Auto surface those differ (car ~1x, phone ~3x) and icons came out a third of the
 * size. Scale the requested size so the icon lands at the intended dp on either display.
 */
@Composable
internal fun iconSize(size: Dp): Dp {
  val local = LocalDensity.current.density
  val bitmap = DisplayMetrics.DENSITY_DEVICE_STABLE / DisplayMetrics.DENSITY_DEFAULT.toFloat()
  return size * (bitmap / local)
}

private fun widthByZoom(at10: Float, at18: Float) =
    interpolate(linear(), zoom(), 10 to const(at10.dp), 18 to const(at18.dp))

/**
 * The route while driving is only the road still to go, starting at the player marker. The road
 * already driven disappears, the way the waypoint route does in GTA V and on the RDR2 minimap.
 */
fun themedRouteOverlay(theme: OverworldTheme, car: Boolean) =
    RouteOverlayBuilder(
        navigationPath = { uiState: NavigationUiState ->
          val geometry = uiState.routeGeometry
          if (geometry != null && geometry.size >= 2) {
            ThemedRouteLine(routeAhead(uiState) ?: geometry, theme, car)
            val extras by AppModule.viewModel.extras.collectAsState()
            val length = remember(geometry) { RouteLine(geometry).length }
            val done = uiState.progress?.let { length - it.distanceRemaining } ?: 0.0
            RouteTrafficLine(geometry, extras.trafficSpans, theme, car, from = done)
          }
        }
    )

/** The route from the car's snapped position to the end, or null when not navigating. */
private fun routeAhead(uiState: NavigationUiState): List<GeographicCoordinate>? {
  val trip = uiState.tripState as? TripState.Navigating ?: return null
  val steps = trip.remainingSteps
  if (steps.isEmpty()) return null
  val index = trip.currentStepGeometryIndex?.toInt() ?: 0
  val points = mutableListOf(trip.snappedUserLocation.coordinates)
  points += steps.first().geometry.drop(index + 1)
  // Each step starts where the last one ended; skip the repeated point.
  for (step in steps.drop(1)) points += step.geometry.drop(1)
  return points.takeIf { it.size >= 2 }
}

private fun lineJson(points: List<GeographicCoordinate>): String {
  val coords = points.joinToString(",") { "[${it.lng},${it.lat}]" }
  return """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coords]}}]}"""
}

/**
 * Traffic on the route itself, like Google's route line: the slow and jammed stretches recoloured
 * (amber slow, red heavy, darkest where closed), only on the road still ahead ([from] metres on).
 * Red Dead's route is already red, so its jams are the darker maroon ink.
 */
@Composable
@MaplibreComposable
fun RouteTrafficLine(points: List<GeographicCoordinate>, spans: List<TrafficSpan>, theme: OverworldTheme, car: Boolean, from: Double = 0.0) {
  val json =
      remember(points, spans, (from / 25).toInt()) {
        val line = RouteLine(points)
        val features =
            spans.filter { it.to > from }.joinToString(",") { span ->
              val coords = line.slice(maxOf(span.from, from), span.to).joinToString(",") { "[${it.lng},${it.lat}]" }
              """{"type":"Feature","properties":{"level":${span.level}},"geometry":{"type":"LineString","coordinates":[$coords]}}"""
            }
        """{"type":"FeatureCollection","features":[$features]}"""
      }
  val source = rememberGeoJsonSource(GeoJsonData.JsonString(json))
  val (slow, heavy, closed) =
      if (theme.skin == Skin.RDR) Triple(Color(0xFFD08A1E), Color(0xFF4A0A10), Color(0xFF1E1E1C))
      else Triple(Color(0xFFFFB020), Color(0xFFFF3B30), Color(0xFF8F0E1A))
  val k = if (theme.skin == Skin.RDR) 0.72f else 1f
  Anchor.Below(FIRST_LABEL_LAYER) {
    LineLayer(
        id = "ow-route-traffic",
        source = source,
        color = switch(feature["level"].asNumber(), case(3, const(closed)), case(2, const(heavy)), fallback = const(slow)),
        width = widthByZoom((if (car) 4.5f else 3.5f) * k, (if (car) 15f else 12f) * k),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
  }
}

@Composable
@MaplibreComposable
fun ThemedRouteLine(points: List<GeographicCoordinate>, theme: OverworldTheme, car: Boolean) {
  val json = remember(points) { lineJson(points) }
  val source = rememberGeoJsonSource(GeoJsonData.JsonString(json))
  // RDR2 inks its route inside the road, so the road's own ink shows along both edges; GTA V's
  // route is as wide as the road.
  val k = if (theme.skin == Skin.RDR) 0.72f else 1f
  Anchor.Below(FIRST_LABEL_LAYER) {
    val glow = theme.routeGlow
    if (glow != null && !car) {
      LineLayer(
          id = "ow-route-glow",
          source = source,
          color = const(glow.copy(alpha = 0.45f)),
          blur = const(8.dp),
          width = widthByZoom(10f, 34f),
          cap = const(LineCap.Round),
          join = const(LineJoin.Round),
      )
    }
    LineLayer(
        id = "ow-route-casing",
        source = source,
        color = const(theme.routeCasing),
        width = widthByZoom((if (car) 7f else 5.5f) * k, (if (car) 22f else 18f) * k),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
    LineLayer(
        id = "ow-route-line",
        source = source,
        color = const(theme.routeLine),
        width = widthByZoom((if (car) 4.5f else 3.5f) * k, (if (car) 15f else 12f) * k),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
  }
}

/** The player arrow: a flat chevron lying on the map, pointing along the route. */
@Composable
@MaplibreComposable
fun ThemedPuck(uiState: NavigationUiState, theme: OverworldTheme, car: Boolean) {
  val location = rememberDisplayedLocation(uiState) ?: return
  var lastBearing by remember { mutableDoubleStateOf(0.0) }
  val bearing = location.courseDegrees ?: lastBearing
  LaunchedEffect(bearing) { lastBearing = bearing }

  val position = location.position.value
  val source =
      rememberGeoJsonSource(
          GeoJsonData.Features(
              FeatureCollection(
                  Feature(
                      geometry = Point(position.longitude, position.latitude),
                      properties = buildJsonObject { put("bearing", bearing) },
                  )
              )
          ),
          options = GeoJsonOptions(synchronousUpdate = true),
      )
  val painter =
      remember(theme.id) {
        when (theme.puckShape) {
          "teardrop" -> TeardropPainter(theme.puckFill, theme.puckStroke)
          else -> ChevronPainter(theme.puckFill, theme.puckShade, theme.puckStroke, glow = theme.routeGlow != null)
        }
      }
  val size = iconSize(if (car) 46.dp else 40.dp)
  SymbolLayer(
      id = "ow-puck",
      source = source,
      iconImage = image(painter, size = DpSize(size, size), drawAsSdf = false),
      iconAnchor = const(SymbolAnchor.Center),
      iconRotate = feature["bearing"].asNumber(const(0f)),
      iconPitchAlignment = const(IconPitchAlignment.Map),
      iconRotationAlignment = const(IconRotationAlignment.Map),
      iconAllowOverlap = const(true),
      iconIgnorePlacement = const(true),
  )
}

/** The waypoint marker: the theme's blip (GTA V four petals, RDR2 crossed ring, or a diamond). */
@Composable
@MaplibreComposable
fun ThemedDestination(at: GeographicCoordinate, theme: OverworldTheme, id: String = "ow-destination") {
  val source =
      rememberGeoJsonSource(
          GeoJsonData.Features(
              FeatureCollection(
                  Feature(geometry = Point(at.lng, at.lat), properties = buildJsonObject {})
              )
          )
      )
  val painter =
      remember(theme.id) {
        when (theme.blipShape) {
          "quatrefoil" -> QuatrefoilPainter(theme.blipFill, theme.blipCenter, theme.blipStroke)
          "crossring" -> CrossRingPainter(theme.blipFill, theme.blipStroke)
          else -> DiamondPainter(theme.blipFill, theme.blipStroke)
        }
      }
  val size = iconSize(when (theme.blipShape) { "quatrefoil" -> 34.dp; "crossring" -> 38.dp; else -> 30.dp })
  SymbolLayer(
      id = id,
      source = source,
      iconImage = image(painter, size = DpSize(size, size), drawAsSdf = false),
      iconAnchor = const(SymbolAnchor.Center),
      iconAllowOverlap = const(true),
      // Map labels under the waypoint step aside instead of printing through it.
      iconIgnorePlacement = const(false),
  )
}

internal class ChevronPainter(
    private val fill: Color,
    private val shade: Color?,
    private val stroke: Color,
    private val glow: Boolean,
) : Painter() {
  override val intrinsicSize: Size = Size.Unspecified

  override fun DrawScope.onDraw() {
    if (shade != null) {
      drawRadarArrow(shade)
      return
    }
    val w = size.width
    val h = size.height
    val path =
        Path().apply {
          moveTo(w * 0.5f, h * 0.1f)
          lineTo(w * 0.84f, h * 0.88f)
          lineTo(w * 0.5f, h * 0.7f)
          lineTo(w * 0.16f, h * 0.88f)
          close()
        }
    val base = size.minDimension * 0.06f
    if (glow) {
      for (i in 3 downTo 1) {
        drawPath(path, stroke.copy(alpha = 0.16f), style = Stroke(width = base * (1 + i * 1.6f), join = StrokeJoin.Round))
      }
    } else {
      drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(width = base * 2.6f, join = StrokeJoin.Round))
    }
    drawPath(path, stroke, style = Stroke(width = base * 1.4f, join = StrokeJoin.Round))
    drawPath(path, fill)
  }

  /**
   * The GTA V radar arrow, traced from its radar_centre sprite: a wide arrowhead with a curved
   * notch at the back, black outline, white left half and grey right half.
   */
  private fun DrawScope.drawRadarArrow(shade: Color) {
    val s = size.minDimension
    val ox = (size.width - s) / 2f
    val oy = (size.height - s) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    // Kept inside the canvas by half the outline width so nothing clips.
    val tip = p(0.5f, 0.08f)
    val right = p(0.9f, 0.91f)
    val left = p(0.1f, 0.91f)
    val notch = p(0.5f, 0.69f)
    val whole =
        Path().apply {
          moveTo(tip.x, tip.y)
          lineTo(right.x, right.y)
          p(0.62f, 0.69f).let { quadraticTo(it.x, it.y, notch.x, notch.y) }
          p(0.38f, 0.69f).let { quadraticTo(it.x, it.y, left.x, left.y) }
          close()
        }
    val rightHalf =
        Path().apply {
          moveTo(tip.x, tip.y)
          lineTo(right.x, right.y)
          p(0.62f, 0.69f).let { quadraticTo(it.x, it.y, notch.x, notch.y) }
          close()
        }
    drawPath(whole, stroke, style = Stroke(width = s * 0.13f, join = StrokeJoin.Miter, miter = 3f))
    drawPath(whole, fill)
    drawPath(rightHalf, shade)
  }
}

/**
 * The GTA V waypoint, traced from its radar_waypoint sprite: four pointed petals in the waypoint
 * colour with black outlines around a dark ring.
 */
internal class QuatrefoilPainter(
    private val fill: Color,
    private val core: Color,
    private val stroke: Color,
) : Painter() {
  override val intrinsicSize: Size = Size.Unspecified

  override fun DrawScope.onDraw() {
    val c = Offset(size.width / 2f, size.height / 2f)
    val s = size.minDimension
    fun p(x: Float, y: Float) = Offset(c.x + x * s, c.y + y * s)
    // One petal pointing up; the others are the same shape rotated.
    val petal =
        Path().apply {
          val tip = p(0f, -0.46f)
          moveTo(tip.x, tip.y)
          val a = p(0.06f, -0.42f); val b = p(0.14f, -0.35f); val e = p(0.14f, -0.27f)
          cubicTo(a.x, a.y, b.x, b.y, e.x, e.y)
          val f = p(0.14f, -0.2f); val g = p(0.1f, -0.15f); val h = p(0.06f, -0.13f)
          cubicTo(f.x, f.y, g.x, g.y, h.x, h.y)
          p(-0.06f, -0.13f).let { lineTo(it.x, it.y) }
          val i = p(-0.1f, -0.15f); val j = p(-0.14f, -0.2f); val k = p(-0.14f, -0.27f)
          cubicTo(i.x, i.y, j.x, j.y, k.x, k.y)
          val l = p(-0.14f, -0.35f); val m = p(-0.06f, -0.42f)
          cubicTo(l.x, l.y, m.x, m.y, tip.x, tip.y)
          close()
        }
    val outline = Stroke(width = s * 0.08f, join = StrokeJoin.Round)
    for (turn in 0 until 4) rotate(turn * 90f, c) { drawPath(petal, stroke, style = outline) }
    for (turn in 0 until 4) rotate(turn * 90f, c) { drawPath(petal, fill) }
    drawCircle(stroke, s * 0.17f, c)
    drawCircle(core, s * 0.12f, c)
  }
}

/**
 * The RDR2 player pointer, traced from blip_code_center: an off-white teardrop pointing the way you
 * face, with a ring punched through it and a rough dark outline.
 */
internal class TeardropPainter(private val fill: Color, private val stroke: Color) : Painter() {
  override val intrinsicSize: Size = Size.Unspecified

  override fun DrawScope.onDraw() {
    val s = size.minDimension
    val ox = (size.width - s) / 2f
    val oy = (size.height - s) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    val drop =
        Path().apply {
          val tip = p(0.5f, 0.07f)
          moveTo(tip.x, tip.y)
          p(0.63f, 0.24f).let { a -> p(0.8f, 0.42f).let { b -> p(0.8f, 0.62f).let { c -> cubicTo(a.x, a.y, b.x, b.y, c.x, c.y) } } }
          p(0.8f, 0.8f).let { a -> p(0.67f, 0.93f).let { b -> p(0.5f, 0.93f).let { c -> cubicTo(a.x, a.y, b.x, b.y, c.x, c.y) } } }
          p(0.33f, 0.93f).let { a -> p(0.2f, 0.8f).let { b -> p(0.2f, 0.62f).let { c -> cubicTo(a.x, a.y, b.x, b.y, c.x, c.y) } } }
          p(0.2f, 0.42f).let { a -> p(0.37f, 0.24f).let { b -> cubicTo(a.x, a.y, b.x, b.y, tip.x, tip.y) } }
          close()
        }
    val hole = p(0.5f, 0.63f)
    drawPath(drop, Color.Black.copy(alpha = 0.22f), style = Stroke(width = s * 0.2f, join = StrokeJoin.Round))
    drawPath(drop, stroke, style = Stroke(width = s * 0.1f, join = StrokeJoin.Round))
    drawPath(drop, fill)
    drawCircle(stroke, s * 0.13f, hole)
    drawCircle(fill.copy(alpha = 0f), s * 0.07f, hole, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
  }
}

/**
 * The RDR2 waypoint, traced from blip_code_waypoint: a hand-drawn X struck through a ring, in the
 * waypoint red with a dark edge.
 */
internal class CrossRingPainter(private val color: Color, private val stroke: Color) : Painter() {
  override val intrinsicSize: Size = Size.Unspecified

  override fun DrawScope.onDraw() {
    val s = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    fun p(x: Float, y: Float) = Offset(c.x + x * s, c.y + y * s)
    val ring = s * 0.25f
    val line = s * 0.085f
    // Slightly uneven ends, like the ink-drawn blip.
    val a1 = p(-0.4f, -0.36f); val a2 = p(0.38f, 0.41f)
    val b1 = p(0.41f, -0.38f); val b2 = p(-0.37f, 0.39f)
    val edge = line + s * 0.07f
    drawCircle(stroke, ring, c, style = Stroke(width = edge))
    drawLine(stroke, a1, a2, strokeWidth = edge, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(stroke, b1, b2, strokeWidth = edge, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawCircle(color, ring, c, style = Stroke(width = line))
    drawLine(color, a1, a2, strokeWidth = line, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(color, b1, b2, strokeWidth = line, cap = androidx.compose.ui.graphics.StrokeCap.Round)
  }
}

private class DiamondPainter(private val fill: Color, private val stroke: Color) : Painter() {
  override val intrinsicSize: Size = Size.Unspecified

  override fun DrawScope.onDraw() {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * 0.42f
    val path =
        Path().apply {
          moveTo(c.x, c.y - r)
          lineTo(c.x + r, c.y)
          lineTo(c.x, c.y + r)
          lineTo(c.x - r, c.y)
          close()
        }
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(width = size.minDimension * 0.09f, join = StrokeJoin.Round))
  }
}
