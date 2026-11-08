package com.thealgothrim.overworld.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thealgothrim.overworld.routing.OverworldRoutes
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.timeline.Segment
import org.json.JSONObject
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

/**
 * A Timeline day on the map, like Google's: the day's drives as lines in the world's route colour
 * over a dark edge, and each stop as a dot.
 */
@Composable
@MaplibreComposable
fun TimelineDayLayer(segments: List<Segment>, theme: OverworldTheme) {
  val lines =
      rememberGeoJsonSource(
          remember(segments) {
            val features =
                segments.filter { it.move && it.path != null }.mapNotNull { s ->
                  val points = OverworldRoutes.decodePolyline6(s.path!!)
                  if (points.size < 2) null
                  else """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[${points.joinToString(",") { "[${it.lng},${it.lat}]" }}]}}"""
                }
            GeoJsonData.JsonString("""{"type":"FeatureCollection","features":[${features.joinToString(",")}]}""")
          }
      )
  val stops =
      rememberGeoJsonSource(
          remember(segments) {
            val features =
                segments.filter { !it.move }.map { s ->
                  """{"type":"Feature","properties":{"name":${JSONObject.quote(s.name ?: "")}},"geometry":{"type":"Point","coordinates":[${s.at.lng},${s.at.lat}]}}"""
                }
            GeoJsonData.JsonString("""{"type":"FeatureCollection","features":[${features.joinToString(",")}]}""")
          }
      )
  LineLayer(id = "ow-timeline-edge", source = lines, color = const(Color(0xCC000000)), width = const(8.dp), cap = const(LineCap.Round), join = const(LineJoin.Round))
  LineLayer(id = "ow-timeline-line", source = lines, color = const(theme.routeLine), width = const(4.5.dp), cap = const(LineCap.Round), join = const(LineJoin.Round))
  CircleLayer(id = "ow-timeline-stops", source = stops, radius = const(7.dp), color = const(theme.routeLine), strokeColor = const(Color.Black), strokeWidth = const(2.5.dp))
}
