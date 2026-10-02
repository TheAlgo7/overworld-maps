package com.thealgothrim.overworld.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.search.Nearby
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.traffic.metres
import com.thealgothrim.overworld.ui.formatDistance
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.drawGameIcon
import com.thealgothrim.overworld.ui.skin.icon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Finding a place on the car screen itself, so the phone can stay in its mount: a search (with
// Android Auto's keyboard while parked) and one-tap nearby places for emergencies. Picking a place
// shows the themed route preview on the map with Start, like a place picked on the phone.

/** Shows [place]'s route preview on the car map and closes the search screens. */
private fun Screen.preview(place: Place) {
  val vm = AppModule.viewModel
  vm.choose(place)
  vm.directions()
  screenManager.popToRoot()
}

private fun Screen.placeRows(places: List<Place>, empty: String = "Nothing found"): ItemList {
  val here = AppModule.viewModel.currentCoordinate ?: AppModule.viewModel.startPoint
  val list = ItemList.Builder().setNoItemsMessage(empty)
  places.take(MAX_ROWS).forEach { place ->
    val away = formatDistance(metres(here, place.coordinate))
    list.addItem(
        Row.Builder()
            .setTitle(place.name)
            .addText(listOf(away, place.detail).filter { it.isNotBlank() }.joinToString("  ·  "))
            .setOnClickListener { preview(place) }
            .build()
    )
  }
  return list.build()
}

/** Search by name, address, plus code or coordinates. Recent places show before typing. */
class CarSearchScreen(carContext: CarContext) : Screen(carContext) {
  private var query = ""
  private var results: List<Place>? = null
  private var job: Job? = null

  private fun find(text: String, wait: Long) {
    query = text
    job?.cancel()
    if (text.isBlank()) {
      results = null
      invalidate()
      return
    }
    job =
        lifecycleScope.launch {
          delay(wait)
          results = runCatching { AppModule.search.search(text, AppModule.viewModel.currentCoordinate) }.getOrDefault(emptyList())
          invalidate()
        }
  }

  override fun onGetTemplate(): Template {
    val callback =
        object : SearchTemplate.SearchCallback {
          override fun onSearchTextChanged(searchText: String) = find(searchText, 450)

          override fun onSearchSubmitted(searchText: String) = find(searchText, 0)
        }
    val shown = results ?: if (query.isBlank()) AppModule.saved.state.value.recents else null
    return SearchTemplate.Builder(callback)
        .setHeaderAction(Action.BACK)
        .setSearchHint("Place, address or plus code")
        .setShowKeyboardByDefault(true)
        .apply {
          if (shown == null) setLoading(true)
          else setItemList(placeRows(shown, empty = if (query.isBlank()) "Places you visit show here" else "Nothing found"))
        }
        .build()
  }
}

/** Petrol, food, parking, toilets, hospitals and hotels, one tap each. */
class CarNearbyScreen(carContext: CarContext) : Screen(carContext) {
  override fun onGetTemplate(): Template {
    val grid = ItemList.Builder()
    Nearby.entries.forEach { kind ->
      grid.addItem(
          GridItem.Builder()
              .setTitle(kind.label)
              .setImage(carIcon(kind.icon))
              .setOnClickListener { screenManager.push(CarNearbyListScreen(carContext, kind)) }
              .build()
      )
    }
    return GridTemplate.Builder().setTitle("Nearby").setHeaderAction(Action.BACK).setSingleList(grid.build()).build()
  }
}

/** The closest places of one kind, nearest first. */
class CarNearbyListScreen(carContext: CarContext, private val kind: Nearby) : Screen(carContext) {
  private var places: List<Place>? = null

  init {
    lifecycleScope.launch {
      val here = AppModule.viewModel.currentCoordinate ?: AppModule.viewModel.startPoint
      places = runCatching { AppModule.search.nearby(kind, here) }.getOrDefault(emptyList())
      invalidate()
    }
  }

  override fun onGetTemplate(): Template =
      ListTemplate.Builder()
          .setTitle(kind.label)
          .setHeaderAction(Action.BACK)
          .apply { places?.let { setSingleList(placeRows(it)) } ?: setLoading(true) }
          .build()
}

/** A game icon (the same drawings as the phone) as a white Android Auto icon. */
fun carIcon(icon: GameIcon): CarIcon {
  val size = 96
  val image = ImageBitmap(size, size)
  CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(size.toFloat(), size.toFloat())) {
    drawGameIcon(icon, Color.White)
  }
  return CarIcon.Builder(IconCompat.createWithBitmap(image.asAndroidBitmap())).build()
}

/** Android Auto shows at most six rows while driving. */
private const val MAX_ROWS = 6
