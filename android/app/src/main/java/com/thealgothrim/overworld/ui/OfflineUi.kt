package com.thealgothrim.overworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.offline.OfflineMaps
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.SkinPanel
import com.thealgothrim.overworld.ui.skin.SkinPrimaryButton
import com.thealgothrim.overworld.ui.skin.SkinSecondaryButton
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.sheetFloat
import kotlin.time.Duration.Companion.milliseconds

/** The frame's margins from the window's edges: clear of the title on top and the panel below. */
private val FRAME_SIDE = 28.dp
private val FRAME_TOP = 120.dp
private val FRAME_BOTTOM = 300.dp

/**
 * "Download a map of this area?", like Google's offline map frame: the map flattens north-up, a
 * frame marks what will be kept, its rough size shows as the map moves, and Download starts it.
 */
@Composable
fun BoxScope.OfflineAreaPicker(spec: SkinSpec, mapState: NavigationMapState, suggestedName: String?, onClose: (String?) -> Unit) {
  // Flat and north up, so the frame is a true rectangle on the ground.
  LaunchedEffect(Unit) {
    mapState.cameraMode = NavigationCameraMode.FREE
    mapState.cameraState.animateTo(mapState.cameraState.position.copy(tilt = 0.0, bearing = 0.0, zoom = minOf(mapState.cameraState.position.zoom, 11.5)), duration = 500.milliseconds)
  }
  var name by remember { mutableStateOf(suggestedName ?: "") }
  var message by remember { mutableStateOf<String?>(null) }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val w = maxWidth
    val h = maxHeight
    // The box's corners on the ground, worked out again whenever the map moves.
    val box by remember {
      derivedStateOf {
        mapState.cameraState.position // read so a move recomputes
        val projection = mapState.cameraState.projection ?: return@derivedStateOf null
        val corners =
            listOf(
                DpOffset(FRAME_SIDE, FRAME_TOP),
                DpOffset(w - FRAME_SIDE, FRAME_TOP),
                DpOffset(FRAME_SIDE, h - FRAME_BOTTOM),
                DpOffset(w - FRAME_SIDE, h - FRAME_BOTTOM),
            ).map { projection.positionFromScreenLocation(it) }
        doubleArrayOf(corners.maxOf { it.latitude }, corners.maxOf { it.longitude }, corners.minOf { it.latitude }, corners.minOf { it.longitude })
      }
    }
    Canvas(Modifier.fillMaxSize()) {
      val left = FRAME_SIDE.toPx()
      val top = FRAME_TOP.toPx()
      val right = size.width - FRAME_SIDE.toPx()
      val bottom = size.height - FRAME_BOTTOM.toPx()
      val shade = Color(0x99000000)
      drawRect(shade, Offset.Zero, Size(size.width, top))
      drawRect(shade, Offset(0f, bottom), Size(size.width, size.height - bottom))
      drawRect(shade, Offset(0f, top), Size(left, bottom - top))
      drawRect(shade, Offset(right, top), Size(size.width - right, bottom - top))
      drawRect(spec.accent, Offset(left, top), Size(right - left, bottom - top), style = Stroke(3.dp.toPx()))
    }
    SkinPanel(spec, Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth()) {
      SkinText(spec.title("Download this area?"), spec.title, spec.titleSize(if (spec.vi) 22.sp else 20.sp, 22.sp), spec.fg, Modifier.padding(14.dp), spacing = spec.titleSpacing)
    }
    SkinPanel(spec, Modifier.align(Alignment.BottomCenter).sheetFloat(spec).fillMaxWidth()) {
      Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        val b = box
        val tiles = b?.let { OfflineMaps.tileCount(it[0], it[1], it[2], it[3]) } ?: 0
        val tooBig = tiles > OfflineMaps.MAX_TILES
        SkinText(
            when {
              b == null -> "Move the map to the area you want."
              tooBig -> "Too big to download at once. Zoom in a little."
              else -> "About ${OfflineMaps.estimateMegabytes(b[0], b[1], b[2], b[3])} MB. Pinch and drag to fit the frame."
            },
            spec.body, 15.sp, if (tooBig) spec.accent else spec.sub, maxLines = 2,
        )
        Gap(10)
        SkinField(spec, name, { name = it }, "Name, e.g. Delhi")
        message?.let {
          Gap(8)
          SkinText(it, spec.body, 14.sp, spec.accent, maxLines = 3)
        }
        Gap(12)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          SkinSecondaryButton(spec, "Cancel", { onClose(null) })
          SkinPrimaryButton(
              spec,
              "Download",
              {
                val area = box ?: return@SkinPrimaryButton
                if (tooBig) return@SkinPrimaryButton
                if (!AppModule.offline.canDownloadNow()) {
                  message = "You're not on Wi-Fi. Try again on Wi-Fi, or turn off Wi-Fi only in Settings > Offline."
                  return@SkinPrimaryButton
                }
                val label = name.trim().ifBlank { "Map ${AppModule.offline.areas.value.size + 1}" }
                AppModule.offline.download(label, area[0], area[1], area[2], area[3]) { error ->
                  if (error != null) message = error else onClose("Downloading $label. See it in Settings > Offline.")
                }
              },
              Modifier.weight(1f),
              GameIcon.DOWNLOAD,
          )
        }
      }
    }
  }
}

