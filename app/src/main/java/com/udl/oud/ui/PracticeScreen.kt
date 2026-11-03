@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.udl.oud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udl.oud.audio.Iqaat
import com.udl.oud.audio.Stroke
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Pitch
import com.udl.oud.practice.PracticeUiState
import com.udl.oud.practice.PracticeViewModel
import com.udl.oud.tuner.Note
import com.udl.oud.tuner.OudSettings
import kotlin.math.roundToInt

@Composable
fun PracticeRoute(vm: PracticeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Leaving the tab or the app silences both loops.
    LifecycleResumeEffect(Unit) { onPauseOrDispose { vm.stopAll() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(topBar = { if (!wide) TopAppBar(title = { Text("Дрон и ритм") }) }) { padding ->
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    DroneCard(state, vm, Modifier.weight(1f))
                    RhythmCard(state, vm, Modifier.weight(1f))
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DroneCard(state, vm, Modifier.fillMaxWidth())
                    RhythmCard(state, vm, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DroneCard(state: PracticeUiState, vm: PracticeViewModel, modifier: Modifier) {
    val pitch = Pitch(state.droneStep)
    Card(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Дрон", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Тянущаяся тоника: играй гамму или таксим поверх неё — фальшь сразу слышна.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pitch.label(NoteLabels.Solfege) + Note(state.droneStep / 2).octave,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        listOfNotNull(pitch.arabicName, "%.1f Гц".format(pitch.frequency(state.a4.toDouble())))
                            .joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PlayButton(state.droneOn, vm::toggleDrone)
            }
            // Steps in tones, as the maqam intervals are written: ½ = semitone, ¼ = quarter tone.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-2 to "−½", -1 to "−¼", 1 to "+¼", 2 to "+½").forEach { (quarters, label) ->
                    FilledTonalButton(onClick = { vm.shiftDrone(quarters) }) { Text(label, fontSize = 18.sp) }
                }
            }
            Text("Быстро: открытые струны", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OudSettings.tuning.value.courses.forEach { note ->
                    val step = Pitch.of(note).step
                    FilterChip(
                        state.droneStep == step,
                        onClick = { vm.setDroneStep(step) },
                        label = { Text(note.solfege + note.octave) },
                    )
                }
            }
            FilterChip(state.droneFifth, onClick = vm::toggleFifth, label = { Text("+ квинта") })
            VolumeRow(state.droneVolume, vm::setDroneVolume)
        }
    }
}

@Composable
private fun RhythmCard(state: PracticeUiState, vm: PracticeViewModel, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Ритм (ика')", style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Iqaat.all.forEach { iqa ->
                    FilterChip(
                        state.iqa == iqa,
                        onClick = { vm.selectIqa(iqa) },
                        label = { Text("${iqa.name} ${iqa.meter}") },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${state.bpm}", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Text("ударов в минуту", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PlayButton(state.rhythmOn, vm::toggleRhythm)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-10, -1, 1, 10).forEach { delta ->
                    FilledTonalButton(onClick = { vm.changeBpm(delta) }) {
                        Text(if (delta > 0) "+$delta" else "−${-delta}", fontSize = 18.sp)
                    }
                }
            }
            // Dragging only shows the number; the loop is rebuilt once, when the finger lifts.
            var dragged by remember(state.bpm) { mutableFloatStateOf(state.bpm.toFloat()) }
            Slider(
                value = dragged,
                onValueChange = { dragged = it },
                onValueChangeFinished = { vm.setBpm(dragged.roundToInt()) },
                valueRange = 40f..240f,
            )
            Strokes(state)
            Text(
                "D — дум (низкий, в центр), T — так (звонкий, по краю)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VolumeRow(state.rhythmVolume, vm::setRhythmVolume)
        }
    }
}

/** The cycle as a row of circles, the one sounding now filled in. */
@Composable
private fun Strokes(state: PracticeUiState) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.iqa.strokes.forEachIndexed { i, stroke ->
            val now = i == state.beatStep
            val onBeat = i % state.iqa.stepsPerBeat == 0
            val color = when (stroke) {
                Stroke.Dum -> MaterialTheme.colorScheme.primary
                Stroke.Tak -> MaterialTheme.colorScheme.tertiary
                Stroke.Rest -> MaterialTheme.colorScheme.outline
            }
            Box(
                Modifier
                    .size(if (stroke == Stroke.Rest) 36.dp else 44.dp)
                    .align(Alignment.CenterVertically)
                    .then(if (now) Modifier.background(color, CircleShape) else Modifier)
                    .border(if (onBeat) 3.dp else 1.5.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (stroke) {
                        Stroke.Dum -> "D"
                        Stroke.Tak -> "T"
                        Stroke.Rest -> "·"
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (now) MaterialTheme.colorScheme.surface else color,
                )
            }
        }
    }
}

@Composable
private fun PlayButton(on: Boolean, onClick: () -> Unit) {
    if (on) {
        Button(onClick = onClick) { Text("■ Стоп", fontSize = 18.sp) }
    } else {
        FilledTonalButton(onClick = onClick) { Text("▶ Играть", fontSize = 18.sp) }
    }
}

@Composable
private fun VolumeRow(volume: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Громкость", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(96.dp))
        Slider(value = volume, onValueChange = onChange, modifier = Modifier.weight(1f))
    }
}
