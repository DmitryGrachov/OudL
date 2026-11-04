@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.udl.oud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udl.oud.audio.Iqa
import com.udl.oud.audio.IqaGroup
import com.udl.oud.audio.Iqaat
import com.udl.oud.audio.Stroke
import com.udl.oud.rhythm.DrumMode
import com.udl.oud.rhythm.HitKind
import com.udl.oud.rhythm.RhythmMode
import com.udl.oud.rhythm.RhythmUiState
import com.udl.oud.rhythm.RhythmViewModel
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.abs
import kotlin.math.roundToInt

private val Amber = Color(0xFFD9A441)

@Composable
fun RhythmRoute(vm: RhythmViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val mic = rememberMicPermission()
    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            vm.stop()
            vm.stopMic()
        }
    }
    // The microphone listens only while the oud mode is open.
    if (state.mode == RhythmMode.Oud && mic.granted) {
        LifecycleResumeEffect(Unit) {
            @Suppress("MissingPermission")
            vm.startMic()
            onPauseOrDispose { vm.stopMic() }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Режим", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
                    RhythmMode.entries.forEach { m ->
                        FilterChip(state.mode == m, onClick = { vm.setMode(m) }, label = { Text(m.title) })
                    }
                }
                if (state.mode == RhythmMode.Quiz) {
                    QuizPanel(state, vm)
                } else {
                    // While practising the library folds into one line, so the pads stay on screen.
                    val practising = state.mode == RhythmMode.Tap || state.mode == RhythmMode.Oud
                    var pickerOpen by rememberSaveable { mutableStateOf(false) }
                    if (!practising || pickerOpen) IqaPicker(state, vm)
                    if (practising) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${state.iqa.name} ${state.iqa.meter}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "   ${state.iqa.spoken}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { pickerOpen = !pickerOpen }) { Text(if (pickerOpen) "Скрыть список" else "Сменить ритм") }
                        }
                    } else {
                        IqaInfo(state.iqa)
                    }
                    CycleGrid(state, Modifier.fillMaxWidth().height(if (wide) 150.dp else 120.dp))
                    PlayControls(state, vm)
                    when (state.mode) {
                        RhythmMode.Tap -> TapPanel(state, vm)
                        RhythmMode.Oud -> if (mic.granted) OudPanel(state, vm) else PermissionRequest(mic.request)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun IqaPicker(state: RhythmUiState, vm: RhythmViewModel) {
    IqaGroup.entries.forEach { group ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(group.title, Modifier.align(Alignment.CenterVertically).width(170.dp), style = MaterialTheme.typography.labelLarge)
            Iqaat.all.filter { it.group == group }.forEach { iqa ->
                FilterChip(
                    state.iqa == iqa,
                    onClick = { vm.select(iqa) },
                    label = { Text("${iqa.name} ${iqa.meter}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun IqaInfo(iqa: Iqa) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(iqa.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "  ${iqa.latin} · ${iqa.meter}" + (iqa.grouping?.let { " (${it.joinToString("+")})" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                color = muted,
            )
        }
        Text(iqa.about, style = MaterialTheme.typography.bodyLarge)
        Text("Счёт: ${iqa.spoken}", style = MaterialTheme.typography.bodyMedium, color = muted)
    }
}

/**
 * The cycle on a grid: a cell per step, bold lines where beats (or the 2s and 3s) start; dum a
 * big dark circle, tek a smaller bright one, ka a ring. The step sounding is lit; after a checked
 * cycle each stroke shows how it went.
 */
@Composable
private fun CycleGrid(state: RhythmUiState, modifier: Modifier) {
    val iqa = state.iqa
    val measurer = rememberTextMeasurer()
    val line = MaterialTheme.colorScheme.outlineVariant
    val strong = MaterialTheme.colorScheme.outline
    val dum = MaterialTheme.colorScheme.primary
    val tak = MaterialTheme.colorScheme.tertiary
    val lit = MaterialTheme.colorScheme.secondaryContainer
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val onDum = MaterialTheme.colorScheme.surface
    val error = MaterialTheme.colorScheme.error
    val results = state.last?.strokes?.associateBy { it.step }.orEmpty()
    val stepMs = iqa.stepMs(state.bpm)
    Canvas(modifier) {
        val n = iqa.strokes.size
        val w = size.width / n
        val top = 22.dp.toPx()
        val h = size.height - top - 18.dp.toPx()
        val cy = top + h / 2
        val r = minOf(w * 0.4f, h * 0.36f)
        state.step?.let { s ->
            drawRect(if (state.drumNow) lit else lit.copy(alpha = 0.45f), Offset(s * w, top), androidx.compose.ui.geometry.Size(w, h))
        }
        val groups = iqa.groupStarts
        var beat = 0
        for (i in 0..n) {
            val x = i * w
            val isGroup = i in groups || i == n
            drawLine(if (isGroup) strong else line, Offset(x, top), Offset(x, top + h), if (isGroup) 3.dp.toPx() else 1.dp.toPx())
            if (i < n && i in groups) {
                beat++
                val t = measurer.measure("$beat", TextStyle(fontSize = 12.sp, color = label, fontWeight = FontWeight.SemiBold))
                drawText(t, topLeft = Offset(x + 4.dp.toPx(), 2.dp.toPx()))
            }
        }
        iqa.strokes.forEachIndexed { i, s ->
            val c = Offset(i * w + w / 2, cy)
            when (s) {
                Stroke.Dum -> drawCircle(dum, r, c)
                Stroke.Tak -> drawCircle(tak, r * 0.72f, c)
                Stroke.Ka -> drawCircle(tak, r * 0.45f, c, style = DrawStroke(2.dp.toPx()))
                Stroke.Rest -> drawCircle(line, r * 0.12f, c)
            }
            val letter = when (s) {
                Stroke.Dum -> "Д"
                Stroke.Tak -> "Т"
                else -> null
            }
            letter?.let {
                val t = measurer.measure(it, TextStyle(fontSize = (r * 0.7f).toSp(), color = onDum, fontWeight = FontWeight.Bold))
                drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
            }
            results[i]?.let { res -> drawResult(res.kind, res.errorMs, c, r, w, stepMs, top + h, error) }
        }
    }
}

private fun DrawScope.drawResult(kind: HitKind, errorMs: Double?, c: Offset, r: Float, w: Float, stepMs: Double, bottom: Float, error: Color) {
    val color = when (kind) {
        HitKind.Good -> InTuneGreen
        HitKind.Ok -> Amber
        HitKind.WrongStroke, HitKind.Missed -> error
    }
    if (kind == HitKind.Missed) {
        val d = r * 0.8f
        drawLine(color, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), 4.dp.toPx())
        drawLine(color, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), 4.dp.toPx())
        return
    }
    drawCircle(color, r + 6.dp.toPx(), c, style = DrawStroke(4.dp.toPx()))
    // Below the cell: where the stroke really fell, early to the left, late to the right.
    errorMs?.let { e ->
        val x = c.x + (e / stepMs * w).toFloat()
        drawLine(color, Offset(x, bottom + 3.dp.toPx()), Offset(x, bottom + 15.dp.toPx()), 4.dp.toPx())
    }
}

@Composable
private fun PlayControls(state: RhythmUiState, vm: RhythmViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.playing && !state.calibrating) {
            Button(onClick = vm::stop) { Text("■ Стоп", fontSize = 18.sp) }
        } else {
            FilledTonalButton(onClick = vm::play, enabled = !state.calibrating) { Text("▶ Играть", fontSize = 18.sp) }
        }
        TextButton(onClick = { vm.changeBpm(-5) }) { Text("−5") }
        Text(
            "${state.iqa.beatUnit} = ${state.bpm}",
            Modifier.align(Alignment.CenterVertically),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = { vm.changeBpm(5) }) { Text("+5") }
        Slider(
            value = state.bpm.toFloat(),
            onValueChange = { vm.setBpm(it.roundToInt()) },
            valueRange = RhythmUiState.BPM_MIN.toFloat()..RhythmUiState.BPM_MAX.toFloat(),
            modifier = Modifier.width(260.dp).align(Alignment.CenterVertically),
        )
        FilterChip(state.clicks, onClick = vm::toggleClicks, label = { Text("Щелчки на долях") })
        if (state.mode == RhythmMode.Tap) {
            DrumMode.entries.forEach { m ->
                FilterChip(state.drumMode == m, onClick = { vm.setDrumMode(m) }, label = { Text(m.title) })
            }
        }
    }
}

/** Two pads, dum and tek: tap the cycle along with the drum, or on your own between its reminders. */
@Composable
private fun TapPanel(state: RhythmUiState, vm: RhythmViewModel) {
    Text(
        if (state.drumMode == DrumMode.Always) "Нажми «Играть» и стучи вместе с барабаном: левая — дум, правая — тек. Первый цикл — вступление."
        else "Барабан играет один цикл из четырёх, остальные три — ты сам. Держи темп по памяти (или со щелчками).",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Pad("ДУМ", MaterialTheme.colorScheme.primary, state.flash == Stroke.Dum, Modifier.weight(1f)) { vm.tap(Stroke.Dum) }
        Pad("ТЕК", MaterialTheme.colorScheme.tertiary, state.flash == Stroke.Tak, Modifier.weight(1f)) { vm.tap(Stroke.Tak) }
    }
    Results(state)
    Calibration(state, vm, "Калибровка: 14 щелчков — стучи по любой площадке точно с ними.", state.tapLatency)
}

@Composable
private fun Pad(text: String, color: Color, flash: Boolean, modifier: Modifier, onHit: () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(if (flash) color else color.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            // On the finger's touch, not its release: that is when the stroke sounds.
            .pointerInput(Unit) { detectTapGestures(onPress = { onHit() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = if (flash) Color.White else color)
    }
}

/** Playing the iqa' on the oud: the microphone hears the strokes, only their timing is checked. */
@Composable
private fun OudPanel(state: RhythmUiState, vm: RhythmViewModel) {
    Text(
        "Играй ритм на уде: дум — на басовой струне, тек — на верхних (или по корпусу). Барабан играет один цикл " +
            "из четырёх, три цикла — ты сам, я слушаю, вовремя ли удары. Какой удар — дум или тек — не различаю.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.playing && !state.calibrating) {
        Text(
            if (state.drumNow) "Слушай барабан…" else "Твой цикл — играй!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (state.drumNow) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Results(state)
    Calibration(state, vm, "Калибровка: 14 щелчков — щипай струну точно с ними.", state.micLatency)
}

/** The last cycle in words and the run so far. */
@Composable
private fun Results(state: RhythmUiState) {
    val st = state.stats
    if (st.cycles == 0) return
    val last = state.last
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        last?.let { c ->
            val total = c.strokes.size
            Text(
                "Последний цикл: вовремя ${c.onTime} из $total" +
                    (c.strokes.count { it.kind == HitKind.WrongStroke }.takeIf { it > 0 }?.let { " · не тот удар: $it" } ?: "") +
                    (c.extra.takeIf { it > 0 }?.let { " · лишних: $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (c.onTime == total && c.extra == 0) InTuneGreen else MaterialTheme.colorScheme.onSurface,
            )
        }
        val drift = st.meanError
        Text(
            "Всего за ${st.cycles} цикл.: точно ${(st.accuracy * 100).roundToInt()}% · " +
                when {
                    st.matched == 0 -> "ударов не слышно"
                    abs(drift) < 15 -> "ровно в долю"
                    drift < 0 -> "в среднем раньше на ${abs(drift).roundToInt()} мс — не торопись"
                    else -> "в среднем позже на ${drift.roundToInt()} мс — чуть опережай"
                } +
                " · пропущено ${st.missed}" + (if (st.wrong > 0) " · не тот удар ${st.wrong}" else "") + " · лишних ${st.extra}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Calibration(state: RhythmUiState, vm: RhythmViewModel, how: String, latency: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.calibrating) {
            Text("Калибровка… ударов: ${state.calibrationHits}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = vm::stop) { Text("Отмена") }
        } else {
            OutlinedButton(onClick = vm::calibrate) { Text("⏱ Калибровка") }
            Text(
                "задержка ${latency.roundToInt()} мс · $how",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Guess by ear: an iqa' plays, pick its name from four. */
@Composable
private fun QuizPanel(state: RhythmUiState, vm: RhythmViewModel) {
    val q = state.quiz ?: return
    Text("Какой это ритм?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "Слушай, считай доли и ищи, где думы. Счёт: ${q.right} из ${q.asked - if (q.picked == null) 1 else 0}",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.playing) {
            Button(onClick = vm::stop) { Text("■ Стоп") }
        } else {
            FilledTonalButton(onClick = vm::play) { Text("▶ Ещё раз") }
        }
        TextButton(onClick = vm::nextQuestion) { Text("➡ Следующий") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        q.options.forEach { o ->
            val shown = q.picked != null
            val color = when {
                shown && o == q.answer -> InTuneGreen
                shown && o == q.picked -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            Button(
                onClick = { vm.pick(o) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f).height(64.dp),
            ) { Text(o.name, fontSize = 18.sp) }
        }
    }
    q.picked?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            (if (it == q.answer) "Верно! " else "Это ${q.answer.name}. ") + "${q.answer.meter} — ${q.answer.spoken}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(q.answer.about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
