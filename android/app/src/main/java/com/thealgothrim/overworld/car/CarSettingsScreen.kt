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
import com.thealgothrim.overworld.theme.MapTime
import com.thealgothrim.overworld.theme.OverworldTheme
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

/**
 * The settings that matter in the car, on the car screen: changing them used to mean taking the
 * phone out of its mount. Android Auto draws it as a standard list, so it stays quick to use; the
 * phone's Settings keep the rest (places, Google, the phone-locked setup). The map ones are the
 * car's own: the phone keeps its theme and switches.
 */
class CarSettingsScreen(carContext: CarContext) : Screen(carContext) {
  private val store = AppModule.themeStore
  private val viewModel = AppModule.viewModel

  init {
    // A change from the phone (or from this list) shows here at once.
    merge(store.carTheme, store.carDetails, store.carMapTime, store.carGameHud, store.carCamButton, viewModel.cameraBeep)
        .onEach { invalidate() }
        .launchIn(lifecycleScope)
  }

  override fun onGetTemplate(): Template = carSettingsTemplate(withBack = true)
}

/** The car's Settings list: its own screen on older Android Auto, a panel over the map on newer. */
internal fun carSettingsTemplate(withBack: Boolean): ListTemplate {
  val store = AppModule.themeStore
  val viewModel = AppModule.viewModel
  val theme = store.carTheme.value
  val details = store.carDetails.value
  val live = AppModule.traffic.flowTilesUrl != null

  val map =
      ItemList.Builder()
          .addItem(
              Row.Builder()
                  .setTitle("Theme")
                  .addText("${theme.name}${fixedLook(theme)}. Tap for the next game's map.")
                  .setOnClickListener { store.cycleCar() }
                  .build()
          )
          .apply {
            if (theme.timeOfDay) {
              val time = store.carMapTime.value
              addItem(
                  Row.Builder()
                      .setTitle("Time of day")
                      .addText("${if (time == MapTime.AUTO) "Auto, with the car's day and night" else time.label}. Tap to step through Auto, Day, Dusk and Night.")
                      .setOnClickListener { store.setCarMapTime(time.next()) }
                      .build()
              )
            }
          }
          .addItem(toggle("Place icons", "Shops, food, petrol and more on the map.", details.placeIcons) { store.setCarDetails(details.copy(placeIcons = it)) })
          .apply {
            if (live) addItem(toggle("Traffic on every road", "Your route always shows its own.", details.traffic) { store.setCarDetails(details.copy(traffic = it)) })
            if (theme.buildings3d) addItem(toggle("3D buildings", null, details.buildings3d) { store.setCarDetails(details.copy(buildings3d = it)) })
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
      .apply { if (withBack) setHeaderAction(Action.BACK) }
      .addSectionedList(SectionedItemList.create(map, "Map"))
      .addSectionedList(SectionedItemList.create(driving, "Driving"))
      .build()
}

/** A theme with one map whatever the hour says so: GTA V's is always dark, Red Dead's always paper. */
private fun fixedLook(theme: OverworldTheme): String =
    when {
      theme.timeOfDay -> ""
      theme.dark -> ", always dark"
      else -> ", always light"
    }

private fun toggle(title: String, text: String?, on: Boolean, set: (Boolean) -> Unit): Row =
    Row.Builder()
        .setTitle(title)
        .apply { text?.let { addText(it) } }
        .setToggle(Toggle.Builder { set(it) }.setChecked(on).build())
        .build()
