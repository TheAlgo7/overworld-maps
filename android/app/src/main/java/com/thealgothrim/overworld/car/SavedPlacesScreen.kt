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

/**
 * Saved places on the car screen: Home, Work, starred and recent destinations. Tapping one starts
 * the trip straight away, like Google Maps' shortcuts in Android Auto. Drawn by Android Auto as a
 * standard list, which keeps it quick to use while parked.
 */
class SavedPlacesScreen(carContext: CarContext) : Screen(carContext) {
  override fun onGetTemplate(): Template {
    val saved = AppModule.saved.state.value
    val entries =
        buildList {
              saved.home?.let { add("Home" to it) }
              saved.work?.let { add("Work" to it) }
              saved.starred.forEach { add(it.name to it) }
              saved.recents.forEach { add(it.name to it) }
            }
            .distinctBy { (_, p) -> p.coordinate }
            .take(MAX_ROWS)

    val list = ItemList.Builder().setNoItemsMessage("Save places on your phone to see them here.")
    entries.forEach { (title, place) ->
      list.addItem(
          Row.Builder()
              .setTitle(title)
              .apply { subtitle(title, place)?.let { addText(it) } }
              .setOnClickListener { go(place) }
              .build()
      )
    }
    return ListTemplate.Builder().setTitle("Saved").setHeaderAction(Action.BACK).setSingleList(list.build()).build()
  }

  private fun subtitle(title: String, place: Place): String? =
      when {
        title != place.name -> place.name
        place.detail.isNotBlank() -> place.detail
        else -> null
      }

  private fun go(place: Place) {
    AppModule.viewModel.startNavigation(place.coordinate, place.name)
    screenManager.pop()
  }

  private companion object {
    // Android Auto caps list length while driving; keep well inside it.
    const val MAX_ROWS = 6
  }
}
