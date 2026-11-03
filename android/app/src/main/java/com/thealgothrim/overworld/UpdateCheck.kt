package com.thealgothrim.overworld

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** A newer Overworld than this one: [label] to show ("0.5.9", or "New version" where Play doesn't name it), and where to get it. */
data class UpdateInfo(val label: String, val url: String)

/**
 * Tells people on an older copy that a new one is out (a dot on Settings, a row inside it). A copy
 * from Google Play asks Play itself (its testing tracks included), so it never points at a version
 * Play doesn't have yet; any other copy (the APK on GitHub) compares with the latest GitHub release.
 * Checked at most every [EVERY_MS], quietly: no answer means no dot.
 */
class UpdateCheck(private val context: Context, private val http: OkHttpClient) {
  private val _available = MutableStateFlow<UpdateInfo?>(null)
  val available: StateFlow<UpdateInfo?> = _available.asStateFlow()
  private var checkedAt = 0L

  suspend fun check() {
    val now = System.currentTimeMillis()
    if (now - checkedAt < EVERY_MS) return
    checkedAt = now
    _available.value = runCatching { if (fromPlay()) fromPlayStore() else fromGitHub() }.onFailure { Log.i(TAG, "update check failed: $it") }.getOrNull()
  }

  /** Opens the store page or release for [info]. */
  fun open(info: UpdateInfo) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  private fun fromPlay(): Boolean {
    val installer =
        runCatching {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
              else @Suppress("DEPRECATION") context.packageManager.getInstallerPackageName(context.packageName)
            }
            .getOrNull()
    return installer == PLAY
  }

  private suspend fun fromPlayStore(): UpdateInfo? {
    val info =
        suspendCancellableCoroutine { done ->
          AppUpdateManagerFactory.create(context).appUpdateInfo.addOnSuccessListener { done.resume(it) }.addOnFailureListener { done.resumeWithException(it) }
        }
    if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return null
    return UpdateInfo("New version", "https://play.google.com/store/apps/details?id=${context.packageName}")
  }

  private suspend fun fromGitHub(): UpdateInfo? =
      withContext(Dispatchers.IO) {
        val request = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
        http.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@withContext null
          val json = JSONObject(response.body.string())
          val latest = json.optString("tag_name").removePrefix("v")
          if (newer(latest, BuildConfig.VERSION_NAME)) UpdateInfo(latest, json.optString("html_url", RELEASES)) else null
        }
      }

  companion object {
    private const val TAG = "UpdateCheck"
    private const val PLAY = "com.android.vending"
    private const val LATEST = "https://api.github.com/repos/TheAlgo7/overworld-maps/releases/latest"
    private const val RELEASES = "https://github.com/TheAlgo7/overworld-maps/releases/latest"
    private const val EVERY_MS = 6 * 60 * 60 * 1000L

    /** Whether version [a] ("0.5.9") is later than [b] ("0.5.8"), part by part. */
    fun newer(a: String, b: String): Boolean {
      val x = a.split('.').map { it.toIntOrNull() ?: return false }
      val y = b.split('.').map { it.toIntOrNull() ?: 0 }
      for (i in 0 until maxOf(x.size, y.size)) {
        val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
        if (d != 0) return d > 0
      }
      return false
    }
  }
}
