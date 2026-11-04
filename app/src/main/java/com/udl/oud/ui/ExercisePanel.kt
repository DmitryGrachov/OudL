@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.udl.oud.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.udl.oud.fretboard.FretboardUiState
import com.udl.oud.fretboard.FretboardViewModel
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Pitch
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.abs
import kotlin.math.roundToInt


/**
 * The "play the scale" drill: which note is due and how the current one sounds, the notes
 * done so far with their offsets, and a summary at the end.
 */
@Composable
internal fun ExercisePanel(
    state: FretboardUiState,
    vm: FretboardViewModel,
    needsMic: Boolean,
    onRequestMic: () -> Unit,
) {
    val exercise = state.exercise ?: return
    val degrees = state.degrees
    fun name(step: Int) = state.labelFor(Pitch(step), degrees)
    val good = InTuneGreen
    val off = MaterialTheme.colorScheme.tertiary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun colorFor(cents: Double) = if (abs(cents) <= FretboardUiState.IN_TUNE_CENTS) good else off

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val target = exercise.target
                when {
                    needsMic -> Text(
                        "Для упражнения нужен микрофон — нажми, чтобы разрешить",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.pointerInput(Unit) { detectTapGestures { onRequestMic() } },
                    )
                    target == null -> {
                        val worst = exercise.worst
                        Text(
                            "Готово: ${exercise.inTuneCount} из ${exercise.steps.size} точно" +
                                " · средняя ошибка ${exercise.meanError.roundToInt()} ц" +
                                (worst?.let { " · хуже всего ${name(exercise.steps[it])} ${signedCents(exercise.results[it])}" } ?: ""),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    else -> {
                        Text(
                            "Нота ${exercise.results.size + 1} из ${exercise.steps.size}:  ",
                            style = MaterialTheme.typography.titleMedium,
                            color = muted,
                        )
                        val place = exercise.targetPlace
                        val where = place?.let {
                            val open = state.tuning.courses[it.course]
                            val course = Pitch.of(open).label(state.labels)
                            " на струне " + if (state.labels == NoteLabels.Arabic) course else "$course${open.octave}"
                        } ?: ""
                        Text(
                            "сыграй ${name(target)}$where",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        val cents = state.exerciseCents
                        val frequency = state.frequency
                        if (cents != null && frequency != null) {
                            val heardName = state.labelFor(
                                Pitch.nearest(frequency, state.a4.toDouble(), state.snapToQuarterTones),
                                degrees,
                            )
                            Text(
                                when {
                                    abs(cents) > 50 -> "   слышу $heardName — другая нота"
                                    abs(cents) <= FretboardUiState.IN_TUNE_CENTS -> "   ${signedCents(cents)} — держи"
                                    cents < 0 -> "   низко на ${abs(cents).roundToInt()} ц"
                                    else -> "   высоко на ${cents.roundToInt()} ц"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (abs(cents) > 50) muted else colorFor(cents),
                            )
                        }
                    }
                }
            }
            if (exercise.places != null) {
                FilterChip(state.drillHint, onClick = vm::toggleDrillHint, label = { Text("Подсказка") })
            }
            TextButton(onClick = vm::restartExercise) { Text(if (exercise.done) "Ещё раз" else "Заново") }
            TextButton(onClick = vm::stopExercise) { Text("Закончить") }
        }
        Text(
            buildAnnotatedString {
                exercise.steps.forEachIndexed { i, step ->
                    val result = exercise.results.getOrNull(i)
                    val style = when {
                        result != null -> SpanStyle(color = colorFor(result), fontWeight = FontWeight.SemiBold)
                        i == exercise.results.size -> SpanStyle(
                            color = MaterialTheme.colorScheme.onPrimary,
                            background = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        else -> SpanStyle(color = muted)
                    }
                    withStyle(style) {
                        append(" ${name(step)}")
                        if (result != null) append(" ${signedCents(result)}")
                        append(" ")
                    }
                    if (i < exercise.steps.lastIndex) withStyle(SpanStyle(color = muted)) { append(" · ") }
                }
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

