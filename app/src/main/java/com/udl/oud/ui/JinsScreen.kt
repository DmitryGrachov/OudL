@file:OptIn(ExperimentalLayoutApi::class)

package com.udl.oud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Pitch
import com.udl.oud.fretboard.Spelling
import com.udl.oud.fretboard.StaffNote
import com.udl.oud.fretboard.intervalLabel
import com.udl.oud.jins.Appearance
import com.udl.oud.jins.JinsCatalog
import com.udl.oud.jins.JinsUiState
import com.udl.oud.jins.JinsViewModel
import com.udl.oud.taqsim.AnswerPhase
import com.udl.oud.taqsim.JinsGrammars
import com.udl.oud.taqsim.Motif
import com.udl.oud.turkish.Perdes
import com.udl.oud.turkish.TurkishGrammar
import com.udl.oud.turkish.TurkishJinsCatalog
import com.udl.oud.turkish.TurkishMakam
import com.udl.oud.turkish.komaStepLabel
import com.udl.oud.turkish.nameIn
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * The jins mode of the maqam tab: study one jins on its own. [onOpenMaqam] goes to a maqam that
 * uses it, [onOpenMakam] to a Turkish makam on a Turkish oud.
 */
@Composable
fun JinsRoute(onOpenMakam: (TurkishMakam) -> Unit, onOpenMaqam: (Maqam) -> Unit, vm: JinsViewModel = viewModel()) {
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
    // A surface of its own, so text takes the theme's colours outside a Scaffold.
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val staffWidth = if (maxWidth >= 900.dp) 200.dp else 120.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 8.dp),
        ) {
            if (state.exercise != null) MotifDrillBar(state, vm) else JinsHeardBar(state, vm, mic.granted)
            JinsPicker(state, vm)
            JinsCharacter(state, vm)
            EtudePanel(state, vm)
            JinsAnswerPanel(state, vm)
            MotifList(state, vm)
            if (state.turkish) WhereItAppearsTurkish(state, onOpenMakam) else WhereItAppears(state, onOpenMaqam)
            Row(Modifier.fillMaxWidth().height(if (wide) 360.dp else 300.dp).padding(top = 8.dp)) {
                JinsBoard(state, vm, Modifier.weight(1f).fillMaxHeight())
                JinsStaff(state, Modifier.width(staffWidth).fillMaxHeight().padding(start = 8.dp))
            }
        }
    }
    }
}

private fun JinsUiState.name(degree: Int) = if (turkish) station.nameIn(labels, ahenk, degree) else station.label(degree)
/** A note of the etude, named in the chosen style on a Turkish oud. */
private fun JinsUiState.noteName(station: com.udl.oud.taqsim.Station, n: com.udl.oud.taqsim.TaqsimNote) =
    if (turkish) station.nameIn(labels, ahenk, n.degree) else n.name
private fun rootName(step: Int) = Spelling.of(step).label(NoteLabels.Solfege)
private fun perdeName(written: Int) = Perdes.of(written) ?: written.toString()

/** A step of the jins as written: tones and quarter tones on the Arabic oud, komas (T, B, S…) on the Turkish one. */
private fun JinsUiState.stepLabel(step: Int) = if (turkish) "${komaStepLabel(step)} $step" else intervalLabel(step)

/** The line about the jins: the Turkish jins' own on a Turkish oud. */
private fun JinsUiState.about() = if (turkish) TurkishGrammar.about(tjins.name) else grammar.about

/** A note the player played: on a Turkish oud the nearest degree, otherwise its quarter-tone name. */
private fun JinsUiState.playedName(n: com.udl.oud.taqsim.HeardNote) =
    if (turkish) name(station.degreeOfHz(n.hz / pitchScale).first) else Pitch(n.step).label(NoteLabels.Solfege)

@Composable
private fun JinsHeardBar(state: JinsUiState, vm: JinsViewModel, micGranted: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val heard = state.heard
            when {
                state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                !micGranted || !state.live || heard == null -> Text(
                    "Джинс — кирпичик макама: 3–5 нот со своим цветом. Нажми на точку или сыграй",
                    style = MaterialTheme.typography.titleMedium,
                    color = muted,
                )
                state.turkish -> {
                    // On a Turkish oud: the nearest perde of the jins' scale, and how far off.
                    val (degree, cents) = state.heardDegree ?: return@Row
                    val inTune = abs(cents) <= JinsUiState.IN_TUNE_CENTS
                    val inJins = Math.floorMod(degree, 7) in state.degrees
                    Text(
                        state.name(degree),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (inTune) InTuneGreen else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "  ${signedCents(cents)}" + if (!inJins) "  · рядом с джинсом" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (inTune) InTuneGreen else MaterialTheme.colorScheme.tertiary,
                    )
                }
                else -> {
                    val inTune = abs(heard.second) <= JinsUiState.IN_TUNE_CENTS
                    val degree = state.degreeOf(heard.first.step)
                    Text(
                        degree?.let(state::name) ?: heard.first.label(NoteLabels.Solfege),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (inTune) InTuneGreen else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "  ${signedCents(heard.second)}" + if (degree == null) "  · не из джинса" else "",
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

@Composable
private fun JinsPicker(state: JinsUiState, vm: JinsViewModel) {
    if (state.turkish) {
        TurkishJinsPicker(state, vm)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Джинс", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
        JinsCatalog.ajnas.forEach { j ->
            FilterChip(
                j == state.jins,
                onClick = { vm.select(j) },
                label = { Text(j.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
    // Roots it is found on in the maqamat, and free moving by half a tone.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("На ноте", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
        JinsCatalog.roots(state.jins).forEach { r ->
            FilterChip(Math.floorMod(r - state.root, 24) == 0, onClick = { vm.setRoot(r) }, label = { Text(rootName(r)) })
        }
        TextButton(onClick = { vm.shiftRoot(-2) }) { Text("−½ тона") }
        TextButton(onClick = { vm.shiftRoot(2) }) { Text("+½ тона") }
    }
}

/** On a Turkish oud: dörtlü, beşli and üçlü, on the perdes the makams put them on. */
@Composable
private fun TurkishJinsPicker(state: JinsUiState, vm: JinsViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Джинс", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
        TurkishJinsCatalog.ajnas.forEach { j ->
            FilterChip(
                j == state.tjins,
                onClick = { vm.selectTurkish(j) },
                label = { Text(j.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("На перде", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
        TurkishJinsCatalog.roots(state.tjins).forEach { r ->
            FilterChip(r == state.durak, onClick = { vm.setDurak(r) }, label = { Text(perdeName(r)) })
        }
        Text(
            "ахенк ${state.ahenk.title}",
            Modifier.align(Alignment.CenterVertically).padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TurkishLabelChips(state.labels)
}

/** Notes, intervals, colour, pillars and how the root is reached. */
@Composable
private fun JinsCharacter(state: JinsUiState, vm: JinsViewModel) {
    val g = state.grammar
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val colour = MaterialTheme.colorScheme.tertiary
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    state.degrees.forEach { d ->
                        val lit = state.playing == d || state.target == d
                        val style = when {
                            lit -> SpanStyle(color = MaterialTheme.colorScheme.onPrimary, background = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            d in g.characteristic -> SpanStyle(color = colour, fontWeight = FontWeight.Bold)
                            d in g.rests -> SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            else -> SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        }
                        withStyle(style) { append(" ${state.name(d)} ") }
                        state.intervals.getOrNull(d)?.let { withStyle(SpanStyle(color = muted)) { append(" ${state.stepLabel(it)} ") } }
                    }
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = vm::playScale) { Text("▶ Звукоряд") }
        }
        Text(state.about(), style = MaterialTheme.typography.bodyLarge)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colour, fontWeight = FontWeight.SemiBold)) {
                    append("Цветная ступень: ${g.characteristic.sorted().joinToString { state.name(it) }}")
                }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                    append("   ·   опоры: ${g.rests.sorted().joinToString { state.name(it) }}")
                }
                append(
                    "   ·   к основе обычно приходят " + when {
                        g.fromAbove >= 0.6 -> "сверху, с ${state.name(1)}"
                        g.fromAbove <= 0.4 -> "снизу, с ${state.name(-1)}"
                        else -> "и сверху, и снизу"
                    },
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
    }
}

/** The jins' vocabulary: its own motifs, and the shared shapes on request. */
@Composable
private fun MotifList(state: JinsUiState, vm: JinsViewModel) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Мотивы джинса", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            FilterChip(state.showCommon, onClick = vm::toggleCommon, label = { Text("и общие формы") })
        }
        val motifs = state.grammar.motifs + if (state.showCommon) JinsGrammars.common else emptyList()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            motifs.forEach { m -> MotifChip(state, vm, m, muted) }
        }
    }
}

@Composable
private fun MotifChip(state: JinsUiState, vm: JinsViewModel, m: Motif, muted: androidx.compose.ui.graphics.Color) {
    val sounding = state.playing != null && state.playing in m.degrees
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = if (sounding) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)) {
                    append(m.name)
                }
                withStyle(SpanStyle(color = muted)) { append("  " + m.degrees.joinToString(" ") { state.name(it) }) }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = { vm.playMotif(m) }) { Text("▶") }
        TextButton(onClick = { vm.startMotifDrill(m) }) { Text("🎯") }
    }
}

/** Every maqam that uses the jins, and how; a tap opens the maqam. */
@Composable
private fun WhereItAppears(state: JinsUiState, onOpenMaqam: (Maqam) -> Unit) {
    val list = JinsCatalog.appearances(state.jins)
    if (list.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Где встречается", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { a ->
                val text = when (a.where) {
                    Appearance.Where.Move -> "${a.maqam.name} → ${a.via?.name} на ${rootName(a.root)}"
                    else -> "${a.maqam.name}: ${a.where.title}, ${rootName(a.root)}"
                }
                AssistChip(onClick = { onOpenMaqam(a.maqam) }, label = { Text(text) })
            }
        }
    }
}

/** Every Turkish makam that uses the jins, below on its durak or above; a tap opens the makam. */
@Composable
private fun WhereItAppearsTurkish(state: JinsUiState, onOpenMakam: (TurkishMakam) -> Unit) {
    val list = TurkishJinsCatalog.appearances(state.tjins)
    if (list.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Где встречается", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { a ->
                val where = if (a.upper) "наверху" else "внизу, на дураке"
                AssistChip(onClick = { onOpenMakam(a.makam) }, label = { Text("${a.makam.turkishName}: $where, ${perdeName(a.durak)}") })
            }
        }
    }
}

@Composable
private fun MotifDrillBar(state: JinsUiState, vm: JinsViewModel) {
    val exercise = state.exercise ?: return
    val motif = state.drillMotif ?: return
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val target = state.target
            if (target == null) {
                Text(
                    "«${motif.name}»: ${exercise.inTuneCount} из ${exercise.steps.size} точно · средняя ошибка ${exercise.meanError.roundToInt()} ц",
                    style = MaterialTheme.typography.titleLarge,
                )
            } else {
                val name = state.name(target)
                Text("«${motif.name}», нота ${exercise.results.size + 1} из ${exercise.steps.size}:  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("сыграй $name", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                state.frequency?.let { f ->
                    val cents = com.udl.oud.tuner.centsBetween(f, state.hz(target))
                    Text(
                        when {
                            abs(cents) > 50 -> "   другая нота"
                            abs(cents) <= JinsUiState.IN_TUNE_CENTS -> "   ${signedCents(cents)} — держи"
                            cents < 0 -> "   низко на ${abs(cents).roundToInt()} ц"
                            else -> "   высоко на ${cents.roundToInt()} ц"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (abs(cents) <= JinsUiState.IN_TUNE_CENTS) InTuneGreen else MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        TextButton(onClick = { vm.playMotif(motif) }) { Text("▶ Послушать") }
        TextButton(onClick = vm::restartDrill) { Text(if (exercise.done) "Ещё раз" else "Заново") }
        TextButton(onClick = vm::stopDrill) { Text("Закончить") }
    }
}

/** One place of a degree on the neck: course, cents above the open string, degree, frequency. */
private data class JinsDot(val course: Int, val cents: Double, val degree: Int, val hz: Double)

/**
 * The neck with the jins: its notes bold, the colour note in its own colour, the notes around it
 * faint. Dots are placed by frequency, so quarter tones and komas sit where they are played.
 */
@Composable
private fun JinsBoard(state: JinsUiState, vm: JinsViewModel, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val maxCents = Fretboard.NECK_CENTS
    val top = state.intervals.size
    val station = state.station
    val a4 = state.a4.toDouble()
    val dots = remember(station, state.tuning, a4, state.pitchScale) {
        state.tuning.courses.flatMapIndexed { course, open ->
            val openHz = open.frequency(a4)
            (-10..17).mapNotNull { d ->
                val hz = station.hz(d) * state.pitchScale
                var cents = 1200 * log2(hz / openHz)
                if (abs(cents) < 12) cents = 0.0
                if (cents < 0 || cents > maxCents) null else JinsDot(course, cents, d, hz)
            }
        }
    }
    val heardHz = state.frequency
    val heardInTune = state.heardDegree?.let { abs(it.second) <= JinsUiState.IN_TUNE_CENTS } ?: false
    val colors = boardColors(if (heardInTune) InTuneGreen else MaterialTheme.colorScheme.tertiary)
    val colourFill = MaterialTheme.colorScheme.tertiary
    val litHz = (state.playing ?: state.target)?.let(state::hz)
    fun near(a: Double, b: Double, cents: Double) = abs(1200 * log2(a / b)) <= cents
    Canvas(
        modifier.pointerInput(dots, state.tuning) {
            detectTapGestures { tap ->
                val g = BoardGeometry(Size(size.width.toFloat(), size.height.toFloat()), state.tuning.courses.size, true, maxCents, this)
                fun dist(p: JinsDot) = hypot(g.x(p.cents) - tap.x, g.y(p.course) - tap.y)
                dots.minByOrNull(::dist)?.takeIf { dist(it) <= 30.dp.toPx() }?.let { vm.playNote(it.degree) }
            }
        },
    ) {
        val g = BoardGeometry(size, state.tuning.courses.size, true, maxCents, this)
        drawBoard(g, colors, measurer)
        dots.forEach { p ->
            val k = Math.floorMod(p.degree, 7)
            val inJins = k in 0..top
            val center = Offset(g.x(p.cents), g.y(p.course))
            val radius = (if (inJins) 14.dp else 9.dp).toPx() * g.dotScale
            val highlight = when {
                litHz != null && near(p.hz, litHz, 5.0) -> colors.playing
                heardHz != null && near(p.hz, heardHz, 35.0) -> colors.heard
                else -> null
            }
            if (highlight != null) drawCircle(highlight.copy(alpha = 0.3f), radius = radius + 12.dp.toPx() * g.dotScale, center = center)
            val fill = when {
                highlight != null -> highlight
                !inJins -> colors.accidental // the notes around the jins, muted
                k in state.grammar.characteristic -> colourFill
                else -> colors.natural
            }
            drawCircle(fill, radius = radius, center = center)
            if (p.cents == 0.0) drawCircle(colors.nut, radius = radius, center = center, style = Stroke(2.dp.toPx()))
            if (k == 0) {
                drawCircle(colors.label, radius = radius + 4.dp.toPx() * g.dotScale, center = center, style = Stroke(3.dp.toPx() * g.dotScale))
            }
            val label = measurer.measure(
                state.name(p.degree),
                TextStyle(fontSize = (if (inJins) 11.sp else 9.sp) * g.dotScale, fontWeight = FontWeight.SemiBold, color = highlight ?: colors.label),
            )
            drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y + radius + 3.dp.toPx() * g.dotScale))
        }
    }
}

@Composable
private fun JinsStaff(state: JinsUiState, modifier: Modifier) {
    val degree = state.playing ?: state.target
    val color = when {
        degree != null -> MaterialTheme.colorScheme.primary
        (state.heardDegree?.second?.let(::abs) ?: 99.0) <= JinsUiState.IN_TUNE_CENTS -> InTuneGreen
        else -> MaterialTheme.colorScheme.onSurface
    }
    val hint = "записано октавой выше звука"
    if (degree != null) {
        NoteStaff(state.station.staff(degree), state.name(degree), color, hint = hint, octaveClef = true, modifier = modifier)
        return
    }
    if (state.turkish) {
        // A heard note, written as the nearest degree of the jins' scale.
        val heard = state.heardDegree?.takeIf { abs(it.second) <= 40 }
        NoteStaff(heard?.let { state.station.staff(it.first) }, heard?.let { state.name(it.first) }, color, hint = hint, octaveClef = true, modifier = modifier)
        return
    }
    val heard = state.heard?.takeIf { abs(it.second) <= 40 }
    val shown = heard?.first?.step
    val d = shown?.let(state::degreeOf)
    val note = shown?.let { s -> d?.let { StaffNote.arabic(s, state.station.spelling(it)) } ?: StaffNote.arabic(s) }
    val name = shown?.let { s -> d?.let(state::name) ?: Pitch(s).label(NoteLabels.Solfege) }
    NoteStaff(note, name, color, hint = hint, octaveClef = true, modifier = modifier)
}

/** A short taqsim inside the jins: listen to it phrase by phrase, the sounding note lit. */
@Composable
private fun EtudePanel(state: JinsUiState, vm: JinsViewModel) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val etude = state.etude
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Этюд в джинсе — маленький таксим на ${state.intervals.size + 1} нотах",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (state.etudeNote != null && state.phase != AnswerPhase.Question) {
                TextButton(onClick = vm::stop) { Text("■ Стоп") }
            } else {
                TextButton(onClick = vm::playEtude) { Text("▶ Слушать этюд") }
            }
            TextButton(onClick = vm::newEtude) { Text("✨ Новый этюд") }
        }
        var start = 0
        etude.phrases.forEachIndexed { i, p ->
            val from = start
            start += p.notes.size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = if (state.etudePhrase == i) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)) {
                            append("${i + 1}. ${p.role.title}: ")
                        }
                        p.notes.forEachIndexed { k, n ->
                            val lit = state.etudeNote == from + k
                            val style = when {
                                lit -> SpanStyle(color = MaterialTheme.colorScheme.onPrimary, background = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                k in p.themeNotes -> SpanStyle(color = MaterialTheme.colorScheme.primary)
                                else -> SpanStyle(color = muted)
                            }
                            withStyle(style) { append(" ${state.noteName(p.station, n)} ") }
                        }
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = { vm.playEtudePhrase(i) }) { Text("▶") }
            }
        }
    }
}

/** Answer in the jins: the etude's question plays, you answer on four or five notes. */
@Composable
private fun JinsAnswerPanel(state: JinsUiState, vm: JinsViewModel) {
    val question = state.etude.phrases.getOrNull(state.question) ?: return
    val answer = state.etude.phrases.getOrNull(state.answer) ?: return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ответь в джинсе", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Вопрос останавливается на ${state.noteName(question.station, question.notes.last())}. " +
                "Ответь своей фразой из нот джинса и приди на ${state.noteName(answer.station, answer.notes.last())}; " +
                "не забудь цветную ступень ${state.colour.joinToString { state.name(it) }}.",
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.phase == AnswerPhase.Question -> androidx.compose.material3.Button(onClick = vm::stop) { Text("■ Стоп") }
                state.phase == AnswerPhase.Listening -> androidx.compose.material3.Button(onClick = vm::finishAnswer) { Text("✋ Готово") }
                else -> androidx.compose.material3.Button(onClick = vm::playQuestion) {
                    Text(if (state.phase == AnswerPhase.Idle) "▶ Задать вопрос" else "▶ Вопрос ещё раз")
                }
            }
            TextButton(onClick = vm::playExample) { Text("💡 Пример ответа") }
            TextButton(onClick = vm::newEtude) { Text("➡ Другой вопрос") }
        }
        if (state.phase == AnswerPhase.Listening) {
            Text("Твоя очередь: играй ответ — после паузы я его разберу.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        }
        if (state.answerNotes.isNotEmpty()) {
            Text(
                buildAnnotatedString {
                    state.answerNotes.forEachIndexed { i, n ->
                        val outside = state.verdict?.outside?.contains(i) ?: false
                        withStyle(SpanStyle(color = if (outside) MaterialTheme.colorScheme.tertiary else InTuneGreen, fontWeight = FontWeight.SemiBold)) {
                            append(" ${state.playedName(n)} ")
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
    }
}
