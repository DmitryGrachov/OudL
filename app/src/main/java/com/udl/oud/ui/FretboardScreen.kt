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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udl.oud.fretboard.Fretboard
import com.udl.oud.fretboard.FretboardUiState
import com.udl.oud.fretboard.FretboardViewModel
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Pitch
import com.udl.oud.fretboard.Position
import com.udl.oud.fretboard.ScaleNote
import com.udl.oud.fretboard.intervalLabel
import com.udl.oud.tuner.Note
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun FretboardRoute(vm: FretboardViewModel = viewModel()) {
    val mic = rememberMicPermission()
    val state by vm.state.collectAsStateWithLifecycle()
    if (mic.granted && state.live) {
        LifecycleResumeEffect(Unit) {
            @Suppress("MissingPermission")
            vm.start()
            onPauseOrDispose { vm.stop() }
        }
    }
    FretboardScreen(state, vm, needsMic = state.live && !mic.granted, onRequestMic = mic.request)
}

@Composable
private fun FretboardScreen(
    state: FretboardUiState,
    vm: FretboardViewModel,
    needsMic: Boolean,
    onRequestMic: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        // On tablets the navigation rail already names the screen; the board needs the height.
        Scaffold(topBar = { if (!wide) TopAppBar(title = { Text("Гриф") }) }) { padding ->
            val content: @Composable (Modifier) -> Unit = { boardModifier ->
                HeardBar(state, needsMic, onRequestMic, vm::setLive)
                Options(state, vm)
                MaqamBar(state, vm)
                FretboardCanvas(state, onTap = vm::play, modifier = boardModifier)
            }
            if (wide) {
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 8.dp)) {
                    content(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp))
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                ) {
                    content(Modifier.fillMaxWidth().height(420.dp).padding(top = 8.dp))
                }
            }
        }
    }
}

/** What is being played: note name, cents off, and the live on/off switch. */
@Composable
private fun HeardBar(
    state: FretboardUiState,
    needsMic: Boolean,
    onRequestMic: () -> Unit,
    onLive: (Boolean) -> Unit,
) {
    val heard = state.heard
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            when {
                state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                needsMic -> Text(
                    "Для подсветки нужен микрофон — нажми, чтобы разрешить",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { onRequestMic() } },
                )
                !state.live -> Text(
                    "Нажми на точку, чтобы услышать ноту",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                heard == null && state.maqam != null -> AjnasSummary(state)
                heard == null -> Text(
                    "Играй — место на грифе подсветится",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    val (positions, cents) = heard
                    val scale = state.scale
                    val pitch = positions.firstOrNull()?.pitch
                        ?: Pitch.nearest(state.frequency!!, state.a4.toDouble(), state.showQuarterTones || scale != null)
                    val inTune = abs(cents) <= FretboardUiState.IN_TUNE_CENTS
                    val outsideMaqam = scale != null && pitch.step.mod(24) !in scale
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.labelFor(pitch, scale),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (inTune) InTuneGreen else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "  ${signedCents(cents)}" +
                                (if (positions.isEmpty()) "  · не на грифе" else "") +
                                (if (outsideMaqam) "  · не из макама" else ""),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (inTune) InTuneGreen else MaterialTheme.colorScheme.tertiary,
                        )
                        CentsBar(cents, Modifier.padding(start = 16.dp).width(160.dp).height(16.dp))
                    }
                }
            }
        }
        Text("Слушать", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = state.live, onCheckedChange = onLive)
    }
}

/** Horizontal −50…+50 cents strip with a marker. */
@Composable
private fun CentsBar(cents: Double, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val marker = if (abs(cents) <= FretboardUiState.IN_TUNE_CENTS) InTuneGreen else MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        val mid = size.height / 2
        drawLine(track, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 4.dp.toPx())
        val zone = (FretboardUiState.IN_TUNE_CENTS / 50 * size.width / 2).toFloat()
        drawLine(
            InTuneGreen.copy(alpha = 0.35f),
            Offset(size.width / 2 - zone, mid),
            Offset(size.width / 2 + zone, mid),
            strokeWidth = 8.dp.toPx(),
        )
        val x = ((cents.coerceIn(-50.0, 50.0) + 50) / 100 * size.width).toFloat()
        drawCircle(marker, radius = size.height / 2, center = Offset(x, mid))
    }
}

@Composable
private fun Options(state: FretboardUiState, vm: FretboardViewModel) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        listOf(NoteLabels.Solfege to "До-Ре-Ми", NoteLabels.Latin to "C-D-E", NoteLabels.Arabic to "Арабские")
            .forEach { (labels, title) ->
                FilterChip(state.labels == labels, onClick = { vm.setLabels(labels) }, label = { Text(title) })
            }
        Text("·", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outline)
        // A maqam decides by itself which notes are shown.
        val noMaqam = state.maqam == null
        FilterChip(state.showAccidentals, onClick = vm::toggleAccidentals, label = { Text("♯ ♭") }, enabled = noMaqam)
        FilterChip(
            state.showQuarterTones,
            onClick = vm::toggleQuarterTones,
            label = { Text("Четвертитоны") },
            enabled = noMaqam,
        )
        FilterChip(state.showFingers, onClick = vm::toggleFingers, label = { Text("Пальцы") })
        FilterChip(!state.bassOnTop, onClick = vm::flip, label = { Text("Бас снизу") })
        FilterChip(state.extended, onClick = vm::toggleExtended, label = { Text("Дальше шейки") })
        if (state.live) {
            Text("·", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outline)
            FilterChip(state.onlyCourse == null, onClick = { vm.setOnlyCourse(null) }, label = { Text("Любая струна") })
            state.tuning.courses.forEachIndexed { index, note ->
                FilterChip(
                    state.onlyCourse == index,
                    onClick = { vm.setOnlyCourse(index) },
                    // Arabic names already tell octaves apart; the others need the number (two Cs).
                    label = {
                        val name = Pitch.of(note).label(state.labels)
                        Text(if (state.labels == NoteLabels.Arabic) name else "$name${note.octave}")
                    },
                )
            }
        }
    }
}

/** Where things go on the board: the nut at the left, courses in rows, real string-length spacing. */
private class BoardGeometry(
    val size: Size,
    val courses: Int,
    val bassOnTop: Boolean,
    val maxCents: Int,
    density: Density,
) {
    private val leftPad = with(density) { 84.dp.toPx() }
    private val rightPad = with(density) { 28.dp.toPx() }
    private val verticalPad = with(density) { 20.dp.toPx() }
    private val openOffset = with(density) { 46.dp.toPx() }

    val nutX = leftPad
    val endX = size.width - rightPad
    val top = verticalPad
    val bottom = size.height - verticalPad
    val rowHeight = (bottom - top) / courses

    fun x(cents: Int): Float = if (cents == 0) {
        nutX - openOffset
    } else {
        val full = Fretboard.distanceFraction(maxCents.toDouble())
        (nutX + (endX - nutX) * Fretboard.distanceFraction(cents.toDouble()) / full).toFloat()
    }

    fun y(course: Int): Float {
        val row = if (bassOnTop) course else courses - 1 - course
        return top + rowHeight * (row + 0.5f)
    }

    fun center(p: Position) = Offset(x(p.cents), y(p.course))
}

@Composable
private fun FretboardCanvas(state: FretboardUiState, onTap: (Position) -> Unit, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val positions = remember(state.tuning, state.maxCents) { Fretboard.positions(state.tuning, state.maxCents) }
    val heard = state.heard
    val heardPositions = heard?.first?.toSet().orEmpty()
    val heardColor = if (heard != null && abs(heard.second) <= FretboardUiState.IN_TUNE_CENTS) {
        InTuneGreen
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scale = state.scale
    val colors = BoardColors(
        upperJins = upperJinsColor(),
        wood = if (dark) Color(0xFF3B2A1D) else Color(0xFFE2C9A0),
        body = if (dark) Color(0xFF55402C) else Color(0xFFF0DDBA),
        nut = Color(0xFFEDE3CF),
        string = Color(0xFFC9B48F),
        guide = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        natural = MaterialTheme.colorScheme.primary,
        onNatural = MaterialTheme.colorScheme.onPrimary,
        accidental = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        onAccidental = MaterialTheme.colorScheme.onSurface,
        quarter = MaterialTheme.colorScheme.tertiary,
        label = MaterialTheme.colorScheme.onSurface,
        heard = heardColor,
    )

    Canvas(
        modifier.pointerInput(
            state.tuning, state.bassOnTop, state.showAccidentals, state.showQuarterTones, state.maxCents, scale,
        ) {
            detectTapGestures { tap ->
                val geometry = BoardGeometry(
                    Size(size.width.toFloat(), size.height.toFloat()),
                    state.tuning.courses.size,
                    state.bassOnTop,
                    state.maxCents,
                    this,
                )
                val touchRadius = 30.dp.toPx()
                positions.filter { state.isVisible(it, scale) }
                    .minByOrNull { val c = geometry.center(it); hypot(c.x - tap.x, c.y - tap.y) }
                    ?.takeIf { val c = geometry.center(it); hypot(c.x - tap.x, c.y - tap.y) <= touchRadius }
                    ?.let(onTap)
            }
        },
    ) {
        val geometry = BoardGeometry(size, state.tuning.courses.size, state.bassOnTop, state.maxCents, this)
        drawBoard(geometry, colors, measurer)
        positions.forEach { p ->
            val lit = p in heardPositions
            if (state.isVisible(p, scale) || lit) {
                val note = scale?.get(p.pitch.step.mod(24))
                drawPosition(p, geometry, state.labelFor(p.pitch, scale), note, state.showFingers, lit, colors, measurer)
            }
        }
    }
}

private class BoardColors(
    val upperJins: Color,
    val wood: Color,
    val body: Color,
    val nut: Color,
    val string: Color,
    val guide: Color,
    val natural: Color,
    val onNatural: Color,
    val accidental: Color,
    val onAccidental: Color,
    val quarter: Color,
    val label: Color,
    val heard: Color,
)

private fun DrawScope.drawBoard(g: BoardGeometry, c: BoardColors, measurer: TextMeasurer) {
    drawRoundRect(
        c.wood,
        topLeft = Offset(g.nutX, 0f),
        size = Size(g.endX + 12.dp.toPx() - g.nutX, size.height),
        cornerRadius = CornerRadius(8.dp.toPx()),
    )
    // Past the end of the neck the strings run over the soundboard.
    if (g.maxCents > Fretboard.NECK_CENTS) {
        val bodyX = g.x(Fretboard.NECK_CENTS) + (g.x(Fretboard.NECK_CENTS + 50) - g.x(Fretboard.NECK_CENTS)) / 2
        drawRoundRect(
            c.body,
            topLeft = Offset(bodyX, 0f),
            size = Size(g.endX + 12.dp.toPx() - bodyX, size.height),
            cornerRadius = CornerRadius(8.dp.toPx()),
        )
        val title = measurer.measure("корпус", TextStyle(fontSize = 11.sp, color = c.guide.copy(alpha = 1f)))
        drawText(title, topLeft = Offset(bodyX + 6.dp.toPx(), 2.dp.toPx()))
    }
    // Faint lines at every semitone: there are no frets, but the eye needs a reference.
    // Stronger at the fourth (end of first position) and the octave.
    for (cents in 100..g.maxCents step 100) {
        val x = g.x(cents)
        drawLine(
            c.guide,
            Offset(x, 0f),
            Offset(x, size.height),
            strokeWidth = (if (cents == 500 || cents == 1200) 2.dp else 1.dp).toPx(),
        )
    }
    drawLine(c.nut, Offset(g.nutX, 0f), Offset(g.nutX, size.height), strokeWidth = 6.dp.toPx())
    // Each course is a pair of strings; bass courses are thicker.
    val n = g.courses
    for (course in 0 until n) {
        val y = g.y(course)
        val thickness = (2.6f - 1.4f * course / (n - 1).coerceAtLeast(1)).dp.toPx()
        val gap = 3.dp.toPx()
        listOf(y - gap, y + gap).forEach {
            drawLine(c.string, Offset(g.nutX - 4.dp.toPx(), it), Offset(size.width, it), strokeWidth = thickness)
        }
    }
}

/**
 * One spot on the board. With a maqam ([note] set) every degree is drawn full size, coloured by
 * the jins it belongs to, with a ring on the tonic; otherwise size and colour show the layer.
 */
private fun DrawScope.drawPosition(
    p: Position,
    g: BoardGeometry,
    text: String,
    note: ScaleNote?,
    showFingers: Boolean,
    lit: Boolean,
    c: BoardColors,
    measurer: TextMeasurer,
) {
    val center = g.center(p)
    val quarter = p.pitch.isQuarterTone && note == null
    val natural = p.pitch.isNatural || note != null
    val radius = when {
        quarter -> 9.dp
        natural -> 15.dp
        else -> 12.dp
    }.toPx()

    if (lit) drawCircle(c.heard.copy(alpha = 0.3f), radius = radius + 12.dp.toPx(), center = center)
    val fill = when {
        lit -> c.heard
        note != null -> if (note.upper) c.upperJins else c.natural
        quarter -> c.quarter
        natural -> c.natural
        else -> c.accidental
    }
    drawCircle(fill, radius = radius, center = center)
    if (p.cents == 0) drawCircle(c.nut, radius = radius, center = center, style = Stroke(2.dp.toPx()))
    if (note?.degree == 0) drawCircle(c.label, radius = radius + 4.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))

    if (showFingers && !quarter) {
        p.finger?.let { finger ->
            val text = measurer.measure(
                finger.toString(),
                TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (natural || lit) c.onNatural else c.onAccidental,
                ),
            )
            drawText(text, topLeft = center - Offset(text.size.width / 2f, text.size.height / 2f))
        }
    }

    // Quarter-tone names go above the string so they don't collide with their neighbours below.
    val label = measurer.measure(
        text,
        TextStyle(
            fontSize = if (quarter) 10.sp else 12.sp,
            fontWeight = if (natural || lit) FontWeight.SemiBold else FontWeight.Normal,
            color = if (lit) c.heard else c.label,
        ),
    )
    val gap = (if (note?.degree == 0) 7.dp else 3.dp).toPx()
    val labelY = if (quarter) center.y - radius - gap - label.size.height else center.y + radius + gap
    drawText(label, topLeft = Offset(center.x - label.size.width / 2f, labelY))
}

/** Name of a pitch: spelled as a maqam degree when a maqam is shown, else by its own rule. */
private fun FretboardUiState.labelFor(pitch: Pitch, scale: Map<Int, ScaleNote>?): String {
    val spelling = scale?.get(pitch.step.mod(24))?.spelling ?: return pitch.label(labels)
    if (labels == NoteLabels.Arabic) pitch.arabicName?.let { return it }
    return spelling.label(if (labels == NoteLabels.Latin) NoteLabels.Latin else NoteLabels.Solfege)
}

@Composable
private fun upperJinsColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF8DB6D9) else Color(0xFF3F6E99)

/** Maqam picker, tonic, and the scale written out with its intervals and ajnas. */
@Composable
private fun MaqamBar(state: FretboardUiState, vm: FretboardViewModel) {
    Column(Modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Макам", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
            FilterChip(state.maqam == null, onClick = { vm.setMaqam(null) }, label = { Text("Все ноты") })
            Maqamat.all.forEach { maqam ->
                FilterChip(state.maqam == maqam, onClick = { vm.setMaqam(maqam) }, label = { Text(maqam.name) })
            }
        }
        val maqam = state.maqam ?: return@Column
        val notes = maqam.notes(state.maqamTonic)
        val scale = state.scale
        fun name(note: ScaleNote) = state.labelFor(Pitch(state.maqamTonic + note.offset), scale)
        val lowerColor = MaterialTheme.colorScheme.primary
        val upperColor = upperJinsColor()

        FlowRow(verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Тоника", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { vm.shiftTonic(-1) }) { Text("−", fontSize = 20.sp) }
                Text(
                    name(notes[0]) + Note(state.maqamTonic / 2).octave,
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { vm.shiftTonic(1) }) { Text("+", fontSize = 20.sp) }
                TextButton(onClick = vm::playScale) { Text("▶ Сыграть гамму") }
            }
            Text(
                buildAnnotatedString {
                    notes.forEachIndexed { i, note ->
                        withStyle(SpanStyle(color = if (note.upper) upperColor else lowerColor, fontWeight = FontWeight.SemiBold)) {
                            append(name(note))
                        }
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append("  ${intervalLabel(maqam.intervals[i])}  ")
                        }
                    }
                    withStyle(SpanStyle(color = lowerColor, fontWeight = FontWeight.SemiBold)) { append(name(notes[0])) }
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterVertically).padding(start = 16.dp),
            )
        }
    }
}

/** How the shown maqam is built: lower jins on the tonic + upper jins on the ghammaz. */
@Composable
private fun AjnasSummary(state: FretboardUiState) {
    val maqam = state.maqam ?: return
    val notes = maqam.notes(state.maqamTonic)
    val scale = state.scale
    fun name(note: ScaleNote) = state.labelFor(Pitch(state.maqamTonic + note.offset), scale)
    val lowerColor = MaterialTheme.colorScheme.primary
    val upperColor = upperJinsColor()
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = lowerColor)) { append("${maqam.lower.name} на ${name(notes[0])}") }
            append("  +  ")
            withStyle(SpanStyle(color = upperColor)) {
                append("${maqam.upper.name} на ${name(notes[maqam.ghammaz])}")
            }
            append("    ◎ тоника · играй — место подсветится")
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
