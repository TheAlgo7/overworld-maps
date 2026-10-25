package com.thealgothrim.overworld.ui.vi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import org.maplibre.compose.camera.CameraProjection
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position
import uniffi.ferrostar.GeographicCoordinate

/**
 * What GTA VI pins to its minimap's edge, in Rockstar's footage and the game's own driving footage:
 * the "N" disc, sliding round the border so it always points north, and the pink waypoint dot
 * whenever the destination is off the map, sitting on the border in its direction. Drawn over the
 * map in the map's own coordinates (fill the same area as the map); [room] keeps both inside the
 * part of the screen the HUD uses.
 */
@Composable
fun ViEdgeMarkers(camera: CameraState, destination: GeographicCoordinate?, room: PaddingValues, modifier: Modifier = Modifier) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    // Read here, so only the markers follow the camera every frame.
    val position = camera.position
    val projection = camera.projection ?: return@BoxWithConstraints
    val left = room.calculateLeftPadding(LayoutDirection.Ltr) + EDGE_INSET
    val right = maxWidth - room.calculateRightPadding(LayoutDirection.Ltr) - EDGE_INSET
    val top = room.calculateTopPadding() + EDGE_INSET
    val bottom = maxHeight - room.calculateBottomPadding() - EDGE_INSET
    if (right <= left || bottom <= top) return@BoxWithConstraints
    // Rays start at the arrow (the camera's target, where the camera's padding puts it).
    val arrow = projection.screenLocationFromPosition(position.target)
    val origin = DpOffset(arrow.x.coerceIn(left, right), arrow.y.coerceIn(top, bottom))

    /** Where a ray from [origin], [angle] degrees clockwise from screen-up, meets the border. */
    fun border(angle: Double): DpOffset {
      val r = Math.toRadians(angle)
      val dx = sin(r)
      val dy = -cos(r)
      val tx = when { dx > 1e-6 -> (right - origin.x).value / dx; dx < -1e-6 -> (left - origin.x).value / dx; else -> Double.MAX_VALUE }
      val ty = when { dy > 1e-6 -> (bottom - origin.y).value / dy; dy < -1e-6 -> (top - origin.y).value / dy; else -> Double.MAX_VALUE }
      val t = min(tx, ty)
      return DpOffset(origin.x + (dx * t).dp, origin.y + (dy * t).dp)
    }

    // North, from the map's bearing: straight up on a north-up map, round the border as it turns.
    EdgeDisc(border(-position.bearing), NORTH_SIZE, Color.Black, Color(0x55FFFFFF)) {
      Text("N", style = TextStyle(fontFamily = Vi.sansSemiBold, fontSize = 13.sp, color = Vi.White))
    }

    destination?.let { d ->
      val target = Position(d.lng, d.lat)
      val p = projection.screenLocationFromPosition(target)
      // On the map: the map's own waypoint shows it. The projection of a point past the horizon can
      // still land inside the frame, so it also has to be in the region the camera really sees.
      val onMap = p.x in left..right && p.y in top..bottom && sees(projection, target)
      if (!onMap) EdgeDisc(border(bearing(position.target, target) - position.bearing), WAYPOINT_SIZE, Vi.Pink, Color(0xFF2A0F1C))
    }
  }
}

@Composable
private fun EdgeDisc(at: DpOffset, size: Dp, fill: Color, ring: Color, content: @Composable () -> Unit = {}) {
  Box(
      Modifier.offset(at.x - size / 2, at.y - size / 2).size(size).background(fill, CircleShape).border(1.5.dp, ring, CircleShape),
      contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/** Whether [p] is inside the ground the camera shows (its near and far corners). */
private fun sees(projection: CameraProjection, p: Position): Boolean {
  val v = projection.queryVisibleRegion()
  val quad = listOf(v.nearLeft, v.nearRight, v.farRight, v.farLeft)
  var inside = false
  for (i in quad.indices) {
    val a = quad[i]
    val b = quad[(i + 1) % quad.size]
    if ((a.latitude > p.latitude) != (b.latitude > p.latitude)) {
      val x = a.longitude + (p.latitude - a.latitude) / (b.latitude - a.latitude) * (b.longitude - a.longitude)
      if (p.longitude < x) inside = !inside
    }
  }
  return inside
}

/** The initial bearing from [a] to [b], degrees clockwise from north. */
private fun bearing(a: Position, b: Position): Double {
  val la1 = Math.toRadians(a.latitude)
  val la2 = Math.toRadians(b.latitude)
  val dl = Math.toRadians(b.longitude - a.longitude)
  return Math.toDegrees(atan2(sin(dl) * cos(la2), cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dl)))
}

private val EDGE_INSET = 18.dp
private val NORTH_SIZE = 26.dp
private val WAYPOINT_SIZE = 20.dp
