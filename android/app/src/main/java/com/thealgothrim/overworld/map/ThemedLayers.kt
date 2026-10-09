package com.thealgothrim.overworld.map

import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.imageResource
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
import com.thealgothrim.overworld.R
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
import org.maplibre.compose.location.Location
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.Source
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import kotlin.math.roundToInt
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import uniffi.ferrostar.GeographicCoordinate
import com.thealgothrim.overworld.traffic.RouteLine
import com.thealgothrim.overworld.traffic.TrafficSpan
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

// Everything here is composed again on every animation frame during a trip (the arrow glides
// between GPS fixes). A source handed a new data object sends it to MapLibre again, which re-cuts
// it into tiles, so each source keeps its data object until the data really changes. Sending the
// whole route 60 times a second is what held the map near 30 fps on a trip.

/**
 * The route while driving: only the road still to go, starting at the player marker. The road
 * already driven disappears, the way the waypoint route does in GTA V and on the RDR2 minimap.
 *
 * Two pieces: the far part from [NEAR_METRES] ahead to the end, sent to MapLibre once per
 * [NEAR_METRES] driven, and the short near part from the arrow to there, which follows the arrow
 * every frame. GTA VI's route fades in ahead of the arrow: its first [OverworldTheme.routeFade]
 * metres are a third piece, drawn with a gradient, and the far part starts beyond the fade's reach.
 */
@Composable
@MaplibreComposable
fun ThemedRouteAhead(route: RoutePolyline, shown: DisplayedPosition, theme: OverworldTheme, car: Boolean, style: Any) {
  val fade = theme.routeFade
  val step by remember(shown) { derivedStateOf { ((shown.along ?: 0.0) / NEAR_METRES).toInt() } }
  val cut = remember(route, step, fade) { minOf(route.length, (step + 1) * NEAR_METRES + fade) }
  val far = rememberGeoJsonSource(remember(route, cut) { GeoJsonData.JsonString(lineJson(route.slice(cut, route.length))) })
  fun fadeEnd(along: Double) = minOf(along.coerceAtMost(cut) + fade, cut)
  fun nearData(along: Double) = GeoJsonData.JsonString(lineJson(route.slice(fadeEnd(along), cut)))
  fun fadeData(along: Double) = GeoJsonData.JsonString(lineJson(route.slice(along.coerceAtMost(cut), fadeEnd(along))))
  // Also fresh for a new map style: MapLibre gets these again when the style reloads.
  val startAlong = remember(route, cut, style) { Snapshot.withoutReadObservation { shown.along } ?: 0.0 }
  // The near pieces are handed to MapLibre straight from the animation, without composing anything.
  val near = rememberGeoJsonSource(remember(route, cut, style) { nearData(startAlong) }, options = GeoJsonOptions(synchronousUpdate = true))
  val faded =
      if (fade > 0) {
        rememberGeoJsonSource(
            remember(route, cut, style) { fadeData(startAlong) },
            // Line metrics give the gradient its 0..1 along the piece.
            options = GeoJsonOptions(synchronousUpdate = true, lineMetrics = true),
        )
      } else null
  LaunchedEffect(near, faded, route, cut) {
    snapshotFlow { shown.along ?: 0.0 }
        .collect {
          near.setData(nearData(it))
          faded?.setData(fadeData(it))
        }
  }
  RouteLayers(listOf(near, far), "ow-trip", theme, car, faded)
}

/** One distance for both pieces of the route ahead: 300 m is about 18 s at 60 km/h. */
private const val NEAR_METRES = 300.0

/** How wide a theme draws its route, as a share of the road. */
private fun routeWidth(theme: OverworldTheme) =
    when (theme.skin) {
      // RDR2 inks its route inside the road, so the road's own ink shows along both edges.
      Skin.RDR -> 0.72f
      // GTA VI's ribbon runs down the middle of its broad roads, about a lane and a half wide.
      Skin.GTA6 -> 0.7f
      // GTA V's route is as wide as the road.
      Skin.GTA -> 1f
    }

/**
 * The themed route line over [sources], all casings under all lines so pieces of one route join
 * without a seam. [faded]: the piece just ahead of the arrow, drawn fading in (GTA VI).
 */
@Composable
@MaplibreComposable
private fun RouteLayers(sources: List<Source>, id: String, theme: OverworldTheme, car: Boolean, faded: Source? = null) {
  val k = routeWidth(theme)
  val lineAt10 = (if (car) 4.5f else 3.5f) * k
  val lineAt18 = (if (car) 15f else 12f) * k
  val lineWidth = widthByZoom(lineAt10, lineAt18)
  // GTA VI: a thin brighter edge each side ([OverworldTheme.routeEdge] of the width); the others: a
  // classic outline. Opaque either way, so the route's pieces join without a seam where they overlap.
  val edge = theme.routeEdge
  val casingWidth =
      if (edge > 0f) widthByZoom(lineAt10 / (1 - 2 * edge), lineAt18 / (1 - 2 * edge))
      else widthByZoom((if (car) 7f else 5.5f) * k, (if (car) 22f else 18f) * k)
  Anchor.Below(FIRST_LABEL_LAYER) {
    val glow = theme.routeGlow
    if (glow != null && !car) {
      sources.forEachIndexed { i, source ->
        LineLayer(
            id = "$id-glow-$i",
            source = source,
            color = const(glow.copy(alpha = 0.45f)),
            blur = const(8.dp),
            width = widthByZoom(10f, 34f),
            cap = const(LineCap.Round),
            join = const(LineJoin.Round),
        )
      }
    }
    if (theme.routeCasing.alpha > 0f) {
      sources.forEachIndexed { i, source ->
        LineLayer(
            id = "$id-casing-$i",
            source = source,
            color = const(theme.routeCasing),
            width = casingWidth,
            cap = const(LineCap.Round),
            join = const(LineJoin.Round),
        )
      }
      faded?.let {
        LineLayer(
            id = "$id-casing-fade",
            source = it,
            gradient = fadeIn(theme.routeCasing),
            width = casingWidth,
            cap = const(LineCap.Butt),
            join = const(LineJoin.Round),
        )
      }
    }
    sources.forEachIndexed { i, source ->
      LineLayer(
          id = "$id-line-$i",
          source = source,
          color = const(theme.routeLine),
          width = lineWidth,
          cap = const(LineCap.Round),
          join = const(LineJoin.Round),
      )
    }
    faded?.let {
      LineLayer(
          id = "$id-line-fade",
          source = it,
          gradient = fadeIn(theme.routeLine),
          width = lineWidth,
          // Square ends: a round cap would poke out solid in front of the arrow.
          cap = const(LineCap.Butt),
          join = const(LineJoin.Round),
      )
    }
  }
}

/**
 * Clear at the arrow to [color] at the end of the piece, easing in like GTA VI's route does just
 * ahead of the player.
 */
private fun fadeIn(color: Color) =
    interpolate(
        linear(),
        feature.lineProgress(),
        0 to const(color.copy(alpha = 0f)),
        0.5 to const(color.copy(alpha = color.alpha * 0.45f)),
        1 to const(color),
    )

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
fun RouteTrafficLine(
    points: List<GeographicCoordinate>,
    spans: List<TrafficSpan>,
    theme: OverworldTheme,
    car: Boolean,
    from: Double = 0.0,
    id: String = "ow-route-traffic",
) {
  val line = remember(points) { RouteLine(points) }
  // Trimmed behind the arrow every 25 m, not every frame.
  val data =
      remember(line, spans, (from / 25).toInt()) {
        val features =
            spans.filter { it.to > from }.joinToString(",") { span ->
              val coords = line.slice(maxOf(span.from, from), span.to).joinToString(",") { "[${it.lng},${it.lat}]" }
              """{"type":"Feature","properties":{"level":${span.level}},"geometry":{"type":"LineString","coordinates":[$coords]}}"""
            }
        GeoJsonData.JsonString("""{"type":"FeatureCollection","features":[$features]}""")
      }
  val source = rememberGeoJsonSource(data)
  val (slow, heavy, closed) =
      if (theme.skin == Skin.RDR) Triple(Color(0xFFD08A1E), Color(0xFF4A0A10), Color(0xFF1E1E1C))
      else Triple(Color(0xFFFFB020), Color(0xFFFF3B30), Color(0xFF8F0E1A))
  val k = routeWidth(theme)
  Anchor.Below(FIRST_LABEL_LAYER) {
    LineLayer(
        id = id,
        source = source,
        color = switch(feature["level"].asNumber(), case(3, const(closed)), case(2, const(heavy)), fallback = const(slow)),
        width = widthByZoom((if (car) 4.5f else 3.5f) * k, (if (car) 15f else 12f) * k),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
  }
}

/** A whole route drawn at once: the directions preview. */
@Composable
@MaplibreComposable
fun ThemedRouteLine(points: List<GeographicCoordinate>, theme: OverworldTheme, car: Boolean) {
  val source = rememberGeoJsonSource(remember(points) { GeoJsonData.JsonString(lineJson(points)) })
  RouteLayers(listOf(source), "ow-route", theme, car)
}

/**
 * The player marker: Gaurav's drawing of each game's blip (tools/blips/, built by
 * tools/make_pucks.py), turned to the way the car is heading. It stands flat to the screen like the
 * games' radar blips, so the tilted 3D map never squashes it, and it is sized against the route the
 * way the games size theirs (GTA V's arrow is about twice the route's width, GTA VI's disc a little
 * over three times).
 */
@Composable
@MaplibreComposable
fun ThemedPuck(shown: DisplayedPosition, theme: OverworldTheme, car: Boolean, style: Any) {
  // Moved every frame straight from the animation, without composing anything (see ThemedRouteAhead).
  val lastBearing = remember { doubleArrayOf(0.0) }
  fun puckData(location: Location): GeoJsonData {
    val bearing = location.courseDegrees ?: lastBearing[0]
    lastBearing[0] = bearing
    val position = location.position.value
    return GeoJsonData.Features(
        FeatureCollection(Feature(geometry = Point(position.longitude, position.latitude), properties = buildJsonObject { put("bearing", bearing) }))
    )
  }
  val source =
      rememberGeoJsonSource(
          remember(shown, style) { puckData(Snapshot.withoutReadObservation { shown.location }) },
          options = GeoJsonOptions(synchronousUpdate = true),
      )
  LaunchedEffect(source, shown) { snapshotFlow { shown.location }.collect { source.setData(puckData(it)) } }
  val art =
      ImageBitmap.imageResource(
          when (theme.skin) {
            Skin.GTA -> R.drawable.puck_gta5
            Skin.RDR -> R.drawable.puck_rdr2
            Skin.GTA6 -> R.drawable.puck_gta6
          }
      )
  val painter = remember(art) { ArtPainter(art) }
  val size =
      iconSize(
          when (theme.skin) {
            Skin.GTA -> if (car) 28.dp else 24.dp
            // The teardrop's square has room for its shadow, so it takes a little more.
            Skin.RDR -> if (car) 31.dp else 27.dp
            Skin.GTA6 -> if (car) 30.dp else 26.dp
          }
      )
  SymbolLayer(
      id = "ow-puck",
      source = source,
      iconImage = image(painter, size = DpSize(size, size), drawAsSdf = false),
      iconAnchor = const(SymbolAnchor.Center),
      iconRotate = feature["bearing"].asNumber(const(0f)),
      iconPitchAlignment = const(IconPitchAlignment.Viewport),
      iconRotationAlignment = const(IconRotationAlignment.Map),
      iconAllowOverlap = const(true),
      iconIgnorePlacement = const(true),
  )
}

/** The waypoint marker: the theme's blip (GTA V four petals, RDR2 crossed ring, GTA VI pink dot, or a diamond). */
@Composable
@MaplibreComposable
fun ThemedDestination(at: GeographicCoordinate, theme: OverworldTheme, car: Boolean, id: String = "ow-destination") {
  val source =
      rememberGeoJsonSource(
          remember(at) { GeoJsonData.Features(FeatureCollection(Feature(geometry = Point(at.lng, at.lat), properties = buildJsonObject {}))) }
      )
  val painter =
      remember(theme.id) {
        when (theme.blipShape) {
          "quatrefoil" -> QuatrefoilPainter(theme.blipFill, theme.blipCenter, theme.blipStroke)
          "crossring" -> CrossRingPainter(theme.blipFill, theme.blipStroke)
          "dot" -> DotPainter(theme.blipFill, theme.blipStroke)
          else -> DiamondPainter(theme.blipFill, theme.blipStroke)
        }
      }
  // About the player marker's size, as the games draw their waypoint blips; GTA VI's dot is smaller.
  val size =
      iconSize(
          when (theme.blipShape) {
            "quatrefoil" -> if (car) 26.dp else 22.dp
            "crossring" -> if (car) 28.dp else 24.dp
            "dot" -> if (car) 20.dp else 17.dp
            else -> if (car) 24.dp else 20.dp
          }
      )
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

/** The GTA VI waypoint, from the official minimap: a pink dot with a thin dark edge. */
internal class DotPainter(private val fill: Color, private val edge: Color) : Painter() {
  override val intrinsicSize: Size = Size.Unspecified

  override fun DrawScope.onDraw() {
    val s = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(Color.Black.copy(alpha = 0.25f), s * 0.48f, c + Offset(0f, s * 0.03f))
    drawCircle(edge, s * 0.45f, c)
    drawCircle(fill, s * 0.37f, c)
  }
}

/**
 * A PNG drawn into whatever size MapLibre asks for, keeping its proportions (never stretched) and
 * centred. It is scaled down in halves first, so its outlines stay clean at the small sizes markers
 * are drawn at; one straight scale from 192 px left them ragged.
 */
internal class ArtPainter(private val art: ImageBitmap) : Painter() {
  override val intrinsicSize: Size = Size(art.width.toFloat(), art.height.toFloat())
  private var cached: ImageBitmap? = null

  override fun DrawScope.onDraw() {
    val fit = minOf(size.width / art.width, size.height / art.height)
    val w = (art.width * fit).roundToInt().coerceAtLeast(1)
    val h = (art.height * fit).roundToInt().coerceAtLeast(1)
    val scaled = cached?.takeIf { it.width == w && it.height == h } ?: downscale(art, w, h).also { cached = it }
    drawImage(scaled, topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f))
  }

  private fun downscale(src: ImageBitmap, w: Int, h: Int): ImageBitmap {
    var b = src.asAndroidBitmap()
    while (b.width / 2 >= w && b.height / 2 >= h) b = android.graphics.Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
    return android.graphics.Bitmap.createScaledBitmap(b, w, h, true).asImageBitmap()
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
