package com.thealgothrim.overworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.LayoutDirection
import com.thealgothrim.overworld.ui.skin.skinPanel
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position
import uniffi.ferrostar.GeographicCoordinate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.CueAhead
import com.thealgothrim.overworld.routing.CueKind
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText

/**
 * "Take the flyover" / "Don't take the flyover" (and the underpass versions) on the turn card, like
 * Google Maps' flyover callouts in India. At the route's own turn it's just the words under that
 * turn ("Bear left", then "Don't take the flyover"); before it, with how far ("In 300 m").
 */
@Composable
fun LevelCueRow(spec: SkinSpec, cue: CueAhead, modifier: Modifier = Modifier, textSize: TextUnit = 21.sp, glyph: Dp = 34.dp) {
  val words = if (cue.withTurn || cue.distance < NOW_M) cue.cue.kind.text else "In ${formatDistance(cue.distance)}, ${cue.cue.kind.text.replaceFirstChar { it.lowercase() }}"
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    LevelGlyph(cue.cue.kind, spec.accent, spec.fg.copy(alpha = 0.55f), Modifier.size(glyph))
    Spacer(Modifier.width(12.dp))
    SkinText(words, spec.body, textSize, spec.accent, maxLines = 2)
  }
}

/**
 * The choice as a picture: a road with a flyover's arch over it, or one dipping under. The way the
 * route goes is drawn bold in [route], the other way thin and broken in [other].
 */
@Composable
fun LevelGlyph(kind: CueKind, route: Color, other: Color, modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    val bold = Stroke(width = w * 0.13f, cap = StrokeCap.Round)
    val thin = Stroke(width = w * 0.07f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.1f, w * 0.08f)))
    val ground = Path().apply {
      moveTo(w * 0.06f, h * 0.78f)
      lineTo(w * 0.94f, h * 0.78f)
    }
    val levelled =
        Path().apply {
          if (kind.flyover) {
            // Up over the ground road and back down.
            moveTo(w * 0.06f, h * 0.78f)
            cubicTo(w * 0.3f, h * 0.78f, w * 0.3f, h * 0.2f, w * 0.5f, h * 0.2f)
            cubicTo(w * 0.7f, h * 0.2f, w * 0.7f, h * 0.78f, w * 0.94f, h * 0.78f)
          } else {
            // Down under it and back up.
            moveTo(w * 0.06f, h * 0.42f)
            cubicTo(w * 0.3f, h * 0.42f, w * 0.3f, h * 0.95f, w * 0.5f, h * 0.95f)
            cubicTo(w * 0.7f, h * 0.95f, w * 0.7f, h * 0.42f, w * 0.94f, h * 0.42f)
          }
        }
    val surface = if (kind.flyover) ground else Path().apply {
      moveTo(w * 0.06f, h * 0.42f)
      lineTo(w * 0.94f, h * 0.42f)
    }
    if (kind.takes) {
      drawPath(surface, other, style = thin)
      drawPath(levelled, route, style = bold)
    } else {
      drawPath(levelled, other, style = thin)
      drawPath(surface, route, style = bold)
    }
    // Which way: an arrowhead at the right end of the route's way.
    val tipY = if (kind.flyover || !kind.takes) (if (kind.flyover) h * 0.78f else h * 0.42f) else h * 0.42f
    val tip = Offset(w * 0.98f, tipY)
    drawLine(route, tip, Offset(w * 0.84f, tipY - h * 0.12f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
    drawLine(route, tip, Offset(w * 0.84f, tipY + h * 0.12f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
  }
}

/**
 * The call on the map itself, standing on the route where it splits, like Google Maps' "Take
 * flyover" callout on its map: the glyph and two or three words in a small card of the theme, a
 * stalk down to the split. Drawn over the map in the map's own coordinates (fill the same area as
 * the map), only while the split is in front of the car and inside [room].
 */
@Composable
fun LevelCallout(camera: CameraState, at: GeographicCoordinate, cue: CueAhead, spec: SkinSpec, room: PaddingValues, modifier: Modifier = Modifier) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    // Read here, so only the callout follows the camera every frame.
    camera.position
    val projection = camera.projection ?: return@BoxWithConstraints
    val target = Position(at.lng, at.lat)
    val p = projection.screenLocationFromPosition(target)
    val left = room.calculateLeftPadding(LayoutDirection.Ltr)
    val right = maxWidth - room.calculateRightPadding(LayoutDirection.Ltr)
    val top = room.calculateTopPadding() + CALLOUT_HEIGHT
    val bottom = maxHeight - room.calculateBottomPadding()
    if (p.x < left || p.x > right || p.y < top || p.y > bottom) return@BoxWithConstraints
    // A point past the horizon of the tilted car map still projects into the frame: check the ground there is the split.
    val back = projection.positionFromScreenLocation(p)
    if (kotlin.math.abs(back.latitude - at.lat) * 111_320.0 > BACK_SLACK_M || kotlin.math.abs(back.longitude - at.lng) * 97_000.0 > BACK_SLACK_M) return@BoxWithConstraints
    Box(Modifier.offset(p.x - STALK / 2, p.y - STALK_LENGTH).size(STALK, STALK_LENGTH).background(spec.accent))
    Box(Modifier.offset(p.x - DOT / 2, p.y - DOT / 2).size(DOT).clip(CircleShape).background(spec.accent))
    Row(
        Modifier.offset(p.x - CALLOUT_WIDTH / 2, p.y - STALK_LENGTH - CALLOUT_HEIGHT)
            .size(CALLOUT_WIDTH, CALLOUT_HEIGHT)
            .skinPanel(spec)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
      LevelGlyph(cue.cue.kind, spec.accent, spec.fg.copy(alpha = 0.55f), Modifier.size(26.dp))
      Spacer(Modifier.width(8.dp))
      SkinText(spec.title(cue.cue.kind.title), spec.title, spec.titleSize(17.sp, 18.sp), spec.fg, spacing = spec.titleSpacing)
    }
  }
}

/** Closer than this the card says it plainly, without the distance. */
private const val NOW_M = 60.0
private val CALLOUT_WIDTH = 190.dp
private val CALLOUT_HEIGHT = 44.dp
private val STALK = 3.dp
private val STALK_LENGTH = 26.dp
private val DOT = 10.dp
/** How far the ground under the projected point may be from the split and still count as it. */
private const val BACK_SLACK_M = 40.0
