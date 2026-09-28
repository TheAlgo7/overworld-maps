package com.thealgothrim.overworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.core.boundingBox
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.navigationCameraOptions
import com.stadiamaps.ferrostar.maplibreui.runtime.rememberNavigationMapState
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.OverworldViewModel
import com.thealgothrim.overworld.PlannerState
import com.thealgothrim.overworld.map.OverworldPhoneMap
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.THEMES
import com.thealgothrim.overworld.ui.gta.GtaBigMessage
import com.thealgothrim.overworld.ui.gta.GtaDriveHud
import com.thealgothrim.overworld.ui.gta.GtaPlanner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

@Composable
fun PhoneScreen(viewModel: OverworldViewModel = AppModule.viewModel) {
  val theme by AppModule.themeStore.theme.collectAsState()
  val uiState by viewModel.navigationUiState.collectAsState()
  val planner by viewModel.planner.collectAsState()
  val testDrive by viewModel.testDrive.collectAsState()
  val cameraOptions = navigationCameraOptions()
  val mapState = rememberNavigationMapState(navigationCameraOptions = cameraOptions)
  val scope = rememberCoroutineScope()
  val navigating = uiState.isNavigating()

  LaunchedEffect(navigating) { if (navigating) mapState.recenter(isNavigating = true) }
  LaunchedEffect(planner.destination) {
    val d = planner.destination ?: return@LaunchedEffect
    mapState.cameraMode = NavigationCameraMode.FREE
    mapState.cameraState.animateTo(
        CameraPosition(target = Position(d.coordinate.lng, d.coordinate.lat), zoom = 15.0),
        duration = 900.milliseconds,
    )
  }

  val gta = theme.id == "metro"
  val area by viewModel.area.collectAsState()
  var arrivedAt by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(Unit) {
    viewModel.arrived.collect {
      arrivedAt = it
      delay(5_000)
      arrivedAt = null
    }
  }
  val toggleOverview: () -> Unit = {
    if (mapState.isTrackingUser) {
      uiState.routeGeometry?.boundingBox()?.let {
        mapState.showRouteOverview(boundingBox = it, paddingValues = PaddingValues(64.dp))
      }
    } else mapState.recenter(isNavigating = true)
  }

  Box(Modifier.fillMaxSize().background(theme.page)) {
    OverworldPhoneMap(
        theme = theme,
        uiState = uiState,
        mapState = mapState,
        cameraOptions = cameraOptions,
        pickedDestination = planner.destination?.coordinate,
        attributionPadding =
            when {
              gta && navigating -> PaddingValues(top = 130.dp, end = 12.dp)
              gta -> PaddingValues(bottom = 80.dp, end = 12.dp)
              navigating -> PaddingValues(top = 150.dp, end = 12.dp)
              else -> PaddingValues(top = 110.dp, end = 12.dp)
            },
        attributionAlignment = if (gta && !navigating) Alignment.BottomEnd else Alignment.TopEnd,
        onLongPress = viewModel::dropPin,
    )
    if (theme.paperOverlay) PaperOverlay()

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
      if (gta && navigating) {
        GtaDriveHud(
            theme = theme,
            uiState = uiState,
            area = area,
            mapBearing = { mapState.cameraState.position.bearing },
            following = mapState.isTrackingUser,
            onMute = viewModel::toggleMute,
            onOverview = toggleOverview,
            onTheme = AppModule.themeStore::cycle,
            onEnd = viewModel::stopNavigation,
        )
      } else if (gta) {
        GtaPlanner(
            theme = theme,
            planner = planner,
            testDrive = testDrive,
            here = uiState.location?.coordinates,
            onQuery = viewModel::onQueryChange,
            onChoose = viewModel::choose,
            onClear = viewModel::clearDestination,
            onGo = viewModel::go,
            onTestDrive = viewModel::setTestDrive,
            onTheme = AppModule.themeStore::cycle,
            onLocate = { scope.launch { mapState.recenter(isNavigating = false) } },
        )
      } else if (navigating) {
        TurnBanner(theme, uiState, Modifier.align(Alignment.TopCenter))
        Column(Modifier.align(Alignment.BottomCenter)) {
          uiState.currentStepRoadName?.takeIf { it.isNotBlank() }?.let { road ->
            Text(
                theme.display(road),
                color = theme.hudFg,
                style = theme.hudText(24.sp, shadow = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
          }
          TripBar(
              theme = theme,
              uiState = uiState,
              following = mapState.isTrackingUser,
              onEnd = viewModel::stopNavigation,
              onTheme = AppModule.themeStore::cycle,
              onMute = viewModel::toggleMute,
              onOverview = toggleOverview,
          )
        }
      } else {
        Planner(
            theme = theme,
            planner = planner,
            testDrive = testDrive,
            onQuery = viewModel::onQueryChange,
            onChoose = viewModel::choose,
            onClear = viewModel::clearDestination,
            onGo = viewModel::go,
            onTestDrive = viewModel::setTestDrive,
            onTheme = AppModule.themeStore::select,
            onLocate = { scope.launch { mapState.recenter(isNavigating = false) } },
        )
      }
    }

    // Arrival: GTA's "mission passed" banner for Metro Crime, a plain card elsewhere.
    GtaBigMessage(
        visible = gta && arrivedAt != null,
        title = "ARRIVED",
        subtitle = arrivedAt.orEmpty(),
        modifier = Modifier.align(Alignment.Center),
    )
    if (!gta) {
      arrivedAt?.let {
        Text(
            "Arrived at $it",
            color = theme.hudFg,
            style = theme.hudText(24.sp),
            modifier = Modifier.align(Alignment.Center).hudCard(theme).padding(horizontal = 20.dp, vertical = 14.dp),
        )
      }
    }
  }
}

@Composable
private fun Planner(
    theme: OverworldTheme,
    planner: PlannerState,
    testDrive: Boolean,
    onQuery: (String) -> Unit,
    onChoose: (Place) -> Unit,
    onClear: () -> Unit,
    onGo: () -> Unit,
    onTestDrive: (Boolean) -> Unit,
    onTheme: (String) -> Unit,
    onLocate: () -> Unit,
) {
  val focus = LocalFocusManager.current
  Box(Modifier.fillMaxSize()) {
    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
      Row(
          Modifier.fillMaxWidth().hudCard(theme).padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        BasicTextField(
            value = planner.query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = uiText(18.sp).copy(color = theme.hudFg),
            cursorBrush = SolidColor(theme.hudAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
              if (planner.query.isEmpty()) {
                Text("Where to?", color = theme.hudSub, style = uiText(18.sp))
              }
              inner()
            },
        )
        if (planner.searching) Text("…", color = theme.hudSub, style = uiText(18.sp))
      }
      if (planner.results.isNotEmpty()) {
        LazyColumn(
            Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = 340.dp).hudCard(theme)
        ) {
          items(planner.results) { place ->
            Column(
                Modifier.fillMaxWidth()
                    .clickable {
                      focus.clearFocus()
                      onChoose(place)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
              Text(place.name, color = theme.hudFg, style = uiText(17.sp, FontWeight.Medium), maxLines = 1)
              if (place.detail.isNotBlank()) {
                Text(
                    place.detail,
                    color = theme.hudSub,
                    style = uiText(14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }

    // While typing a search, the results own the screen.
    val searching = planner.destination == null && planner.query.isNotBlank()
    if (!searching) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
      Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.End) {
        HudButton(theme, "Locate", onLocate, Modifier.width(96.dp))
      }
      Column(Modifier.fillMaxWidth().hudCard(theme).padding(16.dp)) {
        planner.destination?.let { d ->
          Text(d.name, color = theme.hudFg, style = theme.hudText(26.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
          if (d.detail.isNotBlank()) {
            Text(d.detail, color = theme.hudSub, style = uiText(14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
          }
          planner.error?.let { Text(it, color = theme.hudAccent, style = uiText(14.sp), modifier = Modifier.padding(top = 6.dp)) }
          Row(
              Modifier.fillMaxWidth().padding(top = 12.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            HudButton(theme, "Cancel", onClear, Modifier.weight(1f))
            HudButton(theme, if (planner.routing) "Routing" else "Go", onGo, Modifier.weight(2f), strong = true)
          }
          Spacer(Modifier.size(14.dp))
        }
        if (planner.destination == null) {
          Text(
              "Search above, or long-press the map to drop a pin.",
              color = theme.hudSub,
              style = uiText(14.sp),
              modifier = Modifier.padding(bottom = 12.dp),
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("Test drive", color = theme.hudFg, style = uiText(16.sp, FontWeight.Medium))
            Text("Simulates the trip instead of using GPS", color = theme.hudSub, style = uiText(13.sp))
          }
          Switch(
              checked = testDrive,
              onCheckedChange = onTestDrive,
              colors =
                  SwitchDefaults.colors(
                      checkedTrackColor = theme.hudAccent,
                      checkedThumbColor = contrastOn(theme.hudAccent),
                      uncheckedTrackColor = theme.hudFg.copy(alpha = 0.12f),
                      uncheckedThumbColor = theme.hudSub,
                      uncheckedBorderColor = theme.hudBorder,
                  ),
          )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          THEMES.forEach { t -> ThemeChip(t, selected = t.id == theme.id, current = theme, onClick = { onTheme(t.id) }, modifier = Modifier.weight(1f)) }
        }
      }
    }
  }
}

@Composable
private fun ThemeChip(
    t: OverworldTheme,
    selected: Boolean,
    current: OverworldTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(12.dp)
  Column(
      modifier
          .background(if (selected) current.hudFg.copy(alpha = 0.1f) else current.hudFg.copy(alpha = 0.03f), shape)
          .border(if (selected) 2.dp else 1.dp, if (selected) current.hudFg.copy(alpha = 0.7f) else current.hudBorder, shape)
          .clickable(onClick = onClick)
          .padding(10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(Modifier.size(width = 48.dp, height = 22.dp)) {
      Box(Modifier.weight(1f).fillMaxSize().background(t.land, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)))
      Box(Modifier.weight(1f).fillMaxSize().background(t.hudFg))
      Box(Modifier.weight(1f).fillMaxSize().background(t.routeLine, RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)))
    }
    Text(
        t.name,
        color = current.hudFg,
        style = uiText(13.sp, FontWeight.Medium),
        maxLines = 1,
        modifier = Modifier.padding(top = 6.dp),
    )
  }
}
