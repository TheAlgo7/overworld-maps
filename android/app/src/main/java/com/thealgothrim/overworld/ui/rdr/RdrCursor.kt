package com.thealgothrim.overworld.ui.rdr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.theme.GameFonts
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position
import uniffi.ferrostar.GeographicCoordinate

/**
 * Red Dead's map cursor on a picked place: four red ticks round it with a gap in the middle, and a
 * small dark label beside it with the place's name, the way the game marks the point under its
 * cursor ("Waypoint", "Marker", a camp's name: Gaurav's captures 18, 23 and 27). [lift] raises the
 * centre to the middle of a pin standing on the point. Drawn over the map in its own coordinates.
 */
@Composable
fun RdrCursor(camera: CameraState, at: GeographicCoordinate, label: String, lift: Dp = 0.dp, modifier: Modifier = Modifier) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    // Read here, so only the cursor follows the camera.
    camera.position
    val projection = camera.projection ?: return@BoxWithConstraints
    val p = projection.screenLocationFromPosition(Position(at.lng, at.lat))
    if (p.x < 0.dp || p.y < 0.dp || p.x > maxWidth || p.y > maxHeight) return@BoxWithConstraints
    val cx = p.x
    val cy = p.y - lift
    val box = 64.dp
    Canvas(Modifier.offset(cx - box / 2, cy - box / 2).size(box)) {
      val c = Offset(size.width / 2, size.height / 2)
      val gap = 11.dp.toPx()
      val len = 13.dp.toPx()
      val w = 2.5.dp.toPx()
      val red = Color(0xFFCC0000)
      val edge = Color(0x991B1A1A)
      for ((dx, dy) in listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f)) {
        val a = Offset(c.x + dx * gap, c.y + dy * gap)
        val b = Offset(c.x + dx * (gap + len), c.y + dy * (gap + len))
        drawLine(edge, a, b, strokeWidth = w + 2.dp.toPx())
        drawLine(red, a, b, strokeWidth = w)
      }
    }
    Box(
        Modifier.offset(cx + 20.dp, cy - 30.dp)
            .widthIn(max = 220.dp)
            .background(Color(0xE61B1A1A))
            .border(1.dp, Color(0x66E6E6E6))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
      Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = GameFonts.lino, fontSize = 14.sp, color = Rdr.White))
    }
  }
}
