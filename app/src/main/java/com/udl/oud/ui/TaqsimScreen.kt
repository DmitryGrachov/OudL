@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.udl.oud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.udl.oud.fretboard.StaffNote
import com.udl.oud.taqsim.AnswerLevel
import com.udl.oud.taqsim.AnswerPhase
import com.udl.oud.taqsim.JinsGrammars
import com.udl.oud.taqsim.OrnamentKind
import com.udl.oud.taqsim.OrnamentLevel
import com.udl.oud.taqsim.Station
import com.udl.oud.taqsim.TaqsimLength
import com.udl.oud.taqsim.TaqsimMode
import com.udl.oud.taqsim.TaqsimNote
import com.udl.oud.taqsim.TaqsimTempo
import com.udl.oud.taqsim.TaqsimUiState
import com.udl.oud.taqsim.TaqsimViewModel
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.abs
import kotlin.math.roundToInt

/** The taqsim tab: a study improvisation on the chosen maqam, built by the rules of its sayr. */
@Composable
fun TaqsimRoute(vm: TaqsimViewModel = viewModel()) {
    val mic = rememberMicPermission()
    val state by vm.state.collectAsStateWithLifecycle()
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val staffWidth = if (maxWidth >= 900.dp) 200.dp else 120.dp
        Scaffold(topBar = { if (!wide) TopAppBar(title = { Text("Таксим") }) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 8.dp),
            ) {
                if (state.exercise != null) RepeatBar(state, vm) else TaqsimHeardBar(state, vm, mic.granted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Режим", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
                    TaqsimMode.entries.forEach { m ->
                        FilterChip(state.mode == m, onClick = { vm.setMode(m) }, label = { Text(m.title) })
                    }
                }
                MaqamPicker(state, vm)
                if (state.mode == TaqsimMode.Listen) {
                    TaqsimControls(state, vm)
                    PhraseStrip(state, vm)
                    PhraseDetail(state, vm)
                } else {
                    AnswerPanel(state, vm)
                }
                Row(Modifier.fillMaxWidth().height(if (wide) 360.dp else 300.dp).padding(top = 8.dp)) {
                    TaqsimBoard(state, Modifier.weight(1f).fillMaxHeight())
                    TaqsimStaff(state, Modifier.width(staffWidth).fillMaxHeight().padding(start = 8.dp))
                }
            }
        }
    }
}

private fun noteName(n: TaqsimNote) = n.spelling.label(NoteLabels.Solfege)

@Composable
private fun TaqsimHeardBar(state: TaqsimUiState, vm: TaqsimViewModel, micGranted: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val heard = state.heard
            when {
                state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                state.playing -> Text(
                    "Слушай: ноты подсвечиваются на грифе и на стане",
                    style = MaterialTheme.typography.titleMedium,
                    color = muted,
                )
                !micGranted || !state.live || heard == null -> Text(
                    "Учебный таксим по правилам сайра — каждый раз новый",
                    style = MaterialTheme.typography.titleMedium,
                    color = muted,
                )
                else -> {
                    val inTune = abs(heard.second) <= TaqsimUiState.IN_TUNE_CENTS
                    Text(
                        heard.first.label(NoteLabels.Solfege),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (inTune) InTuneGreen else MaterialTheme.colorScheme.onSurface,
                    )
                    Text("  ${signedCents(heard.second)}", style = MaterialTheme.typography.titleMedium, color = muted)
                }
            }
        }
        Text("Слушать", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = state.live, onCheckedChange = vm::setLive)
    }
}

@Composable
private fun MaqamPicker(state: TaqsimUiState, vm: TaqsimViewModel) {
    val family = Maqamat.families.first { state.maqam in it.scales }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Семейство", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
        Maqamat.families.forEach { f ->
            FilterChip(f == family, onClick = { if (f != family) vm.select(f.scales.first() as com.udl.oud.fretboard.Maqam) }, label = { Text(f.name) })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Макам", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
        family.scales.forEach { m ->
            FilterChip(
                m == state.maqam,
                onClick = { vm.select(m as com.udl.oud.fretboard.Maqam) },
                label = { Text(m.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
        Text(
            if (state.taqsim.detailed) "сайр подробный: центр и переходы" else "сайр базовый: из строения макама",
            Modifier.align(Alignment.CenterVertically).padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaqsimControls(state: TaqsimUiState, vm: TaqsimViewModel) {
    var showScore by remember { mutableStateOf(false) }
    if (showScore) TaqsimScoreDialog(state, vm) { showScore = false }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.playing) {
            Button(onClick = vm::stop) { Text("■ Стоп") }
        } else {
            Button(onClick = vm::playAll) { Text("▶ Слушать таксим") }
        }
        TextButton(onClick = vm::regenerate) { Text("✨ Новый таксим") }
        TextButton(onClick = { showScore = true }) { Text("🎼 Ноты") }
        Text("·", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outline)
        TaqsimLength.entries.forEach { l ->
            FilterChip(state.length == l, onClick = { vm.setLength(l) }, label = { Text(l.title) })
        }
        Text("·", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outline)
        TaqsimTempo.entries.forEach { t ->
            FilterChip(state.tempo == t, onClick = { vm.setTempo(t) }, label = { Text(t.title) })
        }
        Text("·", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outline)
        OrnamentChips(state, vm)
    }
    TaqsimOutline(state)
}

/** Skeleton, or with ornaments: the same phrase, plain or as a player would decorate it. */
@Composable
private fun OrnamentChips(state: TaqsimUiState, vm: TaqsimViewModel) {
    OrnamentLevel.entries.forEach { l ->
        FilterChip(state.ornamentLevel == l, onClick = { vm.setOrnamentLevel(l) }, label = { Text(l.title) })
    }
}

/** The phrases in order, grouped by what they do; the one sounding is lit. */
@Composable
private fun PhraseStrip(state: TaqsimUiState, vm: TaqsimViewModel) {
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        state.taqsim.phrases.forEachIndexed { i, p ->
            val sounding = state.playingPhrase == i
            FilterChip(
                selected = sounding || state.selected == i,
                onClick = { vm.selectPhrase(i) },
                // ♦ marks the phrases where the theme comes back.
                label = { Text("${if (p.themeNotes.isNotEmpty()) "♦ " else ""}${i + 1}. ${p.section.title} · ${p.role.title}") },
                colors = if (sounding) {
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                },
            )
        }
    }
}

/** What the shown phrase is, how it was made, its notes, and play / loop / repeat. */
@Composable
private fun PhraseDetail(state: TaqsimUiState, vm: TaqsimViewModel) {
    val index = state.shownPhrase
    val phrase = state.taqsim.phrases.getOrNull(index) ?: return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val start = state.phraseStarts[index]
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append("Фраза ${index + 1}: ${phrase.section.title.lowercase()}, ${phrase.role.title}")
                }
                withStyle(SpanStyle(color = muted)) { append(" — ${phrase.station.name}. ${phrase.how.replaceFirstChar { it.uppercase() }}.") }
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(JinsGrammars.of(phrase.station.jins).about, style = MaterialTheme.typography.bodyMedium, color = muted)
        val ornaments = state.ornaments
        val here = phrase.notes.indices.mapNotNull { i -> ornaments[start + i]?.describe(noteName(phrase.notes[i])) }
        if (here.isNotEmpty()) {
            Text("Украшения: ${here.joinToString("; ")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    phrase.notes.forEachIndexed { i, n ->
                        val lit = state.playingNote == start + i ||
                            (state.drillPhrase == index && state.exercise?.target == i)
                        val done = state.drillPhrase == index && (state.exercise?.results?.size ?: 0) > i
                        val style = when {
                            lit -> SpanStyle(
                                color = MaterialTheme.colorScheme.onPrimary,
                                background = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            done -> SpanStyle(color = InTuneGreen, fontWeight = FontWeight.SemiBold)
                            // Long notes are the resting points: bold.
                            n.beats >= 2.5 -> SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            else -> SpanStyle(color = MaterialTheme.colorScheme.onSurface)
                        }
                        val o = ornaments[start + i]
                        // The theme's notes are underlined, so it can be followed through the taqsim.
                        val styled = if (i in phrase.themeNotes) style.copy(textDecoration = TextDecoration.Underline) else style
                        withStyle(styled) {
                            append(" ")
                            // A grace note is written small before its note, the other signs after it.
                            if (o?.kind == OrnamentKind.GraceAbove || o?.kind == OrnamentKind.GraceBelow) {
                                append("(${o.neighbourSpelling?.label(NoteLabels.Solfege)})")
                            }
                            append(noteName(n))
                            if (o != null && o.kind != OrnamentKind.GraceAbove && o.kind != OrnamentKind.GraceBelow) append(o.kind.sign)
                            append(" ")
                        }
                    }
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { vm.playPhrase(index) }) { Text("▶ Фраза") }
            FilterChip(state.loop, onClick = vm::toggleLoop, label = { Text("🔁 По кругу") })
            TextButton(onClick = { vm.startRepeat(index) }) { Text("🎯 Повторить") }
        }
    }
}

@Composable
private fun RepeatBar(state: TaqsimUiState, vm: TaqsimViewModel) {
    val exercise = state.exercise ?: return
    val index = state.drillPhrase ?: return
    val phrase = state.taqsim.phrases[index]
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val target = state.target
            if (target == null) {
                Text(
                    "Фраза ${index + 1} сыграна: ${exercise.inTuneCount} из ${exercise.steps.size} точно · " +
                        "средняя ошибка ${exercise.meanError.roundToInt()} ц",
                    style = MaterialTheme.typography.titleLarge,
                )
            } else {
                Text("Повтори фразу ${index + 1}, нота ${exercise.results.size + 1} из ${phrase.notes.size}:  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("сыграй ${noteName(target)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                state.exerciseCents?.let { cents ->
                    Text(
                        when {
                            abs(cents) > 50 -> "   другая нота"
                            abs(cents) <= TaqsimUiState.IN_TUNE_CENTS -> "   ${signedCents(cents)} — держи"
                            cents < 0 -> "   низко на ${abs(cents).roundToInt()} ц"
                            else -> "   высоко на ${cents.roundToInt()} ц"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (abs(cents) <= TaqsimUiState.IN_TUNE_CENTS) InTuneGreen else MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        TextButton(onClick = { vm.playPhrase(index) }) { Text("▶ Послушать") }
        TextButton(onClick = vm::restartRepeat) { Text(if (exercise.done) "Ещё раз" else "Заново") }
        TextButton(onClick = vm::stopRepeat) { Text("Закончить") }
    }
}

/** The neck with the station's scale; the note sounding, asked for or heard is lit. */
@Composable
private fun TaqsimBoard(state: TaqsimUiState, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val maxCents = Fretboard.NECK_CENTS
    val positions = remember(state.tuning) { Fretboard.positions(state.tuning, maxCents) }
    val station: Station = state.taqsim.phrases[state.shownPhrase].station
    // Pitch classes of the station's scale, with the degree each belongs to.
    val degreeOfClass = remember(station) { (0 until 7).associateBy { station.step(it).mod(24) } }
    val heard = state.heard?.takeIf { abs(it.second) <= 40 }
    val colors = boardColors(if (heard != null && abs(heard.second) <= TaqsimUiState.IN_TUNE_CENTS) InTuneGreen else MaterialTheme.colorScheme.tertiary)
    val lit = state.playingTone?.step ?: state.target?.step
    val jinsClasses = remember(station) { station.jinsDegrees.map { station.step(it).mod(24) }.toSet() }
    Canvas(modifier) {
        val g = BoardGeometry(size, state.tuning.courses.size, true, maxCents, this)
        drawBoard(g, colors, measurer)
        positions.forEach { p ->
            val degree = degreeOfClass[p.pitch.step.mod(24)] ?: return@forEach
            val center = g.center(p)
            val radius = 13.dp.toPx() * g.dotScale
            val highlight = when {
                p.pitch.step == lit -> colors.playing
                heard != null && p.pitch.step == heard.first.step -> colors.heard
                else -> null
            }
            if (highlight != null) drawCircle(highlight.copy(alpha = 0.3f), radius = radius + 12.dp.toPx() * g.dotScale, center = center)
            val inJins = p.pitch.step.mod(24) in jinsClasses
            drawCircle(highlight ?: if (inJins) colors.upperJins else colors.natural, radius = radius, center = center)
            if (p.cents == 0) drawCircle(colors.nut, radius = radius, center = center, style = Stroke(2.dp.toPx()))
            if (degree == station.focus.mod(7)) {
                drawCircle(colors.label, radius = radius + 4.dp.toPx() * g.dotScale, center = center, style = Stroke(3.dp.toPx() * g.dotScale))
            }
            val label = measurer.measure(
                station.spelling(degree).label(NoteLabels.Solfege),
                TextStyle(fontSize = 11.sp * g.dotScale, fontWeight = FontWeight.SemiBold, color = highlight ?: colors.label),
            )
            drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y + radius + 3.dp.toPx() * g.dotScale))
        }
    }
}

@Composable
private fun TaqsimStaff(state: TaqsimUiState, modifier: Modifier) {
    val tone = state.playingTone ?: state.target
    val heard = state.heard?.takeIf { tone == null && abs(it.second) <= 40 }
    val note = tone?.let { StaffNote.arabic(it.step, it.spelling) } ?: heard?.let { StaffNote.arabic(it.first.step) }
    val name = tone?.let(::noteName) ?: heard?.first?.label(NoteLabels.Solfege)
    val color: Color = when {
        tone != null -> MaterialTheme.colorScheme.primary
        heard != null && abs(heard.second) <= TaqsimUiState.IN_TUNE_CENTS -> InTuneGreen
        else -> MaterialTheme.colorScheme.onSurface
    }
    NoteStaff(note, name, color, hint = "записано октавой выше звука", octaveClef = true, modifier = modifier)
}


/**
 * Answer mode: the app plays a question; the player answers with a phrase of their own that
 * comes to rest on the asked note; then the four checks and the app's own answer to compare.
 */
@Composable
private fun AnswerPanel(state: TaqsimUiState, vm: TaqsimViewModel) {
    val q = state.question ?: return
    val a = state.answer ?: return
    val question = state.taqsim.phrases[q]
    val answer = state.taqsim.phrases[a]
    val station = answer.station
    val grammar = JinsGrammars.of(station.jins)
    val target = answer.notes.last()
    val colour = grammar.characteristic.map { station.spelling(it + station.jinsRoot).label(NoteLabels.Solfege) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Вопрос", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
            AnswerLevel.entries.forEach { l ->
                FilterChip(state.answerLevel == l, onClick = { vm.setAnswerLevel(l) }, label = { Text(l.title) })
            }
            Text("·", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outline)
            TaqsimTempo.entries.forEach { t ->
                FilterChip(state.tempo == t, onClick = { vm.setTempo(t) }, label = { Text(t.title) })
            }
            Text("·", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outline)
            OrnamentChips(state, vm)
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append("Задача: ответь фразой в джинсе ${station.jins.name} и приди на ${noteName(target)}.")
                }
                withStyle(SpanStyle(color = muted)) {
                    append("  Вопрос стоит на ${question.station.name} и останавливается на ${noteName(question.notes.last())}. ")
                    append("Цветная ступень: ${colour.joinToString()}. ${grammar.about}")
                }
            },
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.playing -> Button(onClick = vm::stop) { Text("■ Стоп") }
                state.phase == AnswerPhase.Listening -> Button(onClick = vm::finishAnswer) { Text("✋ Готово") }
                else -> Button(onClick = vm::playQuestion) {
                    Text(if (state.phase == AnswerPhase.Idle) "▶ Задать вопрос" else "▶ Вопрос ещё раз")
                }
            }
            TextButton(onClick = vm::playExample, enabled = !state.playing) { Text("💡 Пример ответа") }
            TextButton(onClick = { vm.nextQuestion() }) { Text("➡ Следующий вопрос") }
        }
        Text(
            when (state.phase) {
                AnswerPhase.Idle -> "Нажми «Задать вопрос», послушай — и отвечай на уде."
                AnswerPhase.Question -> "Слушай вопрос…"
                AnswerPhase.Listening -> "Твоя очередь: играй ответ. После паузы я его разберу (или нажми «Готово»)."
                AnswerPhase.Done -> "Разбор ответа:"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (state.phase == AnswerPhase.Listening) MaterialTheme.colorScheme.primary else muted,
        )
        // The answer so far, each note green in the maqam, orange outside it.
        if (state.answerNotes.isNotEmpty()) {
            val verdict = state.verdict
            Text(
                buildAnnotatedString {
                    state.answerNotes.forEachIndexed { i, n ->
                        val outside = verdict?.outside?.contains(i) ?: false
                        withStyle(
                            SpanStyle(
                                color = if (outside) MaterialTheme.colorScheme.tertiary else InTuneGreen,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) { append(" ${Pitch(n.step).label(NoteLabels.Solfege)} ") }
                    }
                },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        state.verdict?.let { v ->
            v.points.forEach { p ->
                Text(
                    "${if (p.passed) "✓" else "✗"}  ${p.text}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (p.passed) InTuneGreen else MaterialTheme.colorScheme.tertiary,
                )
            }
            if (v.points.size == 4) {
                Text(
                    if (v.passed == 4) "Отличный ответ! Попробуй следующий вопрос или ответь иначе." else "${v.passed} из 4. Послушай пример и попробуй ещё раз.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** The taqsim as a whole: the form of its course and the theme, with where it comes back. */
@Composable
private fun TaqsimOutline(state: TaqsimUiState) {
    val t = state.taqsim
    val theme = t.theme
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Форма: ") }
            append(t.form.title)
            if (theme != null && t.themePhrases.isNotEmpty()) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("   ·   ♦ Тема: ") }
                append(theme.name)
                append(" — во фразах ${t.themePhrases.joinToString { (it + 1).toString() }}, ноты темы подчёркнуты")
            }
        },
        Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
