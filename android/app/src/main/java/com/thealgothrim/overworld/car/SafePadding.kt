package com.thealgothrim.overworld.car

import android.graphics.Rect
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * Padding that keeps the map's focus inside [area] (the part of the car screen Android Auto leaves
 * free), with the top pushed down by [top] of its height. Ferrostar's
 * surfaceStableFractionalPadding, clamped at zero: Compose throws on negative padding, so an area
 * reaching past the drawing surface (the simulator never reports one; a real head unit might)
 * would have closed the app in the car.
 */
@Composable
fun safeStablePadding(area: Rect?, top: Float = 0f): PaddingValues {
  if (area == null) return PaddingValues(0.dp)
  val surface = LocalWindowInfo.current.containerSize
  return with(LocalDensity.current) {
    PaddingValues(
        start = area.left.coerceAtLeast(0).toDp(),
        top = (area.top + area.height() * top).coerceAtLeast(0f).toDp(),
        end = (surface.width - area.right).coerceAtLeast(0).toDp(),
        bottom = (surface.height - area.bottom).coerceAtLeast(0).toDp(),
    )
  }
}
