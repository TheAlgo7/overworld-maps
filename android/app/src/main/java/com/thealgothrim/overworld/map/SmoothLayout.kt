package com.thealgothrim.overworld.map

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Layout changes glide instead of jumping: Android Auto switching between full screen and split
 * screen, its cards coming and going, the phone's trip sheet opening. A spring with a little give,
 * settling in about half a second.
 */
fun <T> layoutSpring() = spring<T>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

/** [target], each side easing to its new value on [layoutSpring]. */
@Composable
fun animatedPadding(target: PaddingValues, label: String): PaddingValues {
  val ltr = LayoutDirection.Ltr
  val start by animateDpAsState(target.calculateStartPadding(ltr), layoutSpring<Dp>(), label = "$label-start")
  val top by animateDpAsState(target.calculateTopPadding(), layoutSpring<Dp>(), label = "$label-top")
  val end by animateDpAsState(target.calculateEndPadding(ltr), layoutSpring<Dp>(), label = "$label-end")
  val bottom by animateDpAsState(target.calculateBottomPadding(), layoutSpring<Dp>(), label = "$label-bottom")
  return PaddingValues(start = start.coerceAtLeast(Dp(0f)), top = top.coerceAtLeast(Dp(0f)), end = end.coerceAtLeast(Dp(0f)), bottom = bottom.coerceAtLeast(Dp(0f)))
}

/** [target] easing on [layoutSpring]. */
@Composable
fun animatedScale(target: Float, label: String): Float {
  val v by animateFloatAsState(target, layoutSpring(), label = label)
  return v
}
