package com.thealgothrim.overworld

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thealgothrim.overworld.map.ChevronPainter
import com.thealgothrim.overworld.map.QuatrefoilPainter
import com.thealgothrim.overworld.theme.THEMES
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Draws the map icons the same way MapLibre Compose does and saves them for inspection. */
@RunWith(AndroidJUnit4::class)
class PainterRenderTest {
  private fun render(painter: Painter, px: Int, name: String): Int {
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
      with(painter) { draw(size) }
    }
    val android = bitmap.asAndroidBitmap()
    val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
    File(dir, "$name.png").outputStream().use { android.compress(Bitmap.CompressFormat.PNG, 100, it) }
    var opaque = 0
    for (y in 0 until px) for (x in 0 until px) if ((android.getPixel(x, y) ushr 24) > 0) opaque++
    return opaque
  }

  @Test
  fun iconsDrawSomething() {
    val t = THEMES.first { it.id == "gta5" }
    val arrow = render(ChevronPainter(t.puckFill, t.puckShade, t.puckStroke, glow = false), 120, "arrow")
    val blip = render(QuatrefoilPainter(t.blipFill, t.blipCenter, t.blipStroke), 120, "blip")
    android.util.Log.i("PainterRenderTest", "arrow opaque px=$arrow blip opaque px=$blip")
    assertTrue("arrow drew nothing", arrow > 500)
    assertTrue("blip drew nothing", blip > 500)
  }
}
