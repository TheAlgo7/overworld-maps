package com.thealgothrim.overworld

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The launcher icons, one per world from Gaurav's refined set (12 Nov 2026), and GTA VI's second
 * one with Macca the Gator inside the O. Each is an activity-alias of MainActivity in the manifest
 * with the launcher category; exactly one is enabled, and that is the icon the phone shows.
 */
enum class AppIcon(val id: String, val alias: String, val label: String, val world: String, val preview: Int) {
  GTA5("gta5", "IconGta5", "GTA V", "gta5", R.drawable.app_icon_gta5),
  RDR2("rdr2", "IconRdr2", "Red Dead", "rdr2", R.drawable.app_icon_rdr2),
  GTA6("gta6", "IconGta6", "GTA VI", "gta6", R.drawable.app_icon_gta6),
  GTA6_MACCA("gta6_macca", "IconGta6Macca", "Macca", "gta6", R.drawable.app_icon_gta6_macca);

  companion object {
    fun byId(id: String?): AppIcon? = entries.firstOrNull { it.id == id }

    /**
     * Enabled in the manifest: GTA V's, the world a new install opens on, so a first install
     * doesn't change icon the first time it's left (Play shows its own GTA VI icon).
     */
    val MANIFEST_DEFAULT = GTA5
  }
}

/** What Settings > Display > App icon holds. */
data class IconChoice(
    /** On: the icon is the phone's world's (his ask, 13 Nov). Off: [manual] stays. */
    val followsWorld: Boolean,
    /** The icon picked by hand, kept while [followsWorld] is off. */
    val manual: AppIcon,
    /** GTA VI's icon when it follows the world: plain, or the one with Macca. */
    val vi: AppIcon,
) {
  /** The icon for the phone's [world] now. */
  fun forWorld(world: String): AppIcon =
      if (!followsWorld) manual else when (world) {
        "rdr2" -> AppIcon.RDR2
        "gta6" -> vi
        else -> AppIcon.GTA5
      }
}

/**
 * Picks the launcher icon and switches it. Switching enables one alias and disables the rest; it
 * waits until the app is out of view and no trip is on (see [apply]), because Android closes the
 * screen a disabled alias opened, and some home screens drop that alias's shortcut.
 */
class AppIcons(context: Context) {
  private val app = context.applicationContext
  private val prefs = app.getSharedPreferences("overworld", Context.MODE_PRIVATE)

  private val _choice =
      MutableStateFlow(
          IconChoice(
              followsWorld = prefs.getBoolean(KEY_FOLLOW, true),
              manual = AppIcon.byId(prefs.getString(KEY_MANUAL, null)) ?: AppIcon.MANIFEST_DEFAULT,
              vi = AppIcon.byId(prefs.getString(KEY_VI, null))?.takeIf { it.world == "gta6" } ?: AppIcon.GTA6,
          )
      )
  val choice: StateFlow<IconChoice> = _choice.asStateFlow()

  /**
   * A tile tapped in Settings while the phone shows [world]. GTA VI's two icons both count as GTA
   * VI's, so either keeps the icon following the world; any other world's icon is a pick by hand.
   */
  fun pick(icon: AppIcon, world: String) {
    val c = _choice.value
    val vi = if (icon.world == "gta6") icon else c.vi
    save(if (c.followsWorld && icon.world == world) c.copy(vi = vi) else c.copy(followsWorld = false, manual = icon, vi = vi))
  }

  /** The follow switch. Off keeps the icon the world has now. */
  fun setFollowsWorld(on: Boolean, world: String) {
    val c = _choice.value
    save(if (on) c.copy(followsWorld = true) else c.copy(followsWorld = false, manual = c.forWorld(world)))
  }

  private fun save(c: IconChoice) {
    _choice.value = c
    prefs.edit {
      putBoolean(KEY_FOLLOW, c.followsWorld)
      putString(KEY_MANUAL, c.manual.id)
      putString(KEY_VI, c.vi.id)
    }
  }

  /** The icon the launcher shows now. */
  fun inUse(): AppIcon = AppIcon.entries.firstOrNull { enabled(it) } ?: AppIcon.MANIFEST_DEFAULT

  /**
   * Makes the launcher show the icon for [world]. Only when it differs, so this is a few cheap
   * look-ups on every call. Called as the phone's screen leaves view, with no trip running: Android
   * removes the task a disabled alias started, and mid-drive that would close the map.
   */
  fun apply(world: String) {
    val want = _choice.value.forWorld(world)
    if (enabled(want) && AppIcon.entries.none { it != want && enabled(it) }) return
    val pm = app.packageManager
    // The new one first, so there is never a moment with no launcher icon at all.
    runCatching { pm.setComponentEnabledSetting(component(want), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP) }
    AppIcon.entries.filter { it != want }.forEach { other ->
      runCatching { pm.setComponentEnabledSetting(component(other), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP) }
    }
  }

  private fun enabled(icon: AppIcon): Boolean =
      when (app.packageManager.getComponentEnabledSetting(component(icon))) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.MANIFEST_DEFAULT
        else -> false
      }

  // The aliases are named in the code's package; the debug build's application id adds ".debug".
  private fun component(icon: AppIcon) = ComponentName(app.packageName, "com.thealgothrim.overworld.${icon.alias}")

  private companion object {
    const val KEY_FOLLOW = "icon_follows_world"
    const val KEY_MANUAL = "app_icon"
    const val KEY_VI = "app_icon_vi"
  }
}
