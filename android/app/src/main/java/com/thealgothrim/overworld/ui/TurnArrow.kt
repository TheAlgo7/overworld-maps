package com.thealgothrim.overworld.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.stadiamaps.ferrostar.composeui.views.components.maneuver.ManeuverImage
import java.util.Locale
import uniffi.ferrostar.DrivingSide
import uniffi.ferrostar.ManeuverModifier
import uniffi.ferrostar.ManeuverType
import uniffi.ferrostar.VisualInstructionContent

/**
 * The arrow for a turn: Ferrostar's ManeuverImage (Mapbox's arrow set), but never blank.
 *
 * Ferrostar looks the drawing up by name, "direction_<type>_<modifier>", and draws nothing when
 * that pair has none: leaving a roundabout or rotary, a roundabout turn, a U-turn. Indian routes
 * are full of those, so this picks the nearest pair that has a drawing. Mapbox draws roundabouts
 * and U-turns for right-hand traffic; where traffic keeps left (India) they are mirrored, with the
 * side swapped, so roundabouts circle clockwise and U-turns curl right. "End of road" (a
 * T-junction) draws the crossing road as a thin hollow bar that reads as a glitch at card size;
 * Google shows a plain turn there, so do we.
 */
@Composable
fun TurnArrow(content: VisualInstructionContent, side: DrivingSide?, tint: Color, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val arrow = remember(content, side) { content.drawnArrow(context, side) }
  Box(modifier.graphicsLayer { if (arrow.mirrored) scaleX = -1f }, contentAlignment = Alignment.Center) {
    ManeuverImage(arrow.content, tint = tint)
  }
}

private class Arrow(val content: VisualInstructionContent, val mirrored: Boolean)

private fun VisualInstructionContent.drawnArrow(context: Context, side: DrivingSide?): Arrow {
  fun drawn(type: ManeuverType, mod: ManeuverModifier?): Boolean {
    val name = listOfNotNull(type.name, mod?.name).joinToString("_").lowercase(Locale.ROOT)
    return context.resources.getIdentifier("direction_$name", "drawable", context.packageName) != 0
  }
  val given = maneuverType ?: return Arrow(this, false)
  val type =
      when (given) {
        ManeuverType.EXIT_ROUNDABOUT -> ManeuverType.ROUNDABOUT
        ManeuverType.EXIT_ROTARY -> ManeuverType.ROTARY
        ManeuverType.ROUNDABOUT_TURN, ManeuverType.END_OF_ROAD -> ManeuverType.TURN
        else -> given
      }
  val keepsLeft = side == DrivingSide.LEFT
  val circles = keepsLeft && (type == ManeuverType.ROUNDABOUT || type == ManeuverType.ROTARY)
  val uTurn = keepsLeft && maneuverModifier == ManeuverModifier.U_TURN
  val tries =
      listOf(
          Triple(type, if (circles) maneuverModifier?.otherSide() else maneuverModifier, circles || uTurn),
          Triple(ManeuverType.TURN, maneuverModifier, uTurn),
          Triple(ManeuverType.CONTINUE, maneuverModifier, uTurn),
          Triple(ManeuverType.CONTINUE, null, false),
      )
  val (t, m, mirrored) = tries.firstOrNull { (t, m, _) -> drawn(t, m) } ?: return Arrow(this, false)
  return Arrow(copy(maneuverType = t, maneuverModifier = m), mirrored)
}

private fun ManeuverModifier.otherSide(): ManeuverModifier =
    when (this) {
      ManeuverModifier.LEFT -> ManeuverModifier.RIGHT
      ManeuverModifier.RIGHT -> ManeuverModifier.LEFT
      ManeuverModifier.SLIGHT_LEFT -> ManeuverModifier.SLIGHT_RIGHT
      ManeuverModifier.SLIGHT_RIGHT -> ManeuverModifier.SLIGHT_LEFT
      ManeuverModifier.SHARP_LEFT -> ManeuverModifier.SHARP_RIGHT
      ManeuverModifier.SHARP_RIGHT -> ManeuverModifier.SHARP_LEFT
      else -> this
    }
