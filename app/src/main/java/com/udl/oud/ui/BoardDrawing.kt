@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.udl.oud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.udl.oud.fretboard.Fretboard
import com.udl.oud.fretboard.FretboardUiState
import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.Sayrs
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Pitch
import com.udl.oud.fretboard.Position
import com.udl.oud.fretboard.ScaleNote
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.abs
import kotlin.math.hypot


/** Where things go on the board: the nut at the left, courses in rows, real string-length spacing. */
internal class BoardGeometry(
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

    fun x(cents: Int): Float = x(cents.toDouble())

    /** Any pitch above the open string, not just the quarter-tone grid. */
    fun x(cents: Double): Float = if (cents == 0.0) {
        nutX - openOffset
    } else {
        val full = Fretboard.distanceFraction(maxCents.toDouble())
        (nutX + (endX - nutX) * Fretboard.distanceFraction(cents) / full).toFloat()
    }

    fun y(course: Int): Float {
        val row = if (bassOnTop) course else courses - 1 - course
        return top + rowHeight * (row + 0.5f)
    }

    /** Where a position is drawn: quarter tones where the [com.udl.oud.fretboard.Intonation] in use puts them. */
    fun center(p: Position) = Offset(x(p.cents + com.udl.oud.fretboard.Intonation.cents(p.pitch.step)), y(p.course))

    private companion object {
        /** Row height at which dots and names are drawn at full size. */
        val COMFORTABLE_ROW = 64.dp
        const val MIN_DOT_SCALE = 0.6f
    }
}

@Composable
internal fun FretboardCanvas(state: FretboardUiState, onTap: (Position) -> Unit, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val positions = remember(state.tuning, state.maxCents) { Fretboard.positions(state.tuning, state.maxCents) }
    val heard = state.heard
    val heardPositions = heard?.first?.toSet().orEmpty()
    val heardColor = if (heard != null && abs(heard.second) <= FretboardUiState.IN_TUNE_CENTS) {
        InTuneGreen
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val scale = state.degrees
    val colors = boardColors(heardColor)

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

/** Board colours from the theme; [heard] tints what the mic hears (in tune or not). */
@Composable
internal fun boardColors(heard: Color): BoardColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return BoardColors(
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
        heard = heard,
        playing = if (dark) Color(0xFFFFF1D6) else Color(0xFFB4552A),
    )
}

internal class BoardColors(
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

internal fun DrawScope.drawBoard(g: BoardGeometry, c: BoardColors, measurer: TextMeasurer) {
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
internal fun FretboardUiState.labelFor(pitch: Pitch, scale: Map<Int, ScaleNote>?): String {
    val spelling = scale?.get(pitch.step.mod(24))?.spelling ?: return pitch.label(labels)
    if (labels == NoteLabels.Arabic) pitch.arabicName?.let { return it }
    return spelling.label(if (labels == NoteLabels.Latin) NoteLabels.Latin else NoteLabels.Solfege)
}

@Composable
internal fun upperJinsColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF8DB6D9) else Color(0xFF3F6E99)

