package com.thealgothrim.overworld.theme

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.thealgothrim.overworld.R

/**
 * The GTA V fonts: Pricedown (money counter, big messages), Chalet London 1960 (menus, help text)
 * and Chalet Comprime Cologne (HUD location and distance). They are loaded from
 * `src/local/assets/fonts` when present, which is gitignored, and fall back to free fonts
 * (Passion One, Barlow, Barlow Condensed) so the app always builds.
 */
object GameFonts {
  var price: FontFamily = FontFamily(Font(R.font.passion_one_bold, FontWeight.Bold))
    private set
  var menu: FontFamily = UiFont
    private set
  var condensed: FontFamily = FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold))
    private set

  // RDR2: RDR Lino (display: titles, prompts, big messages), Hapna Slab Serif (body, help text).
  var lino: FontFamily = FontFamily(Font(R.font.im_fell_english, FontWeight.Normal))
    private set
  var hapna: FontFamily = UiFont
    private set

  private var loaded = false

  fun init(context: Context) {
    if (loaded) return
    loaded = true
    val assets = context.assets
    val have = runCatching { assets.list("fonts")?.toSet() }.getOrNull().orEmpty()
    if ("pricedown.ttf" in have) price = FontFamily(Font("fonts/pricedown.ttf", assets))
    if ("chalet-london.otf" in have) menu = FontFamily(Font("fonts/chalet-london.otf", assets))
    if ("chalet-comprime.ttf" in have) condensed = FontFamily(Font("fonts/chalet-comprime.ttf", assets))
    if ("rdr-lino.ttf" in have) lino = FontFamily(Font("fonts/rdr-lino.ttf", assets))
    if ("hapna-slab.ttf" in have) hapna = FontFamily(Font("fonts/hapna-slab.ttf", assets))
  }
}
