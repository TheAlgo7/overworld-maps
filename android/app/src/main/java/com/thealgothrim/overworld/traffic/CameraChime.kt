package com.thealgothrim.overworld.traffic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The camera warning: two quick rising notes, like Radarbot's. Played on the navigation-guidance
 * channel, so Android Auto treats it like a turn prompt (car speakers, music ducked for a moment)
 * and the phone's ringer mode doesn't silence it, which a notification beep would.
 */
object CameraChime {
  private const val RATE = 44_100
  private val pcm: ShortArray by lazy { notes(listOf(1046.5 to 110, 0.0 to 45, 1318.5 to 150)) }

  fun play(context: Context) {
    Log.i("CameraChime", "camera chime")
    val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    val audio = context.getSystemService(AudioManager::class.java) ?: return
    val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
    try {
      val track =
          AudioTrack.Builder()
              .setAudioAttributes(attributes)
              .setAudioFormat(
                  AudioFormat.Builder()
                      .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                      .setSampleRate(RATE)
                      .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                      .build()
              )
              .setBufferSizeInBytes(pcm.size * 2)
              .setTransferMode(AudioTrack.MODE_STATIC)
              .build()
      track.write(pcm, 0, pcm.size)
      audio.requestAudioFocus(focus)
      track.play()
      Handler(Looper.getMainLooper()).postDelayed(
          {
            track.release()
            audio.abandonAudioFocusRequest(focus)
          },
          pcm.size * 1000L / RATE + 250,
      )
    } catch (e: Exception) {
      Log.w("CameraChime", "could not play the camera chime", e)
      audio.abandonAudioFocusRequest(focus)
    }
  }

  /** Sine notes (Hz, ms; 0 Hz is a pause), each faded in and out over 8 ms so they don't click. */
  private fun notes(spec: List<Pair<Double, Int>>): ShortArray {
    val out = ArrayList<Short>()
    for ((hz, ms) in spec) {
      val n = RATE * ms / 1000
      val fade = RATE * 8 / 1000
      for (i in 0 until n) {
        val envelope = min(1.0, min(i, n - 1 - i).toDouble() / fade)
        val v = if (hz == 0.0) 0.0 else sin(2 * PI * hz * i / RATE) * 0.55 * envelope
        out += (v * Short.MAX_VALUE).toInt().toShort()
      }
    }
    return out.toShortArray()
  }
}
