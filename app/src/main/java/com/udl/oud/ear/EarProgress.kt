package com.udl.oud.ear

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How an exercise at a level has gone: all answers, the right ones, and the latest few in order. */
data class EarStats(val answered: Int = 0, val right: Int = 0, val recent: List<Boolean> = emptyList()) {
    /** Share right among the latest answers, or null before any. */
    val recentShare: Double? get() = if (recent.isEmpty()) null else recent.count { it }.toDouble() / recent.size

    /** Ready for the next level: most of the last ten right. */
    val readyForMore: Boolean get() = recent.size >= READY_AFTER && recent.takeLast(READY_AFTER).count { it } >= READY_RIGHT

    fun record(isRight: Boolean) = copy(
        answered = answered + 1,
        right = right + if (isRight) 1 else 0,
        recent = (recent + isRight).takeLast(KEEP),
    )

    companion object {
        const val KEEP = 20
        const val READY_AFTER = 10
        const val READY_RIGHT = 8
    }
}

/** Ear-training progress, kept between launches. Call [init] once at start-up. */
object EarProgress {
    private const val PREFS = "ear_progress"
    private var prefs: SharedPreferences? = null
    private val _stats = MutableStateFlow<Map<Pair<EarExercise, EarLevel>, EarStats>>(emptyMap())
    val stats: StateFlow<Map<Pair<EarExercise, EarLevel>, EarStats>> = _stats.asStateFlow()

    private fun key(e: EarExercise, l: EarLevel) = "${e.name}_${l.name}"

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _stats.value = EarExercise.entries.flatMap { e -> EarLevel.entries.map { l -> e to l } }.associateWith { (e, l) ->
            val k = key(e, l)
            EarStats(
                answered = p.getInt("${k}_answered", 0),
                right = p.getInt("${k}_right", 0),
                recent = p.getString("${k}_recent", "").orEmpty().map { it == '1' },
            )
        }
    }

    fun of(e: EarExercise, l: EarLevel): EarStats = _stats.value[e to l] ?: EarStats()

    fun record(e: EarExercise, l: EarLevel, isRight: Boolean) {
        val next = of(e, l).record(isRight)
        _stats.value = _stats.value + ((e to l) to next)
        val k = key(e, l)
        prefs?.edit()
            ?.putInt("${k}_answered", next.answered)
            ?.putInt("${k}_right", next.right)
            ?.putString("${k}_recent", next.recent.joinToString("") { if (it) "1" else "0" })
            ?.apply()
    }
}
