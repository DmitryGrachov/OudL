@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.udl.oud.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udl.oud.ear.EarExercise
import com.udl.oud.ear.EarLevel
import com.udl.oud.ear.EarStats
import com.udl.oud.ear.EarUiState
import com.udl.oud.ear.EarViewModel
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.roundToInt

/** The ear tab: hearing jins, maqam, intervals, quarter tones and resting notes without the oud. */
@Composable
fun EarRoute(vm: EarViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { onPauseOrDispose { vm.stop() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(topBar = { if (!wide) TopAppBar(title = { Text("Слух") }) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val exercise = state.exercise
                if (exercise == null) ExerciseList(state, vm) else Session(state, vm, exercise)
            }
        }
    }
}

private fun statLine(s: EarStats): String = when {
    s.answered == 0 -> "ещё не пробовал"
    else -> "${(100 * (s.recentShare ?: 0.0)).roundToInt()}% из последних ${s.recent.size}" + if (s.readyForMore) " ✓" else ""
}

@Composable
private fun ExerciseList(state: EarUiState, vm: EarViewModel) {
    Text("Тренировка слуха", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "Без уда в руках: приложение играет — ты узнаёшь. Серии по ${EarUiState.SERIES} вопросов; после ответа можно послушать варианты рядом. " +
            "Когда 8 из последних 10 верны, рядом с уровнем появится ✓ — пора выше.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    EarExercise.entries.forEach { e ->
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(e.about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        EarLevel.entries.joinToString("   ·   ") { l -> "${l.title}: ${statLine(state.stats[e to l] ?: EarStats())}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { vm.open(e) }) { Text("Начать") }
            }
        }
    }
}

@Composable
private fun Session(state: EarUiState, vm: EarViewModel, exercise: EarExercise) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = vm::close) { Text("← Все задания") }
        Text(exercise.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Уровень", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
        EarLevel.entries.forEach { l ->
            val ready = (state.stats[exercise to l] ?: EarStats()).readyForMore
            FilterChip(state.level == l, onClick = { vm.setLevel(l) }, label = { Text(l.title + if (ready) " ✓" else "") })
        }
        Text(
            "Вопрос ${minOf(state.asked + if (state.chosen == null) 1 else 0, EarUiState.SERIES)} из ${EarUiState.SERIES} · верно ${state.right}",
            Modifier.align(Alignment.CenterVertically).padding(start = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val q = state.question
    if (q == null || state.loading) {
        Text("Готовлю вопрос…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(q.prompt, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (state.playing) {
            OutlinedButton(onClick = vm::stop) { Text("■ Стоп") }
        } else {
            OutlinedButton(onClick = vm::replay) { Text("🔁 Послушать ещё раз") }
        }
    }
    // The options: big buttons; once answered, the right one green and a wrong pick marked.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        q.options.forEachIndexed { i, option ->
            val answered = state.chosen != null
            val colors = when {
                answered && i == q.correct -> ButtonDefaults.buttonColors(containerColor = InTuneGreen, contentColor = MaterialTheme.colorScheme.surface)
                answered && i == state.chosen -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                else -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Button(onClick = { vm.choose(i) }, colors = colors, modifier = Modifier.widthIn(min = 150.dp).heightIn(min = 56.dp)) {
                Text(option, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    val chosen = state.chosen ?: return
    val right = chosen == q.correct
    Text(
        if (right) "✓ Верно — ${q.options[q.correct]}" else "✗ Это ${q.options[q.correct]}, а не ${q.options[chosen]}",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = if (right) InTuneGreen else MaterialTheme.colorScheme.error,
    )
    Text(q.explanation, style = MaterialTheme.typography.bodyLarge)
    if (q.compare.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Сравни:", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
            q.options.forEachIndexed { i, option ->
                FilterChip(state.playingOption == i, onClick = { vm.playOption(i) }, label = { Text("▶ $option") })
            }
        }
    }
    if (state.seriesDone) {
        val stats = state.stats[exercise to state.level] ?: EarStats()
        Text(
            "Серия окончена: ${state.right} из ${EarUiState.SERIES}." +
                if (stats.readyForMore && state.level != EarLevel.Hard) " Отлично — попробуй уровень выше." else "",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Button(onClick = vm::next) { Text("Новая серия") }
    } else {
        Button(onClick = vm::next) { Text("Дальше ➡") }
    }
}
