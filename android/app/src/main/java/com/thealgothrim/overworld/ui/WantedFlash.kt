package com.thealgothrim.overworld.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.Skin

/**
 * GTA's wanted flash over the map: red and blue in turns, like GTA V's radar while the police can
 * see you (its red tint measured at #9d6364 over the radar) and GTA VI's flashing search area.
 * It fires when the car passes a speed camera over the limit (OverworldViewModel.wantedAt), for
 * about three seconds. Red Dead's 1899 has no police lights, so it stays out of that world.
 * [car]: softer on the head unit, so it never glares at night.
 */
@Composable
fun WantedFlash(theme: OverworldTheme, car: Boolean, modifier: Modifier = Modifier) {
  if (theme.skin == Skin.RDR) return
  val at by AppModule.viewModel.wantedAt.collectAsState()
  val on by AppModule.prefs.wantedFlash.collectAsState()
  val t = remember { Animatable(1f) }
  LaunchedEffect(at) {
    if (at == 0L || System.currentTimeMillis() - at > FLASH_MS) return@LaunchedEffect
    t.snapTo(0f)
    t.animateTo(1f, tween(FLASH_MS.toInt(), easing = LinearEasing))
  }
  if (!on || t.value >= 1f) return
  val red = if (theme.skin == Skin.GTA6) Color(0xFFF92F45) else Color(0xFFE8262B)
  val blue = Color(0xFF2F63E8)
  Canvas(modifier.fillMaxSize()) {
    val ms = t.value * FLASH_MS
    // Turns of 400 ms, each easing in and out so it pulses rather than strobes.
    val turn = (ms / TURN_MS).toInt()
    val within = (ms % TURN_MS) / TURN_MS
    val pulse = 1f - kotlin.math.abs(within * 2f - 1f)
    // Fades out over the last turn.
    val fade = ((FLASH_MS - ms) / TURN_MS).coerceIn(0f, 1f)
    val strength = (if (car) 0.2f else 0.32f) * (0.35f + 0.65f * pulse) * fade
    drawRect(lerp(red, blue, if (turn % 2 == 0) 0f else 1f).copy(alpha = strength))
  }
}

private const val FLASH_MS = 3_200L
private const val TURN_MS = 400f
