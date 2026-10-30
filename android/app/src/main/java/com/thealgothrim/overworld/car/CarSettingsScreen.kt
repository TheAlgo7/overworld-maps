package com.thealgothrim.overworld.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.lifecycle.lifecycleScope
import com.thealgothrim.overworld.AppModule
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

/**
 * The settings that matter in the car, on the car screen: changing them used to mean taking the
 * phone out of its mount. Android Auto draws it as a standard list, so it stays quick to use; the
 * phone's Settings keep the rest (places, Google, the phone-locked setup).
 */
class CarSettingsScreen(carContext: CarContext) : Screen(carContext) {
  private val store = AppModule.themeStore
  private val viewModel = AppModule.viewModel

  init {
    // A change from the phone (or from this list) shows here at once.
    merge(store.theme, store.details, store.mapTime, store.carGameHud, store.carCamButton, viewModel.cameraBeep)
        .onEach { invalidate() }
        .launchIn(lifecycleScope)
  }

  override fun onGetTemplate(): Template {
    val theme = store.theme.value
    val details = store.details.value
    val live = AppModule.traffic.flowTilesUrl != null

    val map =
        ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Theme")
                    .addText("${theme.name}. Tap for the next game's map.")
                    .setOnClickListener { store.cycle() }
                    .build()
            )
            .apply {
              if (theme.variants.isNotEmpty()) {
                val time = store.mapTime.value
                addItem(
                    Row.Builder()
                        .setTitle("Time of day")
                        .addText("${time.label}. Tap to step through Auto, Day, Dusk and Night.")
                        .setOnClickListener { store.setMapTime(time.next()) }
                        .build()
                )
              }
            }
            .addItem(toggle("Place icons", "Shops, food, petrol and more on the map.", details.carPlaceIcons) { store.setDetails(details.copy(carPlaceIcons = it)) })
            .apply {
              if (live) addItem(toggle("Traffic on every road", "Your route always shows its own.", details.traffic) { store.setDetails(details.copy(traffic = it)) })
              if (theme.buildings3d) addItem(toggle("3D buildings", null, details.buildings3d) { store.setDetails(details.copy(buildings3d = it)) })
            }
            .build()

    val driving =
        ItemList.Builder()
            .addItem(toggle("Game HUD", "Turn, time and street in the game's style. Off uses Android Auto's cards.", store.carGameHud.value) { store.setCarGameHud(it) })
            .addItem(toggle("Camera beep", "Two notes as a speed camera comes within 500 m.", viewModel.cameraBeep.value) { viewModel.setCameraBeep(it) })
            .addItem(toggle("+ Cam button", "Mark a speed camera as you pass it.", store.carCamButton.value) { store.setCarCamButton(it) })
            .build()

    return ListTemplate.Builder()
        .setTitle("Settings")
        .setHeaderAction(Action.BACK)
        .addSectionedList(SectionedItemList.create(map, "Map"))
        .addSectionedList(SectionedItemList.create(driving, "Driving"))
        .build()
  }

  private fun toggle(title: String, text: String?, on: Boolean, set: (Boolean) -> Unit): Row =
      Row.Builder()
          .setTitle(title)
          .apply { text?.let { addText(it) } }
          .setToggle(Toggle.Builder { set(it) }.setChecked(on).build())
          .build()
}
