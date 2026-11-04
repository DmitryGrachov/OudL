@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.udl.oud.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udl.oud.fretboard.Cadences
import com.udl.oud.fretboard.FretboardUiState
import com.udl.oud.fretboard.FretboardViewModel
import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.Sayrs
import com.udl.oud.fretboard.Spelling
import com.udl.oud.fretboard.Scale
import com.udl.oud.fretboard.ScaleGroup
import com.udl.oud.fretboard.WesternScale
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Pitch
import com.udl.oud.fretboard.ScaleNote
import com.udl.oud.fretboard.intervalLabel
import com.udl.oud.tuner.Note


/** Scale picker, tonic, direction, and the scale written out with its intervals. */
@Composable
internal fun ScaleBar(state: FretboardUiState, vm: FretboardViewModel, groups: List<ScaleGroup>) {
    Column(Modifier.fillMaxWidth()) {
        // With several groups (maqam families) pick the family first, then a scale within it.
        val group = groups.firstOrNull { state.selected in it.scales } ?: groups.first()
        if (groups.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Семья", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
                groups.forEach { g ->
                    FilterChip(
                        g == group,
                        onClick = { if (g != group) vm.select(g.scales.first()) },
                        label = { Text(g.name) },
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (groups.size > 1) {
                Text("Макам", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
            }
            group.scales.forEach { choice ->
                FilterChip(
                    state.selected == choice,
                    onClick = { vm.select(choice) },
                    label = { Text(choice.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        val selected = state.selected ?: return@Column
        val notes = state.selectedNotes ?: return@Column
        val scale = state.degrees
        fun name(note: ScaleNote) = state.labelFor(Pitch(state.tonic + note.offset), scale)
        val lowerColor = MaterialTheme.colorScheme.primary
        val upperColor = upperJinsColor()
        val intervalColor = MaterialTheme.colorScheme.onSurfaceVariant

        // Tonic to octave, written in the direction it is played.
        val upNames = notes.map { name(it) to if (it.upper) upperColor else lowerColor } + (name(notes[0]) to lowerColor)
        val upSteps = notes.map { state.tonic + it.offset } + (state.tonic + 24)
        val upIntervals = selected.intervals(state.descending)
        val names = if (state.descending) upNames.reversed() else upNames
        val steps = if (state.descending) upSteps.reversed() else upSteps
        val intervals = if (state.descending) upIntervals.reversed() else upIntervals
        val playingColor = MaterialTheme.colorScheme.onPrimary
        val playingBackground = MaterialTheme.colorScheme.onSurface

        FlowRow(verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Тоника", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { vm.shiftTonic(-1) }) { Text("−", fontSize = 20.sp) }
                Text(
                    name(notes[0]) + Note(state.tonic / 2).octave,
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { vm.shiftTonic(1) }) { Text("+", fontSize = 20.sp) }
                FilterChip(!state.descending, onClick = { vm.setDescending(false) }, label = { Text("↑ вверх") })
                Spacer(Modifier.width(8.dp))
                FilterChip(state.descending, onClick = { vm.setDescending(true) }, label = { Text("↓ вниз") })
                TextButton(onClick = vm::playScale) { Text("▶ Сыграть") }
                TextButton(onClick = vm::startExercise) { Text("🎯 Упражнение") }
                if (selected is Maqam) {
                    FilterChip(state.sayrOpen, onClick = vm::toggleSayr, label = { Text("Сайр") })
                }
            }
            Text(
                buildAnnotatedString {
                    names.forEachIndexed { i, (noteName, color) ->
                        // The note sounding right now is marked here as well as on the board.
                        val style = if (steps[i] == state.playingStep) {
                            SpanStyle(color = playingColor, background = playingBackground, fontWeight = FontWeight.Bold)
                        } else {
                            SpanStyle(color = color, fontWeight = FontWeight.SemiBold)
                        }
                        withStyle(style) { append(" $noteName ") }
                        if (i < intervals.size) {
                            withStyle(SpanStyle(color = intervalColor)) { append("  ${intervalLabel(intervals[i])}  ") }
                        }
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterVertically).padding(start = 16.dp),
            )
        }
    }
}

/**
 * How the shown scale is built: for a maqam, lower jins on the tonic + upper jins on the
 * ghammaz; for a European scale, what sets it apart.
 */
@Composable
internal fun ScaleSummary(state: FretboardUiState) {
    val selected = state.selected ?: return
    val sameWayDown = if (state.descending && !selected.hasDescent) "  · вниз так же, как вверх" else ""
    val hint = "    ◎ тоника · играй — место подсветится"
    if (selected is WesternScale) {
        Text(
            selected.summary + sameWayDown + hint,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val maqam = selected as Maqam
    val notes = state.selectedNotes ?: return
    val scale = state.degrees
    fun name(note: ScaleNote) = state.labelFor(Pitch(state.tonic + note.offset), scale)
    val lowerColor = MaterialTheme.colorScheme.primary
    val upperColor = upperJinsColor()
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = lowerColor)) { append("${maqam.lower.name} на ${name(notes[0])}") }
            append("  +  ")
            withStyle(SpanStyle(color = upperColor)) {
                append("${maqam.upperJins(state.descending).name} на ${name(notes[maqam.ghammaz])}")
            }
            append(sameWayDown)
            append(hint)
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * How the selected maqam unfolds: its pillars (also ringed on the board), the usual path, and
 * the usual modulations, each a button that moves the board to that maqam on that note.
 */
@Composable
internal fun SayrPanel(state: FretboardUiState, vm: FretboardViewModel, maqam: Maqam) {
    val sayr = Sayrs.of(maqam)
    val notes = state.selectedNotes ?: return
    val degrees = state.degrees
    fun name(degree: Int) = state.labelFor(Pitch(state.tonic + notes[degree].offset), degrees)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val start = sayr?.start?.takeIf { it != 0 && it != maqam.ghammaz }

    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            sayr?.path?.mapIndexed { i, step -> "${i + 1}. $step" }?.joinToString("   →   ")
                ?: "Путь развития для этого макама пока не записан — опоры взяты из его строения.",
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        // Pillars and moves share one row to leave the board its height.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)) {
                        append("◎ тоника ${name(0)}")
                    }
                    withStyle(SpanStyle(color = upperJinsColor(), fontWeight = FontWeight.SemiBold)) {
                        append("   ◎ гаммаз ${name(maqam.ghammaz)}")
                    }
                    if (start != null) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)) {
                            append("   ◎ центр ${name(start)}")
                        }
                    }
                    append("     ")
                },
                Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.titleSmall,
            )
            val trail = state.sayrTrail.lastOrNull()
            if (trail != null) {
                TextButton(onClick = vm::modulateBack) {
                    Text("← назад: ${trail.scale.name} на ${Spelling.of(trail.tonic).label(NoteLabels.Solfege)}")
                }
            }
            val moves = sayr?.modulations.orEmpty().mapNotNull { m ->
                Maqamat.all.firstOrNull { it.name == m.maqam }?.let { it to state.tonic + m.offset }
            }
            if (moves.isNotEmpty()) {
                Text("Переходы:", Modifier.align(Alignment.CenterVertically), color = muted)
                moves.forEach { (target, tonic) ->
                    AssistChip(
                        onClick = { vm.modulate(target, tonic) },
                        label = { Text("→ ${target.name} на ${Spelling.of(tonic).label(NoteLabels.Solfege)}") },
                    )
                }
            }
        }
        val cadences = Cadences.of(maqam)
        if (cadences.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Кадансы:", Modifier.align(Alignment.CenterVertically).padding(end = 4.dp), color = muted)
                cadences.forEach { cadence ->
                    val phrase = cadence.steps(maqam, state.tonic).joinToString(" ") { state.labelFor(Pitch(it), degrees) }
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(cadence.name) }
                            withStyle(SpanStyle(color = muted)) { append("  $phrase") }
                        },
                        Modifier.align(Alignment.CenterVertically).padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { vm.playCadence(cadence) }) { Text("▶") }
                    TextButton(onClick = { vm.startCadenceDrill(cadence) }) { Text("🎯") }
                }
            }
        }
    }
}

