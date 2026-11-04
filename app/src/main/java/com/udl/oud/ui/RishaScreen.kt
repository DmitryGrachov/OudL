@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.udl.oud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udl.oud.risha.ClickMode
import com.udl.oud.risha.GridStats
import com.udl.oud.risha.RishaAnalysis
import com.udl.oud.risha.RishaMode
import com.udl.oud.risha.RishaUiState
import com.udl.oud.risha.RishaViewModel
import com.udl.oud.risha.Subdivision
import com.udl.oud.risha.TremoloStats
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.abs
import kotlin.math.roundToInt

private val Amber = Color(0xFFD9A441)

/** The risha tab: the right hand on its own — tremolo, alternate picking, and speed. */
@Composable
fun RishaRoute(vm: RishaViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val mic = rememberMicPermission()
    if (mic.granted) {
        LifecycleResumeEffect(Unit) {
            @Suppress("MissingPermission")
            vm.start()
            onPauseOrDispose { vm.stopListening() }
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
                    Text("Риша", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
                    RishaMode.entries.forEach { m -> FilterChip(state.mode == m, onClick = { vm.setMode(m) }, label = { Text(m.title) }) }
                }
                if (!mic.granted) {
                    PermissionRequest(mic.request)
                    return@Column
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when (state.mode) {
                    RishaMode.Tremolo -> TremoloPanel(state, vm)
                    RishaMode.Strokes -> StrokesPanel(state, vm)
                }
            }
        }
    }
}

/** Tremolo: play it on one note; the app hears how fast it goes and how even it is. */
@Composable
private fun TremoloPanel(state: RishaUiState, vm: RishaViewModel) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        "Сыграй тремоло (раш) на одной ноте: вниз-вверх, от кисти, риша чуть наклонена. Начни медленно и ровно, " +
            "потом ускоряйся, пока ровность держится. Я слушаю каждый удар: как часто и насколько одинаково.",
        style = MaterialTheme.typography.bodyMedium,
        color = muted,
    )
    val t = state.tremolo
    if (t == null) {
        Text("Жду тремоло — хотя бы ${RishaAnalysis.MIN_TREMOLO} ударов подряд…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Bottom) {
            Big("%.1f".format(t.perSecond), "ударов в секунду")
            Big("${t.evenness}%", "ровность", color = evennessColor(t.evenness))
            Big("±%.1f".format(t.levelSpreadDb), "дБ по громкости")
            Big("${t.strokes}", "ударов подряд")
        }
        Text(
            when {
                t.evenness >= 90 -> "Ровно! Можно прибавить скорость."
                t.evenness >= 80 -> "Почти ровно: следи, чтобы вверх и вниз были одинаковыми."
                else -> "Неровно: расслабь кисть и сбавь скорость — ровность важнее быстроты."
            } + if (t.levelSpreadDb > 3) " Громкость скачет — держи одинаковый нажим." else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        IntervalChart(t, Modifier.fillMaxWidth().height(140.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.bestRate > 0) {
            Text("Лучшее ровное тремоло: ${"%.1f".format(state.bestRate)} уд/с", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = vm::resetTremolo) { Text("Сбросить") }
    }
}

@Composable
private fun Big(value: String, label: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun evennessColor(e: Int) = when {
    e >= 90 -> InTuneGreen
    e >= 80 -> Amber
    else -> Color(0xFFE57373)
}

/** The gaps between the strokes of the run, as bars around their mean: a ragged row is an uneven tremolo. */
@Composable
private fun IntervalChart(t: TremoloStats, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.outline
    val gaps = t.intervals.takeLast(48)
    Canvas(modifier) {
        if (gaps.isEmpty()) return@Canvas
        val mean = gaps.average()
        val max = maxOf(gaps.max(), mean * 1.6)
        val w = size.width / gaps.size
        val y = { v: Double -> (size.height - v / max * size.height).toFloat() }
        gaps.forEachIndexed { i, g ->
            val off = abs(g - mean) / mean
            val color = when {
                off <= 0.08 -> InTuneGreen
                off <= 0.18 -> Amber
                else -> Color(0xFFE57373)
            }
            drawRect(color, Offset(i * w + w * 0.15f, y(g)), Size(w * 0.7f, size.height - y(g)))
        }
        drawLine(line, Offset(0f, y(mean)), Offset(size.width, y(mean)), 2.dp.toPx())
    }
}

/** Alternate picking on the metronome's grid: down on the beat, up between; timing and strength of each. */
@Composable
private fun StrokesPanel(state: RishaUiState, vm: RishaViewModel) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        "Переменный штрих: вниз на долю, вверх между долями — на каждый щелчок по удару. Метроном даёт один такт из четырёх, " +
            "три такта держишь сам (иначе микрофон слышит щелчки). В наушниках можно включить щелчки всегда.",
        style = MaterialTheme.typography.bodyMedium,
        color = muted,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.playing) Button(onClick = vm::stop) { Text("■ Стоп", fontSize = 18.sp) }
        else FilledTonalButton(onClick = { vm.play() }) { Text("▶ Начать", fontSize = 18.sp) }
        TextButton(onClick = { vm.changeBpm(-4) }) { Text("−4") }
        Text("♩ = ${state.bpm}", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { vm.changeBpm(4) }) { Text("+4") }
        Slider(
            value = state.bpm.toFloat(),
            onValueChange = { vm.setBpm(it.roundToInt()) },
            valueRange = RishaUiState.BPM_MIN.toFloat()..RishaUiState.BPM_MAX.toFloat(),
            modifier = Modifier.width(240.dp).align(Alignment.CenterVertically),
        )
        FilterChip(state.ladder, onClick = vm::toggleLadder, label = { Text("Лесенка темпа") })
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Subdivision.entries.forEach { s -> FilterChip(state.subdivision == s, onClick = { vm.setSubdivision(s) }, label = { Text(s.title) }) }
        Text("·", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outline)
        ClickMode.entries.forEach { m -> FilterChip(state.clickMode == m, onClick = { vm.setClickMode(m) }, label = { Text(m.title) }) }
    }
    if (state.ladder) {
        Text(
            "Лесенка: два чистых такта подряд (85% вовремя, без пропусков) — и темп растёт на 4. Лучший темп: ${if (state.bestBpm > 0) state.bestBpm else "—"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (state.playing) {
        Text(
            if (state.clicksNow && state.clickMode == ClickMode.Reminder) "Слушай щелчки… (такт ${state.bar % RishaUiState.BARS + 1})" else "Твой такт — играй!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (state.clicksNow && state.clickMode == ClickMode.Reminder) muted else MaterialTheme.colorScheme.primary,
        )
    }
    StrokeGrid(state, Modifier.fillMaxWidth().height(120.dp))
    state.lastBar?.let { g -> BarReport(state, g) }
    Text(
        "Задержка микрофона ${state.latency.roundToInt()} мс — калибруется на вкладке «Ритм» → «На уде».",
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
}

/**
 * The bar as a grid of strokes, ↓ on the beats and ↑ between (for triplets the hand alternates
 * across the beat). The step sounding is lit; after a checked bar each stroke shows how it went.
 */
@Composable
private fun StrokeGrid(state: RishaUiState, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val line = MaterialTheme.colorScheme.outlineVariant
    val strong = MaterialTheme.colorScheme.outline
    val lit = MaterialTheme.colorScheme.secondaryContainer
    val ink = MaterialTheme.colorScheme.onSurface
    val error = MaterialTheme.colorScheme.error
    val n = state.stepsPerBar
    val errors = state.lastBar?.errors
    Canvas(modifier) {
        val w = size.width / n
        state.step?.let { s -> drawRect(lit, Offset(s * w, 0f), Size(w, size.height - 20.dp.toPx())) }
        for (i in 0..n) {
            val beat = i % state.subdivision.perBeat == 0
            drawLine(if (beat) strong else line, Offset(i * w, 0f), Offset(i * w, size.height - 20.dp.toPx()), if (beat) 3.dp.toPx() else 1.dp.toPx())
        }
        for (i in 0 until n) {
            val e = errors?.getOrNull(i)
            val missed = errors != null && e == null
            val color = when {
                errors == null -> ink
                missed -> error
                abs(e!!) <= RishaAnalysis.GOOD_MS -> InTuneGreen
                else -> Amber
            }
            val t = measurer.measure(if (i % 2 == 0) "↓" else "↑", TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color))
            drawText(t, topLeft = Offset(i * w + w / 2 - t.size.width / 2f, (size.height - 20.dp.toPx()) / 2 - t.size.height / 2f))
            if (e != null) {
                val x = i * w + w / 2 + (e / state.stepMs() * w).toFloat()
                drawLine(color, Offset(x, size.height - 16.dp.toPx()), Offset(x, size.height - 2.dp.toPx()), 4.dp.toPx())
            }
        }
    }
}

@Composable
private fun BarReport(state: RishaUiState, g: GridStats) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Последний такт: вовремя ${g.onTime} из ${g.expected} · разброс ±${g.spreadMs.roundToInt()} мс" +
                (if (g.missed > 0) " · пропущено ${g.missed}" else "") + (if (g.extra > 0) " · лишних ${g.extra}" else ""),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (g.onTime == g.expected && g.extra == 0) InTuneGreen else MaterialTheme.colorScheme.onSurface,
        )
        val bias = g.upMs - g.downMs
        val notes = buildList {
            if (abs(bias) >= 10) add(if (bias > 0) "удар вверх запаздывает на ${bias.roundToInt()} мс" else "удар вверх торопится на ${(-bias).roundToInt()} мс")
            if (abs(g.upLevelDb) >= 3) add(if (g.upLevelDb < 0) "вверх тише на ${(-g.upLevelDb).roundToInt()} дБ" else "вверх громче на ${g.upLevelDb.roundToInt()} дБ")
            if (abs(g.downMs) >= 15) add(if (g.downMs > 0) "доли в среднем позже на ${g.downMs.roundToInt()} мс" else "доли в среднем раньше на ${(-g.downMs).roundToInt()} мс")
        }
        Text(
            if (notes.isEmpty()) "Вниз и вверх ровные." else notes.joinToString(" · ").replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyLarge,
            color = if (notes.isEmpty()) InTuneGreen else Amber,
        )
        if (state.expected > 0) {
            Text(
                "Всего тактов: ${state.bars} · вовремя ${(state.onTime * 100.0 / state.expected).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
        }
    }
}
