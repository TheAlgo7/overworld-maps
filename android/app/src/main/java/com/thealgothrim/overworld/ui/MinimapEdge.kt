package com.thealgothrim.overworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.map.CrossRingPainter
import com.thealgothrim.overworld.map.QuatrefoilPainter
import com.thealgothrim.overworld.theme.GameFonts
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.ui.gta.Gta
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.vi.Vi
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import org.maplibre.compose.camera.CameraProjection
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position
import uniffi.ferrostar.GeographicCoordinate

/**
 * What each game pins to its minimap's edge while driving, drawn round the map's edge because the
 * whole map is the minimap here:
 *  - North, sliding round the border so it always points north as the map turns: GTA V's and GTA
 *    VI's black "N" disc (radar_north; the official GTA VI minimap), Red Dead's N, E, S and W round
 *    the radar's rim with an arrow on the N (Gaurav's Red Dead Online captures, 11 Nov 2026).
 *  - The waypoint, when the destination is off the map, on the border in its direction: GTA V's
 *    purple petals, Red Dead's red crossed ring, GTA VI's pink dot.
 * Drawn over the map in the map's own coordinates (fill the same area as the map); [room] keeps
 * the markers inside the part of the screen the HUD leaves clear.
 */
@Composable
fun MinimapEdge(
    theme: OverworldTheme,
    camera: CameraState,
    destination: GeographicCoordinate?,
    room: PaddingValues,
    modifier: Modifier = Modifier,
    /**
     * What the HUD keeps on the map's edge, to step round (px in the window): the phone's speed
     * readout, the car's turn card, time card and street name. With north behind and to the left,
     * the N sat on the speed's "km/h"; on the car it tucked under the street name (13 Nov).
     */
    avoid: List<androidx.compose.ui.geometry.Rect> = emptyList(),
    /**
     * Android Auto's own cards are up and [room] is the whole map: the markers ride the screen's own
     * border, right to the edge beside the cards, like the game's radar rim, and step along it past
     * the [avoid] boxes instead of stopping in front of them. Sliding round the stable area's edge
     * put the N mid-screen beside the cards (Gaurav, 21 Nov).
     */
    cards: Boolean = false,
) {
  val skin = theme.skin
  val waypoint =
      // The world's waypoint in the route's colour (Settings > Route colour), by its marker shape.
      remember(theme.id, theme.blipShape, theme.blipFill, theme.blipStroke) {
        when (theme.blipShape) {
          "quatrefoil" -> QuatrefoilPainter(theme.blipFill, theme.blipCenter, theme.blipStroke)
          "crossring" -> CrossRingPainter(theme.blipFill, theme.blipStroke)
          else -> null
        }
      }
  val px = LocalDensity.current.density
  BoxWithConstraints(modifier.fillMaxSize()) {
    // Read here, so only the markers follow the camera every frame.
    val position = camera.position
    val projection = camera.projection ?: return@BoxWithConstraints
    val left = room.calculateLeftPadding(LayoutDirection.Ltr) + EDGE_INSET
    val right = maxWidth - room.calculateRightPadding(LayoutDirection.Ltr) - EDGE_INSET
    val top = room.calculateTopPadding() + EDGE_INSET
    val bottom = maxHeight - room.calculateBottomPadding() - EDGE_INSET
    if (right <= left || bottom <= top) return@BoxWithConstraints
    // Rays start at the arrow (the camera's target, where the camera's padding puts it), kept a
    // marker's width inside the border: from on the border, a marker behind the car sat on the arrow.
    val arrow = projection.screenLocationFromPosition(position.target)
    val inner = NORTH_SIZE
    val origin =
        DpOffset(
            arrow.x.coerceIn(left + inner, maxOf(left + inner, right - inner)),
            arrow.y.coerceIn(top + inner, maxOf(top + inner, bottom - inner)),
        )

    /**
     * [p], on the screen's border, moved along the border clear of the [avoid] boxes: to the
     * nearer side of whatever covers it.
     */
    fun clearOfCards(p: DpOffset): DpOffset {
      var x = p.x
      var y = p.y
      // All the way to the screen's own edge, beside Android Auto's cards too, as on the game's
      // radar rim (stepping round the card column held it in front of the cards; Gaurav, 21 Nov).
      // Only our own boxes are stepped round, along the border.
      for (a in avoid) {
        val m = AVOID_MARGIN
        val x0 = (a.left / px).dp - m
        val x1 = (a.right / px).dp + m
        val y0 = (a.top / px).dp - m
        val y1 = (a.bottom / px).dp + m
        if (x !in x0..x1 || y !in y0..y1) continue
        val onSide = x <= left + 1.dp || x >= right - 1.dp
        if (onSide) {
          y = listOf(y0, y1).filter { it in top..bottom }.minByOrNull { kotlin.math.abs((it - y).value) } ?: y
        } else {
          x = listOf(x0, x1).filter { it in left..right }.minByOrNull { kotlin.math.abs((it - x).value) } ?: x
        }
      }
      return DpOffset(x, y)
    }

    /** Where a ray from [origin], [angle] degrees clockwise from screen-up, meets the border. */
    fun border(angle: Double): DpOffset {
      val r = Math.toRadians(angle)
      val dx = sin(r)
      val dy = -cos(r)
      val tx = when { dx > 1e-6 -> (right - origin.x).value / dx; dx < -1e-6 -> (left - origin.x).value / dx; else -> Double.MAX_VALUE }
      val ty = when { dy > 1e-6 -> (bottom - origin.y).value / dy; dy < -1e-6 -> (top - origin.y).value / dy; else -> Double.MAX_VALUE }
      var t = min(tx, ty)
      if (cards) return clearOfCards(DpOffset(origin.x + (dx * t).dp, origin.y + (dy * t).dp))
      // Stop where the ray would enter the box to avoid (grown by a marker's half width), so the
      // marker sits beside it, still in its direction.
      for (a in avoid) {
        val m = AVOID_MARGIN.value
        val x0 = a.left / px - m
        val x1 = a.right / px + m
        val y0 = a.top / px - m
        val y1 = a.bottom / px + m
        val ox = origin.x.value
        val oy = origin.y.value
        val inside = ox in x0..x1 && oy in y0..y1
        if (!inside) {
          fun slab(o: Float, d: Double, lo: Float, hi: Float): Pair<Double, Double> =
              if (kotlin.math.abs(d) < 1e-9) (if (o in lo..hi) Double.NEGATIVE_INFINITY to Double.POSITIVE_INFINITY else Double.POSITIVE_INFINITY to Double.NEGATIVE_INFINITY)
              else ((lo - o) / d).let { a1 -> ((hi - o) / d).let { a2 -> minOf(a1, a2) to maxOf(a1, a2) } }
          val (xin, xout) = slab(ox, dx, x0, x1)
          val (yin, yout) = slab(oy, dy, y0, y1)
          val enter = maxOf(xin, yin)
          val exit = minOf(xout, yout)
          if (enter <= exit && enter > 0 && enter < t) t = enter
        }
      }
      return DpOffset(origin.x + (dx * t).dp, origin.y + (dy * t).dp)
    }

    // North, from the map's bearing: straight up on a north-up map, round the border as it turns.
    val north = -position.bearing
    when (skin) {
      Skin.GTA6 ->
          EdgeDisc(border(north), NORTH_SIZE, Color.Black, Color(0x55FFFFFF)) {
            Text("N", style = TextStyle(fontFamily = Vi.sansSemiBold, fontSize = 13.sp, color = Vi.White))
          }
      Skin.GTA -> GtaRadarNorth(border(north), NORTH_SIZE)
      // Algothrim: a black disc with a crimson rim and a white N in the site's Montserrat.
      Skin.ALGO ->
          EdgeDisc(border(north), NORTH_SIZE, Color.Black, com.thealgothrim.overworld.ui.algo.Algo.Crimson) {
            Text("N", style = TextStyle(fontFamily = com.thealgothrim.overworld.ui.algo.Algo.sansSemiBold, fontSize = 13.sp, color = com.thealgothrim.overworld.ui.algo.Algo.White))
          }
      Skin.RDR -> {
        // The rim's four letters; the N a size up with its arrow pointing out past it.
        RdrLetter("E", border(north + 90), 15)
        RdrLetter("S", border(north + 180), 15)
        RdrLetter("W", border(north + 270), 15)
        val n = border(north)
        RdrNorthArrow(n, north)
        RdrLetter("N", n, 19)
      }
    }

    destination?.let { d ->
      val target = Position(d.lng, d.lat)
      val p = projection.screenLocationFromPosition(target)
      // On the map: the map's own waypoint shows it, anywhere in the frame. Testing against the HUD's
      // inset box put a second dot on the edge beside a waypoint plainly in view near the top of the
      // tilted car map. The projection of a point past the horizon can still land inside the frame,
      // so it also has to map back to itself.
      val half = WAYPOINT_SIZE / 2
      val inFrame = p.x in half..(maxWidth - half) && p.y in half..(maxHeight - half)
      val onMap = inFrame && inFront(projection, p, target, position.target)
      if (!onMap) {
        val at = border(bearing(position.target, target) - position.bearing)
        when (skin) {
          else ->
            if (waypoint == null) EdgeDisc(at, WAYPOINT_SIZE, theme.blipFill, theme.blipStroke)
            else {
            val size = if (skin == Skin.RDR) 24.dp else 22.dp
            Image(waypoint, contentDescription = null, modifier = Modifier.offset(at.x - size / 2, at.y - size / 2).size(size))
          }
        }
      }
    }
  }
}

/**
 * GTA V's radar_north, drawn to the sprite (Gaurav's `GTA V - Radar North.png`, 24 px): a plain
 * black disc, no ring, a thin white N 13/24 of it tall. Drawn, not scaled up, so it stays sharp on
 * a car screen at three times the sprite's size.
 */
@Composable
private fun GtaRadarNorth(at: DpOffset, size: Dp) {
  Canvas(Modifier.offset(at.x - size / 2, at.y - size / 2).size(size)) {
    val u = this.size.width / 24f
    drawCircle(Color.Black)
    val n =
        Path().apply {
          moveTo(7.6f * u, 17.6f * u)
          lineTo(7.6f * u, 5.9f * u)
          lineTo(16.4f * u, 17.4f * u)
          lineTo(16.4f * u, 5.9f * u)
        }
    drawPath(
        n,
        Color.White,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.9f * u, join = androidx.compose.ui.graphics.StrokeJoin.Miter),
    )
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

/** One of Red Dead's rim letters: dark ink with a paper halo, so it reads over roads too. */
@Composable
private fun RdrLetter(letter: String, at: DpOffset, size: Int, light: Boolean = false) {
  val box = (size + 10).dp
  Box(Modifier.offset(at.x - box / 2, at.y - box / 2).size(box), contentAlignment = Alignment.Center) {
    Text(
        letter,
        style =
            TextStyle(
                fontFamily = GameFonts.lino,
                fontSize = size.sp,
                // On the corner dial's dark glass: paper letters with a dark halo.
                color = if (light) PAPER else Rdr.OffBlack,
                shadow = Shadow(color = if (light) Color(0xCC000000) else Color(0xE6E3CCA6), offset = Offset.Zero, blurRadius = 7f),
            ),
    )
  }
}

/**
 * The arrow on Red Dead's N: a slim black shaft out past the letter with a point on the end,
 * pointing north (the radar rim in Gaurav's captures 03 and 28).
 */
@Composable
private fun RdrNorthArrow(n: DpOffset, north: Double, light: Boolean = false) {
  val box = 52.dp
  Canvas(Modifier.offset(n.x - box / 2, n.y - box / 2).size(box).rotate(north.toFloat())) {
    val c = size.width / 2
    val dp = density
    // Distances out from the letter's centre, in dp.
    fun y(r: Float) = c - r * dp
    val halo = if (light) Color(0x99000000) else Color(0xE6E3CCA6)
    val ink = if (light) PAPER else Rdr.OffBlack
    val head =
        Path().apply {
          moveTo(c, y(25f))
          lineTo(c + 4.5f * dp, y(17f))
          lineTo(c, y(18.5f))
          lineTo(c - 4.5f * dp, y(17f))
          close()
        }
    drawLine(halo, Offset(c, y(11f)), Offset(c, y(19f)), strokeWidth = 4.5f * dp)
    drawPath(head, halo, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f * dp))
    drawLine(ink, Offset(c, y(11f)), Offset(c, y(19f)), strokeWidth = 2f * dp)
    drawPath(head, ink)
  }
}

/**
 * Whether [target], drawn at [screen], is really on the ground in view: the ground under that
 * screen point is [target] again. A point behind the camera or past the horizon projects into the
 * frame too, but the ground under its screen point is somewhere else.
 */
private fun inFront(projection: CameraProjection, screen: DpOffset, target: Position, from: Position): Boolean {
  val back = projection.positionFromScreenLocation(screen)
  return metres(back, target) < 30.0 + 0.05 * metres(from, target)
}

private fun metres(a: Position, b: Position): Double {
  val la1 = Math.toRadians(a.latitude)
  val la2 = Math.toRadians(b.latitude)
  val dla = la2 - la1
  val dlo = Math.toRadians(b.longitude - a.longitude)
  val h = sin(dla / 2) * sin(dla / 2) + cos(la1) * cos(la2) * sin(dlo / 2) * sin(dlo / 2)
  return 2 * 6_371_000.0 * asin(sqrt(h))
}

/** The initial bearing from [a] to [b], degrees clockwise from north. */
private fun bearing(a: Position, b: Position): Double {
  val la1 = Math.toRadians(a.latitude)
  val la2 = Math.toRadians(b.latitude)
  val dl = Math.toRadians(b.longitude - a.longitude)
  return Math.toDegrees(atan2(sin(dl) * cos(la2), cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dl)))
}

private val EDGE_INSET = 18.dp
private val AVOID_MARGIN = 15.dp
private val NORTH_SIZE = 26.dp
private val WAYPOINT_SIZE = 20.dp
private val PAPER = Color(0xFFE8D9BC)
