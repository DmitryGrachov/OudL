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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import com.udl.oud.practice.MetronomeUiState
import com.udl.oud.practice.MetronomeViewModel
import kotlin.math.roundToInt

@Composable
fun MetronomeRoute(vm: MetronomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Leaving the tab or the app stops the clicks.
    LifecycleResumeEffect(Unit) { onPauseOrDispose { vm.stop() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(topBar = { if (!wide) TopAppBar(title = { Text("Метроном") }) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Beats(state, Modifier.padding(top = 8.dp))
                Tempo(state, vm)
                Settings(state, vm)
            }
        }
    }
}

/** One big circle per beat; the sounding one fills, the first is ringed thicker. */
@Composable
private fun Beats(state: MetronomeUiState, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(state.beats) { beat ->
            val now = beat == state.beat
            val color = if (beat == 0 && state.accent) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            }
            Box(
                Modifier
                    .size(if (beat == 0) 72.dp else 60.dp)
                    .align(Alignment.CenterVertically)
                    .then(if (now) Modifier.background(color, CircleShape) else Modifier)
                    .border(if (beat == 0) 4.dp else 2.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${beat + 1}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (now) MaterialTheme.colorScheme.surface else color,
                )
            }
        }
    }
}

@Composable
private fun Tempo(state: MetronomeUiState, vm: MetronomeViewModel) {
    Column(Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${state.bpm}", fontSize = 96.sp, fontWeight = FontWeight.Bold)
        Text("ударов в минуту · ${tempoName(state.bpm)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(-10, -1, 1, 10).forEach { delta ->
                FilledTonalButton(onClick = { vm.changeBpm(delta) }) {
                    Text(if (delta > 0) "+$delta" else "−${-delta}", fontSize = 20.sp)
                }
            }
        }
        // Dragging only shows the number; the loop is rebuilt once, when the finger lifts.
        var dragged by remember(state.bpm) { mutableFloatStateOf(state.bpm.toFloat()) }
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { vm.setBpm(dragged.roundToInt()) },
            valueRange = MetronomeViewModel.BPM_MIN.toFloat()..MetronomeViewModel.BPM_MAX.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.on) {
                Button(onClick = vm::toggle, modifier = Modifier.height(64.dp).width(200.dp)) {
                    Text("■ Стоп", fontSize = 22.sp)
                }
            } else {
                FilledTonalButton(onClick = vm::toggle, modifier = Modifier.height(64.dp).width(200.dp)) {
                    Text("▶ Старт", fontSize = 22.sp)
                }
            }
            OutlinedButton(onClick = vm::tap, modifier = Modifier.height(64.dp).width(160.dp)) {
                Text("Тап", fontSize = 22.sp)
            }
        }
        Text(
            "«Тап» — постучи в нужном темпе несколько раз",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Settings(state: MetronomeUiState, vm: MetronomeViewModel) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Долей в такте", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..7).forEach { beats ->
                FilterChip(state.beats == beats, onClick = { vm.setBeats(beats) }, label = { Text("$beats") })
            }
        }
        Text("Деление доли", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "♩ четверти", 2 to "♪♪ восьмые", 3 to "триоли", 4 to "шестнадцатые").forEach { (n, label) ->
                FilterChip(state.subdivision == n, onClick = { vm.setSubdivision(n) }, label = { Text(label) })
            }
        }
        FilterChip(state.accent, onClick = vm::toggleAccent, label = { Text("Акцент на первую долю") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Громкость", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(96.dp))
            Slider(value = state.volume, onValueChange = vm::setVolume, modifier = Modifier.weight(1f))
        }
    }
}

/** Usual Italian tempo marking for a tempo, as a rough guide. */
private fun tempoName(bpm: Int): String = when {
    bpm < 60 -> "Largo"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 168 -> "Allegro"
    else -> "Presto"
}
