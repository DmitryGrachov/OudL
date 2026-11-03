@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.udl.oud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udl.oud.fretboard.Fretboard
import com.udl.oud.turkish.TurkishBoard
import com.udl.oud.turkish.TurkishLabels
import com.udl.oud.turkish.TurkishMakamUiState
import com.udl.oud.turkish.TurkishMakamViewModel
import com.udl.oud.turkish.TurkishMakams
import com.udl.oud.turkish.TurkishMark
import com.udl.oud.turkish.komaStepLabel
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The maqam tab on a Turkish oud: makams on the 53-koma grid. */
@Composable
fun TurkishMakamRoute(vm: TurkishMakamViewModel = viewModel()) {
    val mic = rememberMicPermission()
    val state by vm.state.collectAsStateWithLifecycle()
    if (mic.granted && state.live) {
        LifecycleResumeEffect(Unit) {
            @Suppress("MissingPermission")
            vm.start()
            onPauseOrDispose { vm.stop() }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(topBar = { if (!wide) TopAppBar(title = { Text("Макамы") }) }) { padding ->
            val content: @Composable (Modifier) -> Unit = { boardModifier ->
                if (state.exercise != null) TurkishExercise(state, vm) else TurkishHeardBar(state, vm, mic.granted)
                TurkishMakamBar(state, vm)
                TurkishBoardCanvas(state, vm, boardModifier)
            }
            if (wide) {
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 8.dp)) {
                    content(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp))
                }
            } else {
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    content(Modifier.fillMaxWidth().height(420.dp).padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TurkishHeardBar(state: TurkishMakamUiState, vm: TurkishMakamViewModel, micGranted: Boolean) {
    val heard = state.heard
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            when {
                state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                !micGranted || !state.live -> Text("Нажми на точку, чтобы услышать ступень", color = muted)
                heard == null -> Text(
                    "${state.makam.turkishName}: играй — ступень подсветится, отклонение в центах",
                    style = MaterialTheme.typography.titleMedium,
                    color = muted,
                )
                abs(heard.cents) > TurkishMakamUiState.OUTSIDE_CENTS -> Text(
                    "Не из макама: ближе всего ${state.labelOf(heard.degree, heard.octave)} ${signedCents(heard.cents)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                else -> {
                    val inTune = abs(heard.cents) <= TurkishMakamUiState.IN_TUNE_CENTS
                    Text(
                        state.labelOf(heard.degree, heard.octave),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (inTune) InTuneGreen else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "  ${signedCents(heard.cents)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (inTune) InTuneGreen else MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        Text("Слушать", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = state.live, onCheckedChange = vm::setLive)
    }
}

/** Family (durak) and makam pickers, controls, and the scale written out with its koma steps. */
@Composable
private fun TurkishMakamBar(state: TurkishMakamUiState, vm: TurkishMakamViewModel) {
    val family = TurkishMakams.families.first { state.makam in it.makams }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Дурак", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
            TurkishMakams.families.forEach { f ->
                FilterChip(f == family, onClick = { if (f != family) vm.select(f.makams.first()) }, label = { Text(f.name) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Макам", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
            family.makams.forEach { m ->
                FilterChip(
                    m == state.makam,
                    onClick = { vm.select(m) },
                    label = { Text("${m.name} · ${m.turkishName}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = vm::playScale) { Text("▶ Сыграть") }
            TextButton(onClick = vm::startExercise) { Text("🎯 Упражнение") }
            Text("·", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outline)
            TurkishLabels.entries.forEach { labels ->
                FilterChip(state.labels == labels, onClick = { vm.setLabels(labels) }, label = { Text(labels.title) })
            }
            FilterChip(state.extended, onClick = vm::toggleExtended, label = { Text("Дальше шейки") })
        }
        // The scale with each step in komas and its Turkish name: T = 9, K = 8, S = 5, B = 4, A12 / A13.
        val lower = MaterialTheme.colorScheme.primary
        val upper = upperJinsColor()
        val steps = TurkishBoard.ascending()
        Text(
            buildAnnotatedString {
                steps.forEachIndexed { i, (degree, octave) ->
                    val isUpper = degree != 0 && state.degrees[degree].upper
                    val playing = state.playing == (degree to octave)
                    val style = if (playing) {
                        SpanStyle(
                            color = MaterialTheme.colorScheme.onPrimary,
                            background = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        SpanStyle(color = if (isUpper) upper else lower, fontWeight = FontWeight.SemiBold)
                    }
                    withStyle(style) { append(" ${state.labelOf(degree, octave)} ") }
                    if (i < state.makam.intervals.size) {
                        val k = state.makam.intervals[i]
                        withStyle(SpanStyle(color = muted)) { append(" ${komaStepLabel(k)}($k) ") }
                    }
                }
            },
            style = MaterialTheme.typography.titleMedium,
        )
        val m = state.makam
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = lower)) { append("${m.lower.name} на ${state.labelOf(0, 0)}") }
                append("  +  ")
                withStyle(SpanStyle(color = upper)) { append("${m.upper.name} на ${state.labelOf(m.upperFrom, 0)}") }
                append("    ·  1 комма ≈ 22,6 ц, тон = 9 комм")
                if (state.labels != TurkishLabels.Sounding) append("  ·  запись Bolahenk: звучит на октаву и кварту ниже")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
    }
}

@Composable
private fun TurkishExercise(state: TurkishMakamUiState, vm: TurkishMakamViewModel) {
    val exercise = state.exercise ?: return
    val steps = TurkishBoard.ascending()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val target = state.target
                if (target == null) {
                    val worst = exercise.worst
                    Text(
                        "Готово: ${exercise.inTuneCount} из ${exercise.steps.size} точно · средняя ошибка " +
                            "${exercise.meanError.roundToInt()} ц" +
                            (worst?.let { " · хуже всего ${state.labelOf(steps[it].first, steps[it].second)} ${signedCents(exercise.results[it])}" } ?: ""),
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else {
                    Text("Нота ${exercise.results.size + 1} из ${exercise.steps.size}:  ", color = muted)
                    Text(
                        "сыграй ${state.labelOf(target.first, target.second)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    state.exerciseCents?.let { cents ->
                        Text(
                            when {
                                abs(cents) > 50 -> "   другая нота"
                                abs(cents) <= TurkishMakamUiState.IN_TUNE_CENTS -> "   ${signedCents(cents)} — держи"
                                cents < 0 -> "   низко на ${abs(cents).roundToInt()} ц"
                                else -> "   высоко на ${cents.roundToInt()} ц"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (abs(cents) <= TurkishMakamUiState.IN_TUNE_CENTS) InTuneGreen else MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            TextButton(onClick = vm::startExercise) { Text(if (exercise.done) "Ещё раз" else "Заново") }
            TextButton(onClick = vm::stopExercise) { Text("Закончить") }
        }
        Text(
            buildAnnotatedString {
                steps.forEachIndexed { i, (degree, octave) ->
                    val result = exercise.results.getOrNull(i)
                    val style = when {
                        result != null -> SpanStyle(
                            color = if (abs(result) <= TurkishMakamUiState.IN_TUNE_CENTS) InTuneGreen else MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        i == exercise.results.size -> SpanStyle(
                            color = MaterialTheme.colorScheme.onPrimary,
                            background = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        else -> SpanStyle(color = muted)
                    }
                    withStyle(style) {
                        append(" ${state.labelOf(degree, octave)}")
                        if (result != null) append(" ${signedCents(result)}")
                        append(" ")
                    }
                    if (i < steps.lastIndex) withStyle(SpanStyle(color = muted)) { append(" · ") }
                }
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** The neck with the makam's degrees placed by komas, not on the quarter-tone grid. */
@Composable
private fun TurkishBoardCanvas(state: TurkishMakamUiState, vm: TurkishMakamViewModel, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val maxCents = if (state.extended) Fretboard.EXTENDED_CENTS else Fretboard.NECK_CENTS
    val marks = remember(state.makam, state.tuning, state.a4, maxCents) {
        TurkishBoard.marks(state.makam, state.tuning, state.a4.toDouble(), maxCents)
    }
    val heard = state.heard?.takeIf { abs(it.cents) <= TurkishMakamUiState.OUTSIDE_CENTS }
    val colors = boardColors(
        if (heard != null && abs(heard.cents) <= TurkishMakamUiState.IN_TUNE_CENTS) InTuneGreen else MaterialTheme.colorScheme.tertiary,
    )
    Canvas(
        modifier.pointerInput(marks, state.tuning) {
            detectTapGestures { tap ->
                val g = BoardGeometry(Size(size.width.toFloat(), size.height.toFloat()), state.tuning.courses.size, true, maxCents, this)
                marks.minByOrNull { hypot(g.x(it.cents) - tap.x, g.y(it.course) - tap.y) }
                    ?.takeIf { hypot(g.x(it.cents) - tap.x, g.y(it.course) - tap.y) <= 30.dp.toPx() }
                    ?.let { vm.play(it.degree, it.octave) }
            }
        },
    ) {
        val g = BoardGeometry(size, state.tuning.courses.size, true, maxCents, this)
        drawBoard(g, colors, measurer)
        marks.forEach { mark ->
            val key = mark.degree to mark.octave
            val highlight = when {
                key == state.playing || key == state.target -> colors.playing
                heard != null && key == (heard.degree to heard.octave) -> colors.heard
                else -> null
            }
            drawMark(mark, g, state.labelOf(mark.degree, mark.octave), state.degrees[mark.degree].upper && mark.degree != 0, highlight, colors, measurer)
        }
    }
}

private fun DrawScope.drawMark(
    mark: TurkishMark,
    g: BoardGeometry,
    text: String,
    upper: Boolean,
    highlight: Color?,
    c: BoardColors,
    measurer: TextMeasurer,
) {
    val center = Offset(g.x(mark.cents), g.y(mark.course))
    val radius = 15.dp.toPx() * g.dotScale
    if (highlight != null) drawCircle(highlight.copy(alpha = 0.3f), radius = radius + 12.dp.toPx() * g.dotScale, center = center)
    drawCircle(highlight ?: if (upper) c.upperJins else c.natural, radius = radius, center = center)
    if (mark.cents == 0.0) drawCircle(c.nut, radius = radius, center = center, style = Stroke(2.dp.toPx()))
    val tonic = mark.degree == 0
    if (tonic) drawCircle(c.label, radius = radius + 4.dp.toPx() * g.dotScale, center = center, style = Stroke(3.dp.toPx() * g.dotScale))
    val label = measurer.measure(
        text,
        TextStyle(fontSize = 11.sp * g.dotScale, fontWeight = FontWeight.SemiBold, color = highlight ?: c.label),
    )
    val gap = (if (tonic) 7.dp else 3.dp).toPx() * g.dotScale
    drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y + radius + gap))
}
