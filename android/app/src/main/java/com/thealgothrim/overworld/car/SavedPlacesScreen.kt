package com.thealgothrim.overworld.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.ui.skin.GameIcon

/**
 * Saved places on the car screen: Home, Work, starred and recent destinations. Tapping one starts
 * the trip straight away, like Google Maps' shortcuts in Android Auto. Drawn by Android Auto as a
 * standard list, which keeps it quick to use while parked.
 */
class SavedPlacesScreen(carContext: CarContext) : Screen(carContext) {
  override fun onGetTemplate(): Template =
      savedPlacesTemplate(withBack = true) { place ->
        AppModule.viewModel.startNavigation(place.coordinate, place.name)
        screenManager.pop()
      }
}

/** Home, Work, starred and recent places; [onPick] starts the trip. Its own screen or a map panel. */
internal fun savedPlacesTemplate(withBack: Boolean, onPick: (Place) -> Unit): ListTemplate {
  val saved = AppModule.saved.state.value
  // Each with the world's own icon: home, work, a star, a clock for the recent ones.
  val entries =
      buildList {
            saved.home?.let { add(Triple("Home", it, GameIcon.HOME)) }
            saved.work?.let { add(Triple("Work", it, GameIcon.WORK)) }
            saved.starred.forEach { add(Triple(it.name, it, GameIcon.STAR_FILLED)) }
            saved.recents.forEach { add(Triple(it.name, it, GameIcon.RECENT)) }
          }
          .distinctBy { (_, p, _) -> p.coordinate }
          .take(MAX_ROWS)

  val list = ItemList.Builder().setNoItemsMessage(com.thealgothrim.overworld.ui.NO_SAVED_PLACES)
  entries.forEach { (title, place, icon) ->
    list.addItem(
        Row.Builder()
            .setTitle(title)
            .setImage(carIcon(icon))
            .apply { subtitle(title, place)?.let { addText(it) } }
            .setOnClickListener { onPick(place) }
            .build()
    )
  }
  return ListTemplate.Builder().setTitle("Saved").apply { if (withBack) setHeaderAction(Action.BACK) }.setSingleList(list.build()).build()
}

private fun subtitle(title: String, place: Place): String? =
    when {
      title != place.name -> place.name
      place.detail.isNotBlank() -> place.detail
      else -> null
    }

// Android Auto caps list length while driving; keep well inside it.
private const val MAX_ROWS = 6
