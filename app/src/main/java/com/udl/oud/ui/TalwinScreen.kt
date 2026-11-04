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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
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
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Pitch
import com.udl.oud.taqsim.AnswerPhase
import com.udl.oud.taqsim.SectionKind
import com.udl.oud.taqsim.TaqsimSayrs
import com.udl.oud.talwin.Talwin
import com.udl.oud.talwin.TalwinMode
import com.udl.oud.talwin.TalwinUiState
import com.udl.oud.talwin.TalwinViewModel
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.abs
import kotlin.math.log2

/** The talwin mode of the maqam tab: modulations, what they change, and practice in them. */
@Composable
fun TalwinRoute(vm: TalwinViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val mic = rememberMicPermission()
    if (mic.granted && state.live) {
        LifecycleResumeEffect(Unit) {
            @Suppress("MissingPermission")
            vm.start()
            onPauseOrDispose {
                vm.stopListening()
                vm.stop()
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Тальвин", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
                        TalwinMode.entries.forEach { m -> FilterChip(state.mode == m, onClick = { vm.setMode(m) }, label = { Text(m.title) }) }
                    }
                    Text("Слушать", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                    Switch(checked = state.live, onCheckedChange = vm::setLive)
                }
                if (state.mode == TalwinMode.Quiz) {
                    QuizPanel(state, vm)
                } else {
                    MaqamAndMove(state, vm)
                    Explanation(state)
                    BeforeAfter(state, vm)
                    if (state.mode == TalwinMode.Answer) AnswerPanel(state, vm) else Phrases(state, vm)
                }
                TalwinBoard(state, Modifier.fillMaxWidth().height(if (wide) 330.dp else 280.dp).padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun MaqamAndMove(state: TalwinUiState, vm: TalwinViewModel) {
    val with = Talwin.maqamatWithMoves
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Из макама", Modifier.align(Alignment.CenterVertically).width(96.dp), style = MaterialTheme.typography.labelLarge)
        Maqamat.families.flatMap { f -> f.scales }.filter { it in with }.forEach { m ->
            FilterChip(state.maqam == m, onClick = { vm.selectMaqam(m as com.udl.oud.fretboard.Maqam) }, label = { Text(m.name) })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Переход", Modifier.align(Alignment.CenterVertically).width(96.dp), style = MaterialTheme.typography.labelLarge)
        Talwin.of(state.maqam).forEachIndexed { i, t ->
            FilterChip(
                state.move == i,
                onClick = { vm.selectMove(i) },
                label = { Text("→ ${t.title}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun Explanation(state: TalwinUiState) {
    val lines = state.talwin.explanation
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${state.talwin.from.name} → ${state.talwin.title}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        lines.forEachIndexed { i, l ->
            Text(
                l,
                style = if (i == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (i == 1) FontWeight.SemiBold else FontWeight.Normal,
                color = if (i == 1 && state.talwin.changes.isNotEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The same notes before and after: the maqam's, and the new jins' on them, the changed ones marked. */
@Composable
private fun BeforeAfter(state: TalwinUiState, vm: TalwinViewModel) {
    val t = state.talwin
    val n = t.target.lower.intervals.size
    val home = remember(t) { TaqsimSayrs.Stations(t.from, t.tonic).lower }
    val changed = t.changes.map { it.to.label(NoteLabels.Solfege) }.toSet()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = muted)) { append("Было (${t.from.name}):  ") }
                    (t.pivot..t.pivot + n).forEach { d -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(" ${home.label(d)} ") } }
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = muted)) { append("Стало (${t.target.lower.name}):  ") }
                    (0..n).forEach { d ->
                        val name = t.station.label(d)
                        val style = if (name in changed) SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        else SpanStyle(fontWeight = FontWeight.SemiBold)
                        withStyle(style) { append(" $name ") }
                    }
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        TextButton(onClick = vm::playBefore, enabled = !state.playing) { Text("▶ Было") }
        TextButton(onClick = vm::playAfter, enabled = !state.playing) { Text("▶ Стало") }
    }
}

/** The move as a short taqsim: home, the way to the pivot, the new jins, the way back. */
@Composable
private fun Phrases(state: TalwinUiState, vm: TalwinViewModel) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Переход в таксиме", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (state.playing) Button(onClick = vm::stop) { Text("■ Стоп") } else FilledTonalButton(onClick = vm::playAll) { Text("▶ Весь переход") }
        TextButton(onClick = vm::regenerate) { Text("✨ Другой вариант") }
    }
    var start = 0
    state.taqsim.phrases.forEachIndexed { i, p ->
        val from = start
        start += p.notes.size
        val inMove = p.section == SectionKind.Modulation
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                state.playingPhrase == i -> MaterialTheme.colorScheme.tertiary
                                inMove -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        ),
                    ) { append("${i + 1}. ${p.section.title} · ${p.station.name}: ") }
                    p.notes.forEachIndexed { k, n ->
                        val lit = state.playingNote == from + k
                        val style = if (lit) SpanStyle(color = MaterialTheme.colorScheme.onPrimary, background = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        else SpanStyle(color = muted)
                        withStyle(style) { append(" ${n.name} ") }
                    }
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = { vm.playPhrase(i) }) { Text("▶") }
        }
    }
}

/** Answer: the app leads home and to the pivot; the player makes the move and comes to rest on the new root. */
@Composable
private fun AnswerPanel(state: TalwinUiState, vm: TalwinViewModel) {
    val t = state.talwin
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Задача: послушай, как макам доходит до ${t.where.substringAfter("(").removeSuffix(")")}, и сделай переход сам — " +
                "фразой в ${t.target.lower.name}" + (if (t.changes.isNotEmpty()) " с ${t.changes.joinToString { it.to.label(NoteLabels.Solfege) }}" else "") +
                ", и приди на ${t.station.label(0)}.",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.phase == AnswerPhase.Question -> Button(onClick = vm::stop) { Text("■ Стоп") }
                state.phase == AnswerPhase.Listening -> Button(onClick = vm::finishAnswer) { Text("✋ Готово") }
                else -> Button(onClick = vm::playQuestion) { Text(if (state.phase == AnswerPhase.Idle) "▶ Подвести к переходу" else "▶ Ещё раз") }
            }
            TextButton(onClick = vm::playExample, enabled = !state.playing) { Text("💡 Как сделал бы я") }
            TextButton(onClick = vm::regenerate) { Text("✨ Другой вариант") }
        }
        if (state.phase == AnswerPhase.Listening) {
            Text("Твоя очередь: переходи! После паузы я разберу.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        }
        if (state.answerNotes.isNotEmpty()) {
            Text(
                buildAnnotatedString {
                    state.answerNotes.forEachIndexed { i, n ->
                        val outside = state.verdict?.outside?.contains(i) ?: false
                        withStyle(SpanStyle(color = if (outside) MaterialTheme.colorScheme.tertiary else InTuneGreen, fontWeight = FontWeight.SemiBold)) {
                            append(" ${Pitch(n.step).label(NoteLabels.Solfege)} ")
                        }
                    }
                },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        state.verdict?.points?.forEach { p ->
            Text(
                "${if (p.passed) "✓" else "✗"}  ${p.text}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (p.passed) InTuneGreen else MaterialTheme.colorScheme.tertiary,
            )
        }
        if (state.phase == AnswerPhase.Idle) {
            Text("Проверка по нотам нового джинса: «вне макама» здесь — вне ${t.target.lower.name}.", style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

/** Where did it go: a taqsim leaves home, the player names the jins it went to. */
@Composable
private fun QuizPanel(state: TalwinUiState, vm: TalwinViewModel) {
    val q = state.quiz ?: return
    Text("Из ${q.answer.from.name} — куда ушли?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "Слушай, где сменилась опора и какая нота стала другой. Счёт: ${q.right} из ${q.asked - if (q.picked == null) 1 else 0}",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.playing) Button(onClick = vm::stop) { Text("■ Стоп") } else FilledTonalButton(onClick = vm::playQuizMove) { Text("▶ Ещё раз") }
        TextButton(onClick = vm::nextQuiz) { Text("➡ Следующий") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        q.options.forEach { o ->
            val shown = q.picked != null
            val color = when {
                shown && o == q.answer.title -> InTuneGreen
                shown && o == q.picked -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            Button(onClick = { vm.pickQuiz(o) }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.heightIn(min = 56.dp)) {
                Text(o, fontSize = 18.sp)
            }
        }
    }
    if (q.picked != null) {
        Text(
            (if (q.picked == q.answer.title) "Верно! " else "Это ${q.answer.title}. ") + q.answer.explanation.drop(1).joinToString(" "),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private data class TalwinDot(val course: Int, val cents: Double, val hz: Double, val label: String, val kind: Int)

/**
 * The neck with the maqam and the move over it: the maqam's notes faint, the new jins bold, its
 * changed notes in their own colour, its root ringed; the note sounding or heard is lit.
 */
@Composable
private fun TalwinBoard(state: TalwinUiState, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val maxCents = Fretboard.NECK_CENTS
    val t = state.talwin
    val a4 = state.a4.toDouble()
    val dots = remember(t, state.tuning, a4) {
        val home = TaqsimSayrs.Stations(t.from, t.tonic).lower
        val n = t.target.lower.intervals.size
        val jinsPcs = (0..n).associateBy { Math.floorMod(t.station.step(it), 24) }
        val changed = t.changes.map { it.to }.toSet()
        fun place(hz440: Double, label: String, kind: Int) = state.tuning.courses.mapIndexedNotNull { course, open ->
            val hz = hz440 * state.pitchScale
            var cents = 1200 * log2(hz / open.frequency(a4))
            if (abs(cents) < 12) cents = 0.0
            if (cents < 0 || cents > maxCents) null else TalwinDot(course, cents, hz, label, kind)
        }
        // kind: 0 the maqam's note outside the jins, 1 the jins' own note, 2 a changed note, 3 the root.
        val homeDots = (-10..17).filter { Math.floorMod(home.step(it), 24) !in jinsPcs }.flatMap { place(home.hz(it), home.label(it), 0) }
        val jinsDots = (-14..21).filter { Math.floorMod(it, 7) <= n }.flatMap { d ->
            val k = Math.floorMod(d, 7)
            val kind = when {
                k == 0 -> 3
                t.station.spelling(k) in changed -> 2
                else -> 1
            }
            place(t.station.hz(d), t.station.label(d), kind)
        }
        homeDots + jinsDots
    }
    val heard = state.frequency
    val colors = boardColors(MaterialTheme.colorScheme.tertiary)
    val changedColor = MaterialTheme.colorScheme.tertiary
    fun near(a: Double, b: Double, c: Double) = abs(1200 * log2(a / b)) <= c
    Canvas(modifier) {
        val g = BoardGeometry(size, state.tuning.courses.size, true, maxCents, this)
        drawBoard(g, colors, measurer)
        dots.forEach { p ->
            val center = Offset(g.x(p.cents), g.y(p.course))
            val radius = (if (p.kind == 0) 9.dp else 14.dp).toPx() * g.dotScale
            val highlight = when {
                state.playingHz != null && near(p.hz, state.playingHz, 5.0) -> colors.playing
                heard != null && near(p.hz, heard, 35.0) -> colors.heard
                else -> null
            }
            if (highlight != null) drawCircle(highlight.copy(alpha = 0.3f), radius = radius + 12.dp.toPx() * g.dotScale, center = center)
            val fill = highlight ?: when (p.kind) {
                0 -> colors.accidental
                2 -> changedColor
                else -> colors.upperJins
            }
            drawCircle(fill, radius = radius, center = center)
            if (p.kind == 3) drawCircle(colors.label, radius = radius + 4.dp.toPx() * g.dotScale, center = center, style = Stroke(3.dp.toPx() * g.dotScale))
            val label = measurer.measure(
                p.label,
                TextStyle(fontSize = (if (p.kind == 0) 9.sp else 11.sp) * g.dotScale, fontWeight = FontWeight.SemiBold, color = highlight ?: colors.label),
            )
            drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y + radius + 3.dp.toPx() * g.dotScale))
        }
    }
}
