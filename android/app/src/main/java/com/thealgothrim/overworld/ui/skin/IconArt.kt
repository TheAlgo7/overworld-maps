package com.thealgothrim.overworld.ui.skin

import android.graphics.BitmapFactory
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.theme.Skin
import java.util.concurrent.ConcurrentHashMap

/**
 * Gaurav's generated icon set for each game (tools/make_icons.py): white pictograms with a black
 * edge for GTA V. They ship only in personal builds (local assets, like the game blips); without
 * them every icon falls back to the one drawn in code.
 */
object IconArt {
  private val cache = ConcurrentHashMap<String, Any>()
  private val none = Any()

  /** The icon called [name] in [skin]'s set (a GameIcon or RoadFeatureKind name, lowercase). */
  fun get(skin: Skin, name: String): ImageBitmap? {
    val path = "icons/${skin.game}/$name.png"
    val found =
        cache.getOrPut(path) {
          runCatching { AppModule.context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull() ?: none
        }
    return found as? ImageBitmap
  }

  fun get(skin: Skin, icon: GameIcon): ImageBitmap? = get(skin, icon.name.lowercase())
}

/** The folder name each game's art goes by, in assets and in the tools. */
val Skin.game: String
  get() =
      when (this) {
        Skin.GTA -> "gta5"
        Skin.RDR -> "rdr2"
        Skin.GTA6 -> "gta6"
      }

/**
 * The game whose icon set [GameIconView] draws from. Unset (previews, tests), it draws the icons
 * in code.
 */
val LocalIconSkin = compositionLocalOf<Skin?> { null }
