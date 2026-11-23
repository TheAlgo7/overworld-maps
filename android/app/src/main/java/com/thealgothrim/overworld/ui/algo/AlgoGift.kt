package com.thealgothrim.overworld.ui.algo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.R
import com.thealgothrim.overworld.theme.ThemeStore
import com.thealgothrim.overworld.ui.skin.SkinPrimaryButton
import com.thealgothrim.overworld.ui.skin.SkinSecondaryButton
import com.thealgothrim.overworld.ui.skin.spec
import java.time.LocalDate
import java.time.Month

/**
 * Algothrim is Gaurav's birthday gift (30 Nov 2026) to everyone who drives with Overworld. Once,
 * over the plain map, a card tells them it's there: drawn in Algothrim's own black and crimson
 * whatever world they're in, the way his site greets a visitor ("Hello, I'm" over his name), with
 * one tap to try it.
 */
object AlgoGift {
  /** His birthday. */
  fun isBirthday(today: LocalDate = LocalDate.now()): Boolean = today.month == Month.NOVEMBER && today.dayOfMonth == 30

  /** Whether the card shows at this start: not seen, not already in Algothrim, nothing else going on. */
  fun due(seen: Boolean, worldId: String, carConnected: Boolean, onTrip: Boolean): Boolean =
      !seen && worldId != "algo" && !carConnected && !onTrip
}

@Composable
fun AlgoGiftCard(onTry: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
  val spec = ThemeStore.byId("algo").spec
  val shape = RoundedCornerShape(22.dp)
  Column(
      modifier
          .widthIn(max = 480.dp)
          .fillMaxWidth()
          .algoGlass(corner = 22.dp, color = Color(0xF7000000))
          .background(
              Brush.radialGradient(0f to Color(0x47C9183C), 1f to Color.Transparent, center = Offset(0f, 0f), radius = 900f),
              shape,
          )
  ) {
    Box {
      Image(
          painterResource(R.drawable.theme_algo),
          contentDescription = "The Algothrim map",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
      )
      // The picture fades into the card, like the site's hero into its page.
      Box(
          Modifier.fillMaxWidth()
              .height(132.dp)
              .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xF7000000)))
      )
    }
    Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
      Text(
          if (AlgoGift.isBirthday()) "It's my birthday today" else "My birthday gift to you",
          color = Algo.CrimsonText,
          fontFamily = Algo.display,
          fontSize = 15.sp,
      )
      Text("Algothrim", color = Algo.White, fontFamily = Algo.displayBold, fontSize = 30.sp, lineHeight = 36.sp)
      Spacer(Modifier.height(8.dp))
      Text(
          "A new world in my own colours: a black stage lit in crimson, towers that stand up as you drive, one crimson route. For everyone who drives with Overworld.",
          color = Algo.Sub,
          fontFamily = Algo.sans,
          fontSize = 14.sp,
          lineHeight = 20.sp,
      )
      Text("Gaurav, 30 November", color = Algo.White, fontFamily = Algo.sansSemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
      Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkinPrimaryButton(spec, "Try it", onTry, Modifier.weight(1f))
        SkinSecondaryButton(spec, "Not now", onLater)
      }
    }
  }
}
