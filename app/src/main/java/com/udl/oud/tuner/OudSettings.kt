package com.udl.oud.tuner

import android.content.Context
import android.content.SharedPreferences
import com.udl.oud.fretboard.Intonation
import com.udl.oud.fretboard.IntonationSchool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the quarter tones sit: the school, the player's own height for [IntonationSchool.Custom], and functional shading. */
data class IntonationSetting(
    val school: IntonationSchool = IntonationSchool.Theory,
    val custom: Int = 0,
    val functional: Boolean = false,
)

/**
 * Instrument settings shared by every screen and kept between launches: the tuning, the A4
 * reference and the intonation of the quarter tones. Screens read the flows; changes go through
 * the setters, which save.
 */
object OudSettings {
    private const val PREFS = "oud_settings"
    private const val KEY_TUNING = "tuning"
    private const val KEY_A4 = "a4"
    private const val KEY_SCHOOL = "intonation_school"
    private const val KEY_CUSTOM = "intonation_custom"
    private const val KEY_FUNCTIONAL = "intonation_functional"
    private const val DEFAULT_A4 = 440

    private var prefs: SharedPreferences? = null

    private val _tuning = MutableStateFlow(Tunings.arabic.first())
    private val _a4 = MutableStateFlow(DEFAULT_A4)
    private val _intonation = MutableStateFlow(IntonationSetting())

    val tuning: StateFlow<Tuning> = _tuning.asStateFlow()
    val a4: StateFlow<Int> = _a4.asStateFlow()
    val intonation: StateFlow<IntonationSetting> = _intonation.asStateFlow()

    /** Loads what was saved last time; call once at start-up, before any screen reads. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _tuning.value = Tunings.arabic.firstOrNull { it.name == p.getString(KEY_TUNING, null) } ?: Tunings.arabic.first()
        _a4.value = p.getInt(KEY_A4, DEFAULT_A4)
        apply(
            IntonationSetting(
                school = IntonationSchool.entries.firstOrNull { it.name == p.getString(KEY_SCHOOL, null) } ?: IntonationSchool.Theory,
                custom = p.getInt(KEY_CUSTOM, 0),
                functional = p.getBoolean(KEY_FUNCTIONAL, false),
            ),
        )
    }

    fun setTuning(tuning: Tuning) {
        _tuning.value = tuning
        save()
    }

    fun setA4(a4: Int) {
        _a4.value = a4
        save()
    }

    fun setIntonation(setting: IntonationSetting) {
        apply(setting.copy(custom = setting.custom.coerceIn(-Intonation.CUSTOM_RANGE, Intonation.CUSTOM_RANGE)))
        save()
    }

    private fun apply(s: IntonationSetting) {
        Intonation.school = s.school
        Intonation.custom = s.custom
        Intonation.functional = s.functional
        _intonation.value = s
    }

    private fun save() {
        prefs?.edit()
            ?.putString(KEY_TUNING, _tuning.value.name)
            ?.putInt(KEY_A4, _a4.value)
            ?.putString(KEY_SCHOOL, _intonation.value.school.name)
            ?.putInt(KEY_CUSTOM, _intonation.value.custom)
            ?.putBoolean(KEY_FUNCTIONAL, _intonation.value.functional)
            ?.apply()
    }
}
