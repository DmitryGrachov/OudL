@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.udl.oud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.udl.oud.tuner.Ahenk
import com.udl.oud.turkish.Perdes
import com.udl.oud.turkish.TurkishBoard
import com.udl.oud.turkish.TurkishLabels
import com.udl.oud.turkish.TurkishMakamUiState
import com.udl.oud.turkish.TurkishMakamViewModel
import com.udl.oud.turkish.TurkishMakams
import com.udl.oud.turkish.TurkishMark
import com.udl.oud.turkish.TurkishScale
import com.udl.oud.turkish.TurkishSeyirs
import com.udl.oud.turkish.TurkishSpelling
import com.udl.oud.turkish.TurkishTone
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
        // A narrow staff in portrait leaves the board its width.
        val staffWidth = if (maxWidth >= 900.dp) 200.dp else 120.dp
        Scaffold(topBar = { if (!wide) TopAppBar(title = { Text("Макамы") }) }) { padding ->
            val content: @Composable (Modifier) -> Unit = { boardModifier ->
                if (state.exercise != null) TurkishExercise(state, vm) else TurkishHeardBar(state, vm, mic.granted)
                TurkishMakamBar(state, vm)
                if (state.seyirOpen) SeyirPanel(state, vm)
                Row(boardModifier) {
                    TurkishBoardCanvas(state, vm, Modifier.weight(1f).fillMaxHeight())
                    if (state.showStaff) TurkishStaff(state, Modifier.width(staffWidth).fillMaxHeight().padding(start = 8.dp))
                }
            }
            if (wide && !state.seyirOpen) {
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 8.dp)) {
                    content(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp))
                }
            } else if (wide) {
                // The seyir takes room the board can't spare: the page scrolls, the board keeps its height.
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    content(Modifier.fillMaxWidth().height(380.dp).padding(top = 8.dp))
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
                    "${makamTitle(state)}: играй — ступень подсветится, отклонение в центах",
                    style = MaterialTheme.typography.titleMedium,
                    color = muted,
                )
                abs(heard.cents) > TurkishMakamUiState.OUTSIDE_CENTS -> Text(
                    "Не из макама: ближе всего ${state.labelOf(heard.tone)} ${signedCents(heard.cents)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                else -> {
                    val inTune = abs(heard.cents) <= TurkishMakamUiState.IN_TUNE_CENTS
                    Text(
                        state.labelOf(heard.tone),
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

/** "Uşşak", or for a moved scale "Uşşak şeddi на Rast" — or its own name when it has one. */
private fun makamTitle(state: TurkishMakamUiState): String {
    val durak = state.durak ?: return state.base.turkishName
    state.sedName?.let { return it.turkishName }
    return "${state.base.turkishName} şeddi на ${Perdes.of(durak) ?: "?"}"
}

/** Family (durak) and makam pickers, şed, controls, and the scale written out with its koma steps. */
@Composable
private fun TurkishMakamBar(state: TurkishMakamUiState, vm: TurkishMakamViewModel) {
    val family = TurkishMakams.families.first { state.base in it.makams }
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
                    m == state.base,
                    onClick = { vm.select(m) },
                    label = { Text("${m.name} · ${m.turkishName}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        // Şed: the same makam on another perde, its accidentals recounted in komas.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Шед на", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
            TurkishMakams.duraks.forEach { durak ->
                FilterChip(
                    state.makam.tonic == durak,
                    onClick = { vm.setDurak(durak) },
                    label = { Text(Perdes.of(durak) ?: "?") },
                )
            }
            val named = state.sedName
            if (named != null && named != state.base) {
                AssistChip(onClick = { vm.select(named) }, label = { Text("= ${named.turkishName}") })
            }
            AhenkMenu(state.ahenk, vm::setAhenk)
            FilterChip(state.showStaff, onClick = vm::toggleStaff, label = { Text("Ноты") })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = vm::playScale) { Text("▶ Сыграть") }
            TextButton(onClick = vm::startExercise) { Text("🎯 Упражнение") }
            FilterChip(!state.descending, onClick = { vm.setDescending(false) }, label = { Text("↑ Вверх") })
            FilterChip(state.descending, onClick = { vm.setDescending(true) }, label = { Text("↓ Вниз") })
            FilterChip(state.seyirOpen, onClick = vm::toggleSeyir, label = { Text("Сейир") })
            Text("·", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outline)
            TurkishLabels.entries.forEach { labels ->
                FilterChip(state.labels == labels, onClick = { vm.setLabels(labels) }, label = { Text(labels.title) })
            }
            FilterChip(state.extended, onClick = vm::toggleExtended, label = { Text("Дальше шейки") })
        }
        // The scale with each step in komas and its Turkish name: T = 9, K = 8, S = 5, B = 4, A12 / A13.
        val lower = MaterialTheme.colorScheme.primary
        val upper = upperJinsColor()
        val changed = MaterialTheme.colorScheme.tertiary
        val steps = state.steps
        val inici = TurkishSeyirs.of(state.makam).inici
        Text(
            buildAnnotatedString {
                steps.forEachIndexed { i, tone ->
                    val degree = state.degreeOf(tone)
                    val isUpper = degree != null && degree != 0 && state.degrees[degree].upper
                    val style = when {
                        state.playing?.written == tone.written -> SpanStyle(
                            color = MaterialTheme.colorScheme.onPrimary,
                            background = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        state.descending && degree in inici -> SpanStyle(color = changed, fontWeight = FontWeight.Bold)
                        else -> SpanStyle(color = if (isUpper) upper else lower, fontWeight = FontWeight.SemiBold)
                    }
                    withStyle(style) { append(" ${state.labelOf(tone)} ") }
                    steps.getOrNull(i + 1)?.let { next ->
                        val k = abs(next.written - tone.written)
                        withStyle(SpanStyle(color = muted)) { append(" ${komaStepLabel(k)}($k) ") }
                    }
                }
            },
            style = MaterialTheme.typography.titleMedium,
        )
        val m = state.makam
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = lower)) { append("${m.lower.name} на ${state.labelOf(TurkishScale.tone(m, 0))}") }
                append("  +  ")
                withStyle(SpanStyle(color = upper)) { append("${m.upper.name} на ${state.labelOf(TurkishScale.tone(m, m.upperFrom))}") }
                if (state.descending && inici.isNotEmpty()) {
                    withStyle(SpanStyle(color = changed)) {
                        append("  ·  вниз: ")
                        append(inici.keys.joinToString { d -> "${state.labelOf(TurkishScale.tone(m, d))} → ${state.labelOf(TurkishScale.tone(m, d, down = true))}" })
                    }
                }
                append("    ·  1 комма ≈ 22,6 ц, тон = 9 комм")
                if (state.labels == TurkishLabels.Written) {
                    append("  ·  запись: в Bolâhenk звучит на октаву и кварту ниже")
                    if (state.ahenk != Ahenk.Bolahenk) append(", в ${state.ahenk.title} ещё ${state.ahenk.shift}")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
    }
}

/** Ahenk picker: the same as on the tuner, kept small here. */
@Composable
private fun AhenkMenu(ahenk: Ahenk, onSelect: (Ahenk) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("Ахенк: ${ahenk.title} ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Ahenk.entries.forEach { a ->
                DropdownMenuItem(
                    text = { Text("${a.title} — ${a.shift}") },
                    onClick = {
                        open = false
                        onSelect(a)
                    },
                )
            }
        }
    }
}

/**
 * How the makam unfolds: direction, durak / güçlü / yeden (also ringed on the board), the usual
 * course, geçkis that move the board to that makam on that perde, and kararlar to play and drill.
 */
@Composable
private fun SeyirPanel(state: TurkishMakamUiState, vm: TurkishMakamViewModel) {
    val seyir = state.seyir
    val makam = state.makam
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Сейир ${seyir.direction.turkish} — ${seyir.direction.title}.   " +
                seyir.path.mapIndexed { i, step -> "${i + 1}. $step" }.joinToString("   →   "),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)) {
                        append("◎ дурак ${state.labelOf(TurkishScale.tone(makam, 0))}")
                    }
                    withStyle(SpanStyle(color = upperJinsColor(), fontWeight = FontWeight.SemiBold)) {
                        append("   ◎ гючлю ${state.labelOf(TurkishScale.guclu(makam))}")
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)) {
                        append("   ◎ йеден ${state.labelOf(state.yeden)}")
                    }
                    append("     ")
                },
                Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.titleSmall,
            )
            val back = state.trail.lastOrNull()
            if (back != null) {
                val name = back.durak?.let { "${back.base.turkishName} на ${Perdes.of(it) ?: "?"}" } ?: back.base.turkishName
                TextButton(onClick = vm::geckiBack) { Text("← назад: $name") }
            }
            val moves = seyir.gecki.filter { TurkishMakams.named(it.makam) != null }
            if (moves.isNotEmpty()) {
                Text("Гечки:", Modifier.align(Alignment.CenterVertically), color = muted)
                moves.forEach { g ->
                    val durak = makam.tonic + g.offset
                    AssistChip(onClick = { vm.gecki(g) }, label = { Text("→ ${g.makam} на ${Perdes.of(durak) ?: perdeOrNote(durak)}") })
                }
            }
        }
        val kararlar = TurkishSeyirs.kararlar(makam)
        if (kararlar.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Карар:", Modifier.align(Alignment.CenterVertically).padding(end = 4.dp), color = muted)
                kararlar.forEach { karar ->
                    val phrase = TurkishScale.phrase(makam, karar).joinToString(" ") { state.labelOf(it) }
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(karar.name) }
                            withStyle(SpanStyle(color = muted)) { append("  $phrase") }
                        },
                        Modifier.align(Alignment.CenterVertically).padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { vm.playKarar(karar) }) { Text("▶") }
                    TextButton(onClick = { vm.startKararDrill(karar) }) { Text("🎯") }
                }
            }
        }
    }
}

/** A written pitch without a perde name, by its nearest natural letter. */
private fun perdeOrNote(written: Int): String {
    val pc = Math.floorMod(written, 53)
    val naturals = listOf(0, 9, 18, 22, 31, 40, 49)
    val letter = naturals.indices.minBy { abs(naturals[it] - pc) }
    return TurkishSpelling(letter, pc - naturals[letter]).label()
}

@Composable
private fun TurkishExercise(state: TurkishMakamUiState, vm: TurkishMakamViewModel) {
    val exercise = state.exercise ?: return
    val steps = state.drillTones
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
                            (worst?.let { " · хуже всего ${state.labelOf(steps[it])} ${signedCents(exercise.results[it])}" } ?: ""),
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else {
                    val what = state.drillKarar?.let { "Карар «${it.name}», нота" } ?: "Нота"
                    Text("$what ${exercise.results.size + 1} из ${exercise.steps.size}:  ", color = muted)
                    Text(
                        "сыграй ${state.labelOf(target)}",
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
            TextButton(onClick = vm::restartExercise) { Text(if (exercise.done) "Ещё раз" else "Заново") }
            TextButton(onClick = vm::stopExercise) { Text("Закончить") }
        }
        Text(
            buildAnnotatedString {
                steps.forEachIndexed { i, tone ->
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
                        append(" ${state.labelOf(tone)}")
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

/** The neck with the makam's tones placed by komas, not on the quarter-tone grid. */
@Composable
private fun TurkishBoardCanvas(state: TurkishMakamUiState, vm: TurkishMakamViewModel, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val maxCents = if (state.extended) Fretboard.EXTENDED_CENTS else Fretboard.NECK_CENTS
    val tones = state.boardTones
    val marks = remember(tones, state.tuning, state.a4, state.ahenk, maxCents) {
        TurkishBoard.marks(tones, state.tuning, state.a4.toDouble(), state.ahenk, maxCents)
    }
    val heard = state.heard?.takeIf { abs(it.cents) <= TurkishMakamUiState.OUTSIDE_CENTS }
    val colors = boardColors(
        if (heard != null && abs(heard.cents) <= TurkishMakamUiState.IN_TUNE_CENTS) InTuneGreen else MaterialTheme.colorScheme.tertiary,
    )
    val makam = state.makam
    val guclu = TurkishScale.guclu(makam)
    val yeden = state.yeden
    val tonic = TurkishScale.tone(makam, 0)
    val inici = TurkishSeyirs.of(makam).inici
    val yedenRing = MaterialTheme.colorScheme.tertiary
    Canvas(
        modifier.pointerInput(marks, state.tuning) {
            detectTapGestures { tap ->
                val g = BoardGeometry(Size(size.width.toFloat(), size.height.toFloat()), state.tuning.courses.size, true, maxCents, this)
                marks.minByOrNull { hypot(g.x(it.cents) - tap.x, g.y(it.course) - tap.y) }
                    ?.takeIf { hypot(g.x(it.cents) - tap.x, g.y(it.course) - tap.y) <= 30.dp.toPx() }
                    ?.let { vm.play(it.tone) }
            }
        },
    ) {
        val g = BoardGeometry(size, state.tuning.courses.size, true, maxCents, this)
        drawBoard(g, colors, measurer)
        marks.forEach { mark ->
            val t = mark.tone
            val highlight = when {
                t.written == state.playing?.written || t.written == state.target?.written -> colors.playing
                heard != null && t.written == heard.tone.written -> colors.heard
                else -> null
            }
            val degree = state.degreeOf(t)
            val fill = when {
                degree == null -> colors.quarter // the yeden, when it isn't a degree
                state.descending && degree in inici -> colors.quarter
                degree != 0 && state.degrees[degree].upper -> colors.upperJins
                else -> colors.natural
            }
            // With the seyir open the güçlü and yeden get a ring, besides the durak's own.
            val ring = when {
                t.sameClass(tonic) -> colors.label
                state.seyirOpen && t.sameClass(guclu) -> colors.upperJins
                state.seyirOpen && t.sameClass(yeden) -> yedenRing
                else -> null
            }
            drawMark(mark, g, state.labelOf(t), fill, ring, t.sameClass(tonic), highlight, colors, measurer)
        }
    }
}

private fun DrawScope.drawMark(
    mark: TurkishMark,
    g: BoardGeometry,
    text: String,
    fill: Color,
    ring: Color?,
    tonic: Boolean,
    highlight: Color?,
    c: BoardColors,
    measurer: TextMeasurer,
) {
    val center = Offset(g.x(mark.cents), g.y(mark.course))
    val radius = 15.dp.toPx() * g.dotScale
    if (highlight != null) drawCircle(highlight.copy(alpha = 0.3f), radius = radius + 12.dp.toPx() * g.dotScale, center = center)
    drawCircle(highlight ?: fill, radius = radius, center = center)
    if (mark.cents == 0.0) drawCircle(c.nut, radius = radius, center = center, style = Stroke(2.dp.toPx()))
    ring?.let { drawCircle(it, radius = radius + 4.dp.toPx() * g.dotScale, center = center, style = Stroke(3.dp.toPx() * g.dotScale)) }
    val label = measurer.measure(
        text,
        TextStyle(fontSize = 11.sp * g.dotScale, fontWeight = FontWeight.SemiBold, color = highlight ?: c.label),
    )
    val gap = (if (ring != null || tonic) 7.dp else 3.dp).toPx() * g.dotScale
    drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y + radius + gap))
}

/** The note in Turkish notation beside the board: sounding, asked for by the drill, or heard. */
@Composable
private fun TurkishStaff(state: TurkishMakamUiState, modifier: Modifier) {
    val heard = state.heard?.takeIf { abs(it.cents) <= TurkishMakamUiState.OUTSIDE_CENTS }
    val tone = state.playing ?: state.target ?: heard?.tone
    val color = when {
        tone == null -> MaterialTheme.colorScheme.onSurfaceVariant
        heard == null || tone != heard.tone -> MaterialTheme.colorScheme.primary
        abs(heard.cents) <= TurkishMakamUiState.IN_TUNE_CENTS -> InTuneGreen
        else -> MaterialTheme.colorScheme.onSurface
    }
    NoteStaff(
        tone?.staffNote(),
        tone?.let(state::labelOf),
        color,
        hint = "турецкая запись; в ${state.ahenk.title} звучит ниже",
        octaveClef = false,
        modifier = modifier,
    )
}
