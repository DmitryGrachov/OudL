package com.udl.oud.tuner

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Instrument settings shared by every screen and kept between launches: which oud, its tuning
 * and the A4 reference. Screens read the flows; changes go through the setters, which save.
 */
object OudSettings {
    private const val PREFS = "oud_settings"
    private const val KEY_OUD = "oud"
    private const val KEY_TUNING = "tuning"
    private const val KEY_A4 = "a4"
    private const val DEFAULT_A4 = 440

    private var prefs: SharedPreferences? = null

    private val _oud = MutableStateFlow(OudType.Arabic)
    private val _tuning = MutableStateFlow(Tunings.arabic.first())
    private val _a4 = MutableStateFlow(DEFAULT_A4)

    val oud: StateFlow<OudType> = _oud.asStateFlow()
    val tuning: StateFlow<Tuning> = _tuning.asStateFlow()
    val a4: StateFlow<Int> = _a4.asStateFlow()

    /** Loads what was saved last time; call once at start-up, before any screen reads. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val oud = p.getString(KEY_OUD, null)?.let { name -> OudType.entries.firstOrNull { it.name == name } }
            ?: OudType.Arabic
        _oud.value = oud
        val tunings = Tunings.forOud(oud)
        _tuning.value = tunings.firstOrNull { it.name == p.getString(KEY_TUNING, null) } ?: tunings.first()
        _a4.value = p.getInt(KEY_A4, DEFAULT_A4)
    }

    /** Switching the oud also switches to its first tuning. */
    fun setOud(oud: OudType) {
        if (oud == _oud.value) return
        _oud.value = oud
        _tuning.value = Tunings.forOud(oud).first()
        save()
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
            ?.putString(KEY_OUD, _oud.value.name)
            ?.putString(KEY_TUNING, _tuning.value.name)
            ?.putInt(KEY_A4, _a4.value)
            ?.apply()
    }
}
