package com.udl.oud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udl.oud.fretboard.Fretboard
import com.udl.oud.tuner.Tuning
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log2

/** How a note looks on the board: the scale's own, its jins, the jins' colour note, or a faint one around it. */
enum class DotStyle { Scale, Jins, Colour, Faint }

/** A note to show on the board: what it sounds at, its name, its look, a ring for the note to rest on. */
data class BoardNote(val hz: Double, val label: String, val style: DotStyle, val ring: Boolean = false, val degree: Int = 0)

private data class Placed(val note: BoardNote, val course: Int, val cents: Double)

/**
 * The neck with a set of notes placed by frequency, so quarter tones (in any intonation) sit
 * where they are played; every note shows on each course it can be played on. The note
 * sounding ([litHz]) and the one heard ([heardHz]) are lit. Shared by the taqsim, jins and talwin.
 */
@Composable
fun ScaleBoard(
    notes: List<BoardNote>,
    tuning: Tuning,
    a4: Double,
    litHz: Double?,
    heardHz: Double?,
    heardInTune: Boolean,
    modifier: Modifier = Modifier,
    onTap: ((BoardNote) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val maxCents = Fretboard.NECK_CENTS
    val placed = remember(notes, tuning, a4) {
        tuning.courses.flatMapIndexed { course, open ->
            val openHz = open.frequency(a4)
            notes.mapNotNull { n ->
                var cents = 1200 * log2(n.hz / openHz)
                if (abs(cents) < SNAP_CENTS) cents = 0.0
                if (cents < 0 || cents > maxCents) null else Placed(n, course, cents)
            }
        }
    }
    val colors = boardColors(if (heardInTune) InTuneGreen else MaterialTheme.colorScheme.tertiary)
    val colour = MaterialTheme.colorScheme.tertiary
    fun near(a: Double, b: Double, c: Double) = abs(1200 * log2(a / b)) <= c
    val tap = if (onTap == null) Modifier else Modifier.pointerInput(placed, tuning) {
        detectTapGestures { at ->
            val g = BoardGeometry(Size(size.width.toFloat(), size.height.toFloat()), tuning.courses.size, true, maxCents, this)
            fun dist(p: Placed) = hypot(g.x(p.cents) - at.x, g.y(p.course) - at.y)
            placed.minByOrNull(::dist)?.takeIf { dist(it) <= 30.dp.toPx() }?.let { onTap(it.note) }
        }
    }
    Canvas(modifier.then(tap)) {
        val g = BoardGeometry(size, tuning.courses.size, true, maxCents, this)
        drawBoard(g, colors, measurer)
        placed.forEach { p ->
            val faint = p.note.style == DotStyle.Faint
            val center = Offset(g.x(p.cents), g.y(p.course))
            val radius = (if (faint) 9.dp else 14.dp).toPx() * g.dotScale
            val highlight = when {
                litHz != null && near(p.note.hz, litHz, LIT_CENTS) -> colors.playing
                heardHz != null && near(p.note.hz, heardHz, HEARD_CENTS) -> colors.heard
                else -> null
            }
            if (highlight != null) drawCircle(highlight.copy(alpha = 0.3f), radius = radius + 12.dp.toPx() * g.dotScale, center = center)
            val fill = highlight ?: when (p.note.style) {
                DotStyle.Scale -> colors.natural
                DotStyle.Jins -> colors.upperJins
                DotStyle.Colour -> colour
                DotStyle.Faint -> colors.accidental
            }
            drawCircle(fill, radius = radius, center = center)
            if (p.cents == 0.0) drawCircle(colors.nut, radius = radius, center = center, style = Stroke(2.dp.toPx()))
            if (p.note.ring) drawCircle(colors.label, radius = radius + 4.dp.toPx() * g.dotScale, center = center, style = Stroke(3.dp.toPx() * g.dotScale))
            val label = measurer.measure(
                p.note.label,
                TextStyle(fontSize = (if (faint) 9.sp else 11.sp) * g.dotScale, fontWeight = FontWeight.SemiBold, color = highlight ?: colors.label),
            )
            drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y + radius + 3.dp.toPx() * g.dotScale))
        }
    }
}

/** A note this close to the open string is the open string. */
private const val SNAP_CENTS = 12.0
private const val LIT_CENTS = 5.0
private const val HEARD_CENTS = 35.0
