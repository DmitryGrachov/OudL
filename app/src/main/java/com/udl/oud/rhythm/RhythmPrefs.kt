package com.udl.oud.rhythm

import android.content.Context
import android.content.SharedPreferences

/** The measured latencies of tapping on the screen and of playing into the microphone, kept between launches. */
object RhythmPrefs {
    private const val PREFS = "rhythm"
    private const val KEY_TAP = "tap_latency"
    private const val KEY_MIC = "mic_latency"
    private var prefs: SharedPreferences? = null

    var tapLatency: Double = 0.0
        private set
    var micLatency: Double = 0.0
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        tapLatency = p.getFloat(KEY_TAP, 0f).toDouble()
        micLatency = p.getFloat(KEY_MIC, 0f).toDouble()
    }

    fun setTap(ms: Double) {
        tapLatency = ms
        prefs?.edit()?.putFloat(KEY_TAP, ms.toFloat())?.apply()
    }

    fun setMic(ms: Double) {
        micLatency = ms
        prefs?.edit()?.putFloat(KEY_MIC, ms.toFloat())?.apply()
    }
}
