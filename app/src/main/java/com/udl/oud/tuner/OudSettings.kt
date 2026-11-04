package com.udl.oud.tuner

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Instrument settings shared by every screen and kept between launches: the tuning and the A4
 * reference. Screens read the flows; changes go through the setters, which save.
 */
object OudSettings {
    private const val PREFS = "oud_settings"
    private const val KEY_TUNING = "tuning"
    private const val KEY_A4 = "a4"
    private const val DEFAULT_A4 = 440

    private var prefs: SharedPreferences? = null

    private val _tuning = MutableStateFlow(Tunings.arabic.first())
    private val _a4 = MutableStateFlow(DEFAULT_A4)

    val tuning: StateFlow<Tuning> = _tuning.asStateFlow()
    val a4: StateFlow<Int> = _a4.asStateFlow()

    /** Loads what was saved last time; call once at start-up, before any screen reads. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _tuning.value = Tunings.arabic.firstOrNull { it.name == p.getString(KEY_TUNING, null) } ?: Tunings.arabic.first()
        _a4.value = p.getInt(KEY_A4, DEFAULT_A4)
    }

    fun setTuning(tuning: Tuning) {
        _tuning.value = tuning
        save()
    }

    fun setA4(a4: Int) {
        _a4.value = a4
        save()
    }

    private fun save() {
        prefs?.edit()
            ?.putString(KEY_TUNING, _tuning.value.name)
            ?.putInt(KEY_A4, _a4.value)
            ?.apply()
    }
}
