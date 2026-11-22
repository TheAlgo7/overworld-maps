package com.thealgothrim.overworld.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.car.app.model.CarIcon
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.IconCompat
import com.thealgothrim.overworld.routing.CueKind
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.ui.drawLevelGlyph
import com.thealgothrim.overworld.ui.gta.Gta
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.turnArrowDrawing
import com.thealgothrim.overworld.ui.vi.Vi
import java.util.concurrent.ConcurrentHashMap
import uniffi.ferrostar.DrivingSide
import uniffi.ferrostar.VisualInstructionContent

/**
 * The world's turn icons for Android Auto's own turn card, in place of its stock arrows: each on a
 * tile in the world's route colour, like a game's GPS. GTA VI's is the game's own turn card (a dark
 * arrow on its pink tile, square-cornered on the card's edge in the game, rounded here); GTA V's
 * the waypoint purple, Red Dead's its route red, both square like their HUDs. The arrows are the
 * same drawings the phone's HUD uses (TurnArrow: never blank at a roundabout exit or a U-turn,
 * mirrored where India keeps left). A flyover or underpass choice gets its own picture.
 *
 * The icons are pictures with their own colours: Android Auto draws an icon that asks for no tint
 * as it is. Each is drawn once and kept.
 */
internal object CarArrows {
  private val cache = ConcurrentHashMap<String, CarIcon>()

  /** The turn's icon, or null when the turn has no drawing (Android Auto then shows its own). */
  fun turn(context: Context, content: VisualInstructionContent, side: DrivingSide?, skin: Skin): CarIcon? {
    val (res, mirrored) = turnArrowDrawing(context, content, side) ?: return null
    return cache.getOrPut("turn-$res-$mirrored-$skin") {
      tile(skin) { canvas, inset, px ->
        val arrow = ContextCompat.getDrawable(context, res)?.mutate() ?: return@tile
        DrawableCompat.setTint(arrow, ink(skin).toArgb())
        arrow.setBounds(inset, inset, px - inset, px - inset)
        canvas.save()
        if (mirrored) canvas.scale(-1f, 1f, px / 2f, px / 2f)
        arrow.draw(canvas)
        canvas.restore()
      }
    }
  }

  /** A flyover or underpass choice: the arch (or dip) picture on the world's tile. */
  fun level(kind: CueKind, skin: Skin): CarIcon =
      cache.getOrPut("level-$kind-$skin") {
        tile(skin) { canvas, inset, px ->
          val side = px - inset * 2
          val image = ImageBitmap(side, side)
          val ink = ink(skin)
          CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(side.toFloat(), side.toFloat())) {
            drawLevelGlyph(kind, ink, ink.copy(alpha = 0.5f))
          }
          canvas.drawBitmap(image.asAndroidBitmap(), inset.toFloat(), inset.toFloat(), null)
        }
      }

  /** The arrow's colour on the world's tile: GTA VI's dark ink on pink, the HUD white on the others. */
  private fun ink(skin: Skin): Color =
      when (skin) {
        Skin.GTA6 -> Vi.Ink
        Skin.GTA -> Gta.White
        Skin.RDR -> Rdr.White
      }

  /** The tile in the world's route colour: GTA V's waypoint purple, Red Dead's red, GTA VI's pink. */
  private fun tileColor(skin: Skin): Color =
      when (skin) {
        Skin.GTA -> Color(0xFFA44CF2)
        Skin.RDR -> Color(0xFFCC0000)
        Skin.GTA6 -> Vi.Pink
      }

  private fun tile(skin: Skin, draw: (AndroidCanvas, Int, Int) -> Unit): CarIcon {
    val px = ICON_PX
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tileColor(skin).toArgb() }
    // Rounded in every world, to sit in Android Auto's rounded card: square tiles there looked
    // pasted on (Gaurav, 21 Nov), even though GTA V's and Red Dead's own HUDs are square.
    val radius = px * 0.22f
    canvas.drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), radius, radius, paint)
    draw(canvas, (px * 0.16f).toInt(), px)
    return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
  }

  /** Android Auto's maneuver icons are drawn at up to 128 dp; this covers its densities. */
  private const val ICON_PX = 192
}
