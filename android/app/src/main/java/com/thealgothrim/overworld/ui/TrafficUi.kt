package com.thealgothrim.overworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.annotation.Speed
import com.thealgothrim.overworld.HazardAhead
import com.thealgothrim.overworld.map.RoadFeaturePainter
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.traffic.RoadFeatureKind
import com.thealgothrim.overworld.traffic.TrafficEta
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.skinPanel
import kotlin.math.roundToInt

/** Time-to-go colour: the theme's normal colour when clear, amber / red with traffic, like Google. */
fun SkinSpec.trafficColor(eta: TrafficEta?): Color =
    when (eta?.level) {
      null -> fg // no live data: don't claim the roads are clear
      1 -> Color(0xFFFFB020)
      2 -> if (gta) Color(0xFFFF4B3E) else Color(0xFFE0301E)
      else -> good
    }

/** "+6 min of traffic", or null when clear or unknown. */
fun trafficNote(eta: TrafficEta?): String? {
  val delay = eta?.delaySeconds ?: return null
  if (delay < 60) return null
  return "+${formatDuration(delay)} of traffic"
}

/** The alert under the turn banner: "Accident ahead · 600 m". */
@Composable
fun HazardStrip(spec: SkinSpec, hazard: HazardAhead, modifier: Modifier = Modifier, textSize: TextUnit = 17.sp, icon: Dp = 34.dp) {
  val kind = hazard.feature.kind
  val painter = RoadFeaturePainter(kind, spec.skin)
  Row(modifier.skinPanel(spec).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Canvas(Modifier.size(icon)) { with(painter) { draw(size) } }
    Spacer(Modifier.width(12.dp))
    Column {
      val what = if (kind == RoadFeatureKind.SPEED_CAMERA) "Speed camera ahead" else "${kind.label} ahead"
      SkinText(what, spec.body, textSize, spec.fg)
      val extra = listOfNotNull(formatDistance(hazard.distance), hazard.feature.description?.takeIf { it.length < 40 })
      SkinText(extra.joinToString("  ·  "), spec.body, textSize * 0.8f, spec.sub)
    }
  }
}

/** Speed badge, bottom-left as in Google Maps; shows the limit beside it when OpenStreetMap has one. */
@Composable
fun SpeedBadge(spec: SkinSpec, uiState: NavigationUiState, modifier: Modifier = Modifier, big: Boolean = false) {
  val kmh = uiState.location?.speed?.value?.let { (it * 3.6).roundToInt().coerceAtLeast(0) }
  val limit = (uiState.currentAnnotation?.speed as? Speed.Value)?.let {
    if (it.unit.name.contains("MILE", ignoreCase = true)) (it.value * 1.609).roundToInt() else it.value.roundToInt()
  }
  val over = kmh != null && limit != null && kmh > limit + 3
  val d = if (big) 70.dp else 58.dp
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Box(
        Modifier.size(d)
            .background(if (spec.gta) Color(0xE6000000) else Color(0xF21B1A1A), CircleShape)
            .border(2.dp, if (over) Color(0xFFFF3B30) else spec.fg.copy(alpha = if (spec.gta) 0.25f else 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SkinText(kmh?.toString() ?: "--", spec.number, if (big) 26.sp else 22.sp, if (over) Color(0xFFFF3B30) else spec.fg)
        SkinText("km/h", spec.body, if (big) 11.sp else 9.sp, spec.sub)
      }
    }
    if (limit != null) {
      Spacer(Modifier.width(6.dp))
      // Indian limit sign: white disc, red ring.
      Box(Modifier.size(d * 0.72f).background(Color.White, CircleShape).border(d * 0.08f, Color(0xFFD32F2F), CircleShape), contentAlignment = Alignment.Center) {
        SkinText(limit.toString(), spec.number, if (big) 20.sp else 17.sp, Color.Black)
      }
    }
  }
}

