package com.thealgothrim.overworld

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thealgothrim.overworld.map.ArtPainter
import com.thealgothrim.overworld.map.QuatrefoilPainter
import com.thealgothrim.overworld.theme.THEMES
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Draws the map icons the same way MapLibre Compose does and saves them for inspection. */
@RunWith(AndroidJUnit4::class)
class PainterRenderTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private fun render(painter: Painter, w: Int, h: Int, name: String): Bitmap {
    val bitmap = ImageBitmap(w, h)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
      with(painter) { draw(size) }
    }
    val android = bitmap.asAndroidBitmap()
    File(context.getExternalFilesDir(null)!!, "$name.png").outputStream().use { android.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return android
  }

  /** Width / height of the drawn (non-transparent) part. */
  private fun inkAspect(b: Bitmap): Float {
    var left = b.width; var right = -1; var top = b.height; var bottom = -1
    for (y in 0 until b.height) for (x in 0 until b.width) {
      if ((b.getPixel(x, y) ushr 24) > 128) {
        left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
      }
    }
    assertTrue("drew nothing", right >= left)
    return (right - left + 1).toFloat() / (bottom - top + 1)
  }

  @Test
  fun playerMarkersKeepTheirShape() {
    for (name in listOf("puck_gta5", "puck_rdr2", "puck_gta6")) {
      val id = context.resources.getIdentifier(name, "drawable", context.packageName)
      val art = BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false })
      val original = inkAspect(art)
      // As MapLibre asks for it on the phone, and in a box of the wrong shape: never stretched.
      val square = inkAspect(render(ArtPainter(art.asImageBitmap()), 72, 72, "${name}_72"))
      val wide = inkAspect(render(ArtPainter(art.asImageBitmap()), 120, 60, "${name}_wide"))
      android.util.Log.i("PainterRenderTest", "$name aspect $original, at 72 px $square, in a wide box $wide")
      assertEquals("$name stretched", original, square, 0.06f)
      assertEquals("$name stretched in a wide box", original, wide, 0.06f)
    }
  }

  @Test
  fun waypointDrawsSomething() {
    val t = THEMES.first { it.id == "gta5" }
    val blip = render(QuatrefoilPainter(t.blipFill, t.blipCenter, t.blipStroke), 120, 120, "blip")
    var opaque = 0
    for (y in 0 until 120) for (x in 0 until 120) if ((blip.getPixel(x, y) ushr 24) > 0) opaque++
    assertTrue("blip drew nothing", opaque > 500)
  }
}
