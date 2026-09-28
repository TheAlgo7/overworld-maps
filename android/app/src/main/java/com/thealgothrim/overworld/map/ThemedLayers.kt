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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.maplibreui.routeline.RouteOverlayBuilder
import com.thealgothrim.overworld.theme.OverworldTheme
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

/** First label layer in every Overworld style; the route is drawn just under it. */
private const val FIRST_LABEL_LAYER = "label-water"

/**
 * MapLibre reads an icon's pixel ratio from the bitmap's density, which Android sets to the phone
 * screen's density, while MapLibre Compose draws the bitmap at the current display's density. On
 * the Android Auto surface those differ (car ~1x, phone ~3x) and icons came out a third of the
 * size. Scale the requested size so the icon lands at the intended dp on either display.
 */
@Composable
private fun iconSize(size: Dp): Dp {
  val local = LocalDensity.current.density
  val bitmap = DisplayMetrics.DENSITY_DEVICE_STABLE / DisplayMetrics.DENSITY_DEFAULT.toFloat()
  return size * (bitmap / local)
}

private fun widthByZoom(at10: Float, at18: Float) =
    interpolate(linear(), zoom(), 10 to const(at10.dp), 18 to const(at18.dp))

fun themedRouteOverlay(theme: OverworldTheme, car: Boolean) =
    RouteOverlayBuilder(
        navigationPath = { uiState: NavigationUiState ->
          val geometry = uiState.routeGeometry
          if (geometry != null && geometry.size >= 2) ThemedRouteLine(geometry, theme, car)
        }
    )

@Composable
@MaplibreComposable
fun ThemedRouteLine(points: List<GeographicCoordinate>, theme: OverworldTheme, car: Boolean) {
  val json =
      remember(points) {
        val coords = points.joinToString(",") { "[${it.lng},${it.lat}]" }
        """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coords]}}]}"""
      }
  val source = rememberGeoJsonSource(GeoJsonData.JsonString(json))
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
        width = widthByZoom(if (car) 7f else 5.5f, if (car) 22f else 18f),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
    LineLayer(
        id = "ow-route-line",
        source = source,
        color = const(theme.routeLine),
        width = widthByZoom(if (car) 4.5f else 3.5f, if (car) 15f else 12f),
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
      remember(theme.id) { ChevronPainter(theme.puckFill, theme.puckStroke, glow = theme.routeGlow != null) }
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

/** The waypoint marker: a diamond in the theme's accent. */
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
  val painter = remember(theme.id) { DiamondPainter(theme.hudAccent, theme.puckStroke) }
  val size = iconSize(30.dp)
  SymbolLayer(
      id = id,
      source = source,
      iconImage = image(painter, size = DpSize(size, size), drawAsSdf = false),
      iconAnchor = const(SymbolAnchor.Center),
      iconAllowOverlap = const(true),
      iconIgnorePlacement = const(true),
  )
}

private class ChevronPainter(
    private val fill: Color,
    private val stroke: Color,
    private val glow: Boolean,
) : Painter() {
  override val intrinsicSize: Size = Size.Unspecified

  override fun DrawScope.onDraw() {
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
