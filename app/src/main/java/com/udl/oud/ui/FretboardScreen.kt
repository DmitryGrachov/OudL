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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
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
import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.Sayrs
import com.udl.oud.fretboard.Spelling
import com.udl.oud.fretboard.Scale
import com.udl.oud.fretboard.ScaleGroup
import com.udl.oud.fretboard.WesternScale
import com.udl.oud.fretboard.WesternScales
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Pitch
import com.udl.oud.fretboard.Position
import com.udl.oud.fretboard.ScaleNote
import com.udl.oud.fretboard.intervalLabel
import com.udl.oud.tuner.Note
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** What a board tab shows: all notes, or one scale at a time picked from [groups]. */
private enum class BoardMode(val title: String, val groups: List<ScaleGroup>) {
    Notes("Гриф", emptyList()),
    Maqamat("Макамы", com.udl.oud.fretboard.Maqamat.families),
    Western("Гаммы", listOf(ScaleGroup("Гаммы", WesternScales.all))),
    ;

    val choices: List<Scale> get() = groups.flatMap { it.scales }
}

/** The fretboard with all notes and layers. */
@Composable
fun FretboardRoute() = BoardRoute(BoardMode.Notes)

/** The same board showing one maqam at a time. */
@Composable
fun MaqamRoute() = BoardRoute(BoardMode.Maqamat)

/** The same board showing one European scale at a time. */
@Composable
fun WesternScaleRoute() = BoardRoute(BoardMode.Western)

@Composable
private fun BoardRoute(mode: BoardMode) {
    // Each tab keeps its own board settings.
    val vm: FretboardViewModel = viewModel(key = mode.name)
    val mic = rememberMicPermission()
    val state by vm.state.collectAsStateWithLifecycle()
    if (mode.choices.isNotEmpty() && state.selected == null) {
        LaunchedEffect(Unit) { vm.select(mode.choices.first()) }
    }
    if (mic.granted && state.live) {
        LifecycleResumeEffect(Unit) {
            @Suppress("MissingPermission")
            vm.start()
            onPauseOrDispose { vm.stop() }
        }
    }
    FretboardScreen(state, vm, mode, needsMic = state.live && !mic.granted, onRequestMic = mic.request)
}

@Composable
private fun FretboardScreen(
    state: FretboardUiState,
    vm: FretboardViewModel,
    mode: BoardMode,
    needsMic: Boolean,
    onRequestMic: () -> Unit,
) {
    val scaleMode = mode.choices.isNotEmpty()
    // View options (names, layers, course) fold away so the board keeps its height; the scale
    // tabs have their own bar above, so there they start folded.
    var optionsOpen by rememberSaveable(mode) { mutableStateOf(!scaleMode) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        // On tablets the navigation rail already names the screen; the board needs the height.
        Scaffold(topBar = { if (!wide) TopAppBar(title = { Text(mode.title) }) }) { padding ->
            val content: @Composable (Modifier) -> Unit = { boardModifier ->
                if (state.exercise != null) {
                    ExercisePanel(state, vm, needsMic, onRequestMic)
                } else {
                    HeardBar(
                        state, needsMic, onRequestMic, vm::setLive,
                        // Scale tabs start their drill from the scale bar.
                        onDrill = if (scaleMode) null else vm::startFretboardDrill,
                        optionsOpen = optionsOpen,
                        onToggleOptions = { optionsOpen = !optionsOpen },
                    )
                }
                if (scaleMode) ScaleBar(state, vm, mode.groups)
                if (scaleMode && state.sayrOpen) (state.selected as? Maqam)?.let { SayrPanel(state, vm, it) }
                if (optionsOpen) Options(state, vm, scaleMode)
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
    onDrill: (() -> Unit)?,
    optionsOpen: Boolean,
    onToggleOptions: () -> Unit,
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
                heard == null && state.selected != null -> ScaleSummary(state)
                heard == null -> Text(
                    "Играй — место на грифе подсветится",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    val (positions, cents) = heard
                    val scale = state.degrees
                    val pitch = positions.firstOrNull()?.pitch
                        ?: Pitch.nearest(state.frequency!!, state.a4.toDouble(), state.snapToQuarterTones)
                    val inTune = abs(cents) <= FretboardUiState.IN_TUNE_CENTS
                    val outside = scale != null && pitch.step.mod(24) !in scale
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
                                when {
                                    !outside -> ""
                                    state.selected is Maqam -> "  · не из макама"
                                    else -> "  · не из гаммы"
                                },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (inTune) InTuneGreen else MaterialTheme.colorScheme.tertiary,
                        )
                        CentsBar(cents, Modifier.padding(start = 16.dp).width(160.dp).height(16.dp))
                    }
                }
            }
        }
        onDrill?.let { TextButton(onClick = it, modifier = Modifier.padding(end = 8.dp)) { Text("🎯 Найди ноту") } }
        TextButton(onClick = onToggleOptions, modifier = Modifier.padding(end = 8.dp)) {
            Text(if (optionsOpen) "Вид ▴" else "Вид ▾")
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
private fun Options(state: FretboardUiState, vm: FretboardViewModel, scaleMode: Boolean) {
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
        // A scale decides by itself which notes are shown, so the note layers only make sense without one.
        if (!scaleMode) {
            FilterChip(state.showAccidentals, onClick = vm::toggleAccidentals, label = { Text("♯ ♭") })
            FilterChip(state.showQuarterTones, onClick = vm::toggleQuarterTones, label = { Text("Четвертитоны") })
        }
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

    /** Dots and names shrink when rows get tight, so a busy screen never makes them overlap. */
    val dotScale: Float = (rowHeight / with(density) { COMFORTABLE_ROW.toPx() }).coerceIn(MIN_DOT_SCALE, 1f)

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

    private companion object {
        /** Row height at which dots and names are drawn at full size. */
        val COMFORTABLE_ROW = 64.dp
        const val MIN_DOT_SCALE = 0.6f
    }
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
    val scale = state.degrees
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
        playing = if (dark) Color(0xFFFFF1D6) else Color(0xFFB4552A),
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
        // With the sayr open the maqam's pillars get a ring: the ghammaz in the upper jins'
        // colour, and the degree development starts from, when it isn't the tonic.
        val maqam = state.selected as? Maqam
        val sayr = maqam?.let { Sayrs.of(it) }
        fun pillarRing(note: ScaleNote?): Color? = when {
            !state.sayrOpen || maqam == null || note == null -> null
            note.degree == maqam.ghammaz -> colors.upperJins
            sayr != null && sayr.start != 0 && note.degree == sayr.start -> colors.quarter
            else -> null
        }
        // A scale drill shows every place for the due note; a fretboard drill shows its one
        // spot, and only when the hint is on — finding it is the exercise.
        val drill = state.exercise
        fun drillShows(p: Position): Boolean = when {
            drill == null || p.pitch.step != drill.target -> false
            drill.places == null -> true
            else -> state.drillHint && p == drill.targetPlace
        }
        // In a fretboard drill the names would give the answer away: only what lights up is named.
        val hideNames = drill?.places != null && !drill.done && !state.drillHint
        positions.forEach { p ->
            // What the app plays, or the note the drill asks for, lights up just like what the
            // mic hears, in its own colour.
            val highlight = when {
                p.pitch.step == state.playingStep -> colors.playing
                drillShows(p) -> colors.playing
                p in heardPositions -> colors.heard
                else -> null
            }
            if (state.isVisible(p, scale) || highlight != null) {
                val note = scale?.get(p.pitch.step.mod(24))
                // Open strings keep their names: they say which string is which.
                val hidden = hideNames && highlight == null && p.cents > 0
                val label = if (hidden) "" else state.labelFor(p.pitch, scale)
                drawPosition(p, geometry, label, note, state.showFingers, highlight, pillarRing(note), colors, measurer)
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
    val playing: Color,
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
 * A [highlight] (heard or being played) overrides the colour and adds a halo.
 */
private fun DrawScope.drawPosition(
    p: Position,
    g: BoardGeometry,
    text: String,
    note: ScaleNote?,
    showFingers: Boolean,
    highlight: Color?,
    /** Ring marking a pillar of the maqam's sayr, besides the tonic's own ring. */
    pillar: Color?,
    c: BoardColors,
    measurer: TextMeasurer,
) {
    val lit = highlight != null
    val center = g.center(p)
    val quarter = p.pitch.isQuarterTone && note == null && !lit
    val natural = p.pitch.isNatural || note != null || lit
    val radius = when {
        quarter -> 9.dp
        natural -> 15.dp
        else -> 12.dp
    }.toPx() * g.dotScale

    if (highlight != null) drawCircle(highlight.copy(alpha = 0.3f), radius = radius + 12.dp.toPx() * g.dotScale, center = center)
    val fill = when {
        highlight != null -> highlight
        note != null -> if (note.upper) c.upperJins else c.natural
        quarter -> c.quarter
        natural -> c.natural
        else -> c.accidental
    }
    drawCircle(fill, radius = radius, center = center)
    if (p.cents == 0) drawCircle(c.nut, radius = radius, center = center, style = Stroke(2.dp.toPx()))
    val ring = if (note?.degree == 0) c.label else pillar
    ring?.let { drawCircle(it, radius = radius + 4.dp.toPx() * g.dotScale, center = center, style = Stroke(3.dp.toPx() * g.dotScale)) }

    if (showFingers && !quarter) {
        p.finger?.let { finger ->
            val text = measurer.measure(
                finger.toString(),
                TextStyle(
                    fontSize = 13.sp * g.dotScale,
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
            fontSize = (if (quarter) 10.sp else 12.sp) * g.dotScale,
            fontWeight = if (natural || lit) FontWeight.SemiBold else FontWeight.Normal,
            color = highlight ?: c.label,
        ),
    )
    val gap = (if (ring != null) 7.dp else 3.dp).toPx() * g.dotScale
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

/** Scale picker, tonic, direction, and the scale written out with its intervals. */
@Composable
private fun ScaleBar(state: FretboardUiState, vm: FretboardViewModel, groups: List<ScaleGroup>) {
    Column(Modifier.fillMaxWidth()) {
        // With several groups (maqam families) pick the family first, then a scale within it.
        val group = groups.firstOrNull { state.selected in it.scales } ?: groups.first()
        if (groups.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Семья", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
                groups.forEach { g ->
                    FilterChip(
                        g == group,
                        onClick = { if (g != group) vm.select(g.scales.first()) },
                        label = { Text(g.name) },
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (groups.size > 1) {
                Text("Макам", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
            }
            group.scales.forEach { choice ->
                FilterChip(
                    state.selected == choice,
                    onClick = { vm.select(choice) },
                    label = { Text(choice.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        val selected = state.selected ?: return@Column
        val notes = state.selectedNotes ?: return@Column
        val scale = state.degrees
        fun name(note: ScaleNote) = state.labelFor(Pitch(state.tonic + note.offset), scale)
        val lowerColor = MaterialTheme.colorScheme.primary
        val upperColor = upperJinsColor()
        val intervalColor = MaterialTheme.colorScheme.onSurfaceVariant

        // Tonic to octave, written in the direction it is played.
        val upNames = notes.map { name(it) to if (it.upper) upperColor else lowerColor } + (name(notes[0]) to lowerColor)
        val upSteps = notes.map { state.tonic + it.offset } + (state.tonic + 24)
        val upIntervals = selected.intervals(state.descending)
        val names = if (state.descending) upNames.reversed() else upNames
        val steps = if (state.descending) upSteps.reversed() else upSteps
        val intervals = if (state.descending) upIntervals.reversed() else upIntervals
        val playingColor = MaterialTheme.colorScheme.onPrimary
        val playingBackground = MaterialTheme.colorScheme.onSurface

        FlowRow(verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Тоника", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { vm.shiftTonic(-1) }) { Text("−", fontSize = 20.sp) }
                Text(
                    name(notes[0]) + Note(state.tonic / 2).octave,
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { vm.shiftTonic(1) }) { Text("+", fontSize = 20.sp) }
                FilterChip(!state.descending, onClick = { vm.setDescending(false) }, label = { Text("↑ вверх") })
                Spacer(Modifier.width(8.dp))
                FilterChip(state.descending, onClick = { vm.setDescending(true) }, label = { Text("↓ вниз") })
                TextButton(onClick = vm::playScale) { Text("▶ Сыграть") }
                TextButton(onClick = vm::startExercise) { Text("🎯 Упражнение") }
                if (selected is Maqam) {
                    FilterChip(state.sayrOpen, onClick = vm::toggleSayr, label = { Text("Сайр") })
                }
            }
            Text(
                buildAnnotatedString {
                    names.forEachIndexed { i, (noteName, color) ->
                        // The note sounding right now is marked here as well as on the board.
                        val style = if (steps[i] == state.playingStep) {
                            SpanStyle(color = playingColor, background = playingBackground, fontWeight = FontWeight.Bold)
                        } else {
                            SpanStyle(color = color, fontWeight = FontWeight.SemiBold)
                        }
                        withStyle(style) { append(" $noteName ") }
                        if (i < intervals.size) {
                            withStyle(SpanStyle(color = intervalColor)) { append("  ${intervalLabel(intervals[i])}  ") }
                        }
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterVertically).padding(start = 16.dp),
            )
        }
    }
}

/**
 * How the shown scale is built: for a maqam, lower jins on the tonic + upper jins on the
 * ghammaz; for a European scale, what sets it apart.
 */
@Composable
private fun ScaleSummary(state: FretboardUiState) {
    val selected = state.selected ?: return
    val sameWayDown = if (state.descending && !selected.hasDescent) "  · вниз так же, как вверх" else ""
    val hint = "    ◎ тоника · играй — место подсветится"
    if (selected is WesternScale) {
        Text(
            selected.summary + sameWayDown + hint,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val maqam = selected as Maqam
    val notes = state.selectedNotes ?: return
    val scale = state.degrees
    fun name(note: ScaleNote) = state.labelFor(Pitch(state.tonic + note.offset), scale)
    val lowerColor = MaterialTheme.colorScheme.primary
    val upperColor = upperJinsColor()
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = lowerColor)) { append("${maqam.lower.name} на ${name(notes[0])}") }
            append("  +  ")
            withStyle(SpanStyle(color = upperColor)) {
                append("${maqam.upperJins(state.descending).name} на ${name(notes[maqam.ghammaz])}")
            }
            append(sameWayDown)
            append(hint)
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The "play the scale" drill: which note is due and how the current one sounds, the notes
 * done so far with their offsets, and a summary at the end.
 */
@Composable
private fun ExercisePanel(
    state: FretboardUiState,
    vm: FretboardViewModel,
    needsMic: Boolean,
    onRequestMic: () -> Unit,
) {
    val exercise = state.exercise ?: return
    val degrees = state.degrees
    fun name(step: Int) = state.labelFor(Pitch(step), degrees)
    val good = InTuneGreen
    val off = MaterialTheme.colorScheme.tertiary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun colorFor(cents: Double) = if (abs(cents) <= FretboardUiState.IN_TUNE_CENTS) good else off

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val target = exercise.target
                when {
                    needsMic -> Text(
                        "Для упражнения нужен микрофон — нажми, чтобы разрешить",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.pointerInput(Unit) { detectTapGestures { onRequestMic() } },
                    )
                    target == null -> {
                        val worst = exercise.worst
                        Text(
                            "Готово: ${exercise.inTuneCount} из ${exercise.steps.size} точно" +
                                " · средняя ошибка ${exercise.meanError.roundToInt()} ц" +
                                (worst?.let { " · хуже всего ${name(exercise.steps[it])} ${signedCents(exercise.results[it])}" } ?: ""),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    else -> {
                        Text(
                            "Нота ${exercise.results.size + 1} из ${exercise.steps.size}:  ",
                            style = MaterialTheme.typography.titleMedium,
                            color = muted,
                        )
                        val place = exercise.targetPlace
                        val where = place?.let {
                            val open = state.tuning.courses[it.course]
                            val course = Pitch.of(open).label(state.labels)
                            " на струне " + if (state.labels == NoteLabels.Arabic) course else "$course${open.octave}"
                        } ?: ""
                        Text(
                            "сыграй ${name(target)}$where",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        val cents = state.exerciseCents
                        val frequency = state.frequency
                        if (cents != null && frequency != null) {
                            val heardName = state.labelFor(
                                Pitch.nearest(frequency, state.a4.toDouble(), state.snapToQuarterTones),
                                degrees,
                            )
                            Text(
                                when {
                                    abs(cents) > 50 -> "   слышу $heardName — другая нота"
                                    abs(cents) <= FretboardUiState.IN_TUNE_CENTS -> "   ${signedCents(cents)} — держи"
                                    cents < 0 -> "   низко на ${abs(cents).roundToInt()} ц"
                                    else -> "   высоко на ${cents.roundToInt()} ц"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (abs(cents) > 50) muted else colorFor(cents),
                            )
                        }
                    }
                }
            }
            if (exercise.places != null) {
                FilterChip(state.drillHint, onClick = vm::toggleDrillHint, label = { Text("Подсказка") })
            }
            TextButton(onClick = vm::restartExercise) { Text(if (exercise.done) "Ещё раз" else "Заново") }
            TextButton(onClick = vm::stopExercise) { Text("Закончить") }
        }
        Text(
            buildAnnotatedString {
                exercise.steps.forEachIndexed { i, step ->
                    val result = exercise.results.getOrNull(i)
                    val style = when {
                        result != null -> SpanStyle(color = colorFor(result), fontWeight = FontWeight.SemiBold)
                        i == exercise.results.size -> SpanStyle(
                            color = MaterialTheme.colorScheme.onPrimary,
                            background = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        else -> SpanStyle(color = muted)
                    }
                    withStyle(style) {
                        append(" ${name(step)}")
                        if (result != null) append(" ${signedCents(result)}")
                        append(" ")
                    }
                    if (i < exercise.steps.lastIndex) withStyle(SpanStyle(color = muted)) { append(" · ") }
                }
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * How the selected maqam unfolds: its pillars (also ringed on the board), the usual path, and
 * the usual modulations, each a button that moves the board to that maqam on that note.
 */
@Composable
private fun SayrPanel(state: FretboardUiState, vm: FretboardViewModel, maqam: Maqam) {
    val sayr = Sayrs.of(maqam)
    val notes = state.selectedNotes ?: return
    val degrees = state.degrees
    fun name(degree: Int) = state.labelFor(Pitch(state.tonic + notes[degree].offset), degrees)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val start = sayr?.start?.takeIf { it != 0 && it != maqam.ghammaz }

    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            sayr?.path?.mapIndexed { i, step -> "${i + 1}. $step" }?.joinToString("   →   ")
                ?: "Путь развития для этого макама пока не записан — опоры взяты из его строения.",
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        // Pillars and moves share one row to leave the board its height.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)) {
                        append("◎ тоника ${name(0)}")
                    }
                    withStyle(SpanStyle(color = upperJinsColor(), fontWeight = FontWeight.SemiBold)) {
                        append("   ◎ гаммаз ${name(maqam.ghammaz)}")
                    }
                    if (start != null) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)) {
                            append("   ◎ центр ${name(start)}")
                        }
                    }
                    append("     ")
                },
                Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.titleSmall,
            )
            val trail = state.sayrTrail.lastOrNull()
            if (trail != null) {
                TextButton(onClick = vm::modulateBack) {
                    Text("← назад: ${trail.scale.name} на ${Spelling.of(trail.tonic).label(NoteLabels.Solfege)}")
                }
            }
            val moves = sayr?.modulations.orEmpty().mapNotNull { m ->
                Maqamat.all.firstOrNull { it.name == m.maqam }?.let { it to state.tonic + m.offset }
            }
            if (moves.isNotEmpty()) {
                Text("Переходы:", Modifier.align(Alignment.CenterVertically), color = muted)
                moves.forEach { (target, tonic) ->
                    AssistChip(
                        onClick = { vm.modulate(target, tonic) },
                        label = { Text("→ ${target.name} на ${Spelling.of(tonic).label(NoteLabels.Solfege)}") },
                    )
                }
            }
        }
    }
}
