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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
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
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Pitch
import com.udl.oud.fretboard.Position
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
    Scaffold(topBar = { TopAppBar(title = { Text("Гриф") }) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 600.dp
            val content: @Composable (Modifier) -> Unit = { boardModifier ->
                HeardBar(state, needsMic, onRequestMic, vm::setLive)
                Options(state, vm)
                FretboardCanvas(state, onTap = vm::play, modifier = boardModifier)
            }
            if (wide) {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    content(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp))
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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
                heard == null -> Text(
                    "Играй — место на грифе подсветится",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    val (positions, cents) = heard
                    val pitch = positions.firstOrNull()?.pitch
                        ?: Pitch.nearest(state.frequency!!, state.a4.toDouble(), state.showQuarterTones)
                    val inTune = abs(cents) <= FretboardUiState.IN_TUNE_CENTS
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            pitch.label(state.labels),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (inTune) InTuneGreen else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "  ${signedCents(cents)}" + if (positions.isEmpty()) "  · не на грифе" else "",
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
        FilterChip(state.showAccidentals, onClick = vm::toggleAccidentals, label = { Text("♯ ♭") })
        FilterChip(state.showQuarterTones, onClick = vm::toggleQuarterTones, label = { Text("Четвертитоны") })
        FilterChip(state.showFingers, onClick = vm::toggleFingers, label = { Text("Пальцы") })
        FilterChip(!state.bassOnTop, onClick = vm::flip, label = { Text("Бас снизу") })
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
private class BoardGeometry(val size: Size, val courses: Int, val bassOnTop: Boolean, density: Density) {
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
        val full = Fretboard.distanceFraction(Fretboard.NECK_CENTS.toDouble())
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
    val positions = remember(state.tuning) { Fretboard.positions(state.tuning) }
    val heard = state.heard
    val heardPositions = heard?.first?.toSet().orEmpty()
    val heardColor = if (heard != null && abs(heard.second) <= FretboardUiState.IN_TUNE_CENTS) {
        InTuneGreen
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val colors = BoardColors(
        wood = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF3B2A1D) else Color(0xFFE2C9A0),
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
        modifier.pointerInput(state.tuning, state.bassOnTop, state.showAccidentals, state.showQuarterTones) {
            detectTapGestures { tap ->
                val geometry = BoardGeometry(
                    Size(size.width.toFloat(), size.height.toFloat()),
                    state.tuning.courses.size,
                    state.bassOnTop,
                    this,
                )
                val touchRadius = 30.dp.toPx()
                positions.filter(state::isVisible)
                    .minByOrNull { val c = geometry.center(it); hypot(c.x - tap.x, c.y - tap.y) }
                    ?.takeIf { val c = geometry.center(it); hypot(c.x - tap.x, c.y - tap.y) <= touchRadius }
                    ?.let(onTap)
            }
        },
    ) {
        val geometry = BoardGeometry(size, state.tuning.courses.size, state.bassOnTop, this)
        drawBoard(geometry, colors)
        positions.forEach { p ->
            val lit = p in heardPositions
            if (state.isVisible(p) || lit) drawPosition(p, geometry, state, lit, colors, measurer)
        }
    }
}

private class BoardColors(
    val wood: Color,
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

private fun DrawScope.drawBoard(g: BoardGeometry, c: BoardColors) {
    drawRoundRect(
        c.wood,
        topLeft = Offset(g.nutX, 0f),
        size = Size(g.endX + 12.dp.toPx() - g.nutX, size.height),
        cornerRadius = CornerRadius(8.dp.toPx()),
    )
    // Faint lines at every semitone: there are no frets, but the eye needs a reference.
    for (cents in 100..Fretboard.NECK_CENTS step 100) {
        val x = g.x(cents)
        drawLine(
            c.guide,
            Offset(x, 0f),
            Offset(x, size.height),
            strokeWidth = (if (cents == 500) 2.dp else 1.dp).toPx(),
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

private fun DrawScope.drawPosition(
    p: Position,
    g: BoardGeometry,
    state: FretboardUiState,
    lit: Boolean,
    c: BoardColors,
    measurer: TextMeasurer,
) {
    val center = g.center(p)
    val quarter = p.pitch.isQuarterTone
    val natural = p.pitch.isNatural
    val radius = when {
        quarter -> 9.dp
        natural -> 15.dp
        else -> 12.dp
    }.toPx()

    if (lit) drawCircle(c.heard.copy(alpha = 0.3f), radius = radius + 12.dp.toPx(), center = center)
    val fill = when {
        lit -> c.heard
        quarter -> c.quarter
        natural -> c.natural
        else -> c.accidental
    }
    drawCircle(fill, radius = radius, center = center)
    if (p.cents == 0) drawCircle(c.nut, radius = radius, center = center, style = Stroke(2.dp.toPx()))

    if (state.showFingers && !quarter) {
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
        p.pitch.label(state.labels),
        TextStyle(
            fontSize = if (quarter) 10.sp else 12.sp,
            fontWeight = if (natural || lit) FontWeight.SemiBold else FontWeight.Normal,
            color = if (lit) c.heard else c.label,
        ),
    )
    val gap = 3.dp.toPx()
    val labelY = if (quarter) center.y - radius - gap - label.size.height else center.y + radius + gap
    drawText(label, topLeft = Offset(center.x - label.size.width / 2f, labelY))
}
