package com.udl.oud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.udl.oud.fretboard.Accidental
import com.udl.oud.fretboard.StaffNote
import com.udl.oud.taqsim.NoteValue

/**
 * A treble staff with one note on it — what is sounding, asked for or heard — and its name
 * under it. [octaveClef] adds the small 8 under the clef: written an octave above the sound.
 */
@Composable
fun NoteStaff(
    note: StaffNote?,
    name: String?,
    color: Color,
    hint: String,
    octaveClef: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            drawStaff(note, color, lineColor, octaveClef, measurer)
        }
        Text(
            name ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (name != null) color else muted,
            textAlign = TextAlign.Center,
        )
        Text(
            hint,
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** Staff positions in view at once: the staff and five ledger lines' worth around it. */
private const val WINDOW = 22

private fun DrawScope.drawStaff(note: StaffNote?, color: Color, lineColor: Color, octaveClef: Boolean, measurer: TextMeasurer) {
    // Half a staff space: as big as the column's width allows, with the window's height in view.
    val h = minOf(size.height / (WINDOW + 2), size.width / 13)
    // The staff sits in the middle (B4, position 4, at the centre); a note further out than the
    // window shifts the view to it so that its ledger lines still show.
    val half = WINDOW / 2
    val pos = note?.position ?: 4
    val centre = when {
        pos < 4 - half + 3 -> pos + half - 3
        pos > 4 + half - 3 -> pos - half + 3
        else -> 4
    }
    val middle = size.height / 2
    fun y(position: Int) = middle - (position - centre) * h
    val left = 4.dp.toPx()
    val right = size.width - 4.dp.toPx()
    val stroke = maxOf(1.dp.toPx(), h / 9)

    for (p in 0..8 step 2) drawLine(lineColor, Offset(left, y(p)), Offset(right, y(p)), stroke)

    // Treble clef: its curl wraps the G line (position 2).
    val clef = measurer.measure("𝄞", TextStyle(fontSize = (h * 6.6f).toSp(), color = lineColor))
    drawText(clef, topLeft = Offset(left + h * 0.2f, y(2) - clef.size.height * 0.64f))
    if (octaveClef) {
        val eight = measurer.measure("8", TextStyle(fontSize = (h * 1.6f).toSp(), color = lineColor, fontWeight = FontWeight.Bold))
        drawText(eight, topLeft = Offset(left + h * 1.6f, y(-3) - eight.size.height / 2f))
    }

    if (note == null) return
    val p = note.position
    val x = size.width * 0.7f
    val cy = y(p)

    for (l in note.ledgerLines) drawLine(lineColor, Offset(x - h * 2.1f, y(l)), Offset(x + h * 2.1f, y(l)), stroke)

    // Note head, tilted as engraved, with a stem: up below the middle line, down from it.
    val head = Size(h * 2.6f, h * 1.85f)
    rotate(-20f, pivot = Offset(x, cy)) {
        drawOval(color, topLeft = Offset(x - head.width / 2, cy - head.height / 2), size = head)
    }
    val stemUp = p < 4
    val stemX = if (stemUp) x + head.width / 2 - stroke else x - head.width / 2 + stroke
    val stemEnd = if (stemUp) cy - h * 7 else cy + h * 7
    drawLine(color, Offset(stemX, cy), Offset(stemX, stemEnd), stroke * 1.4f)

    drawAccidental(note, Offset(x - h * 3.4f, cy), h, color, stroke, measurer)
}

internal fun DrawScope.drawAccidental(note: StaffNote, at: Offset, h: Float, color: Color, stroke: Float, measurer: TextMeasurer) {
    fun glyph(text: String, mirrored: Boolean = false, raise: Float = 0f, slashed: Boolean = false) {
        val g = measurer.measure(text, TextStyle(fontSize = (h * 4.2f).toSp(), color = color))
        val topLeft = Offset(at.x - g.size.width / 2f, at.y - g.size.height / 2f - raise)
        if (mirrored) {
            scale(-1f, 1f, pivot = Offset(at.x, at.y)) { drawText(g, topLeft = topLeft) }
        } else {
            drawText(g, topLeft = topLeft)
        }
        // The Arabic half-flat: a flat with a stroke across its stem.
        if (slashed) {
            val stemX = at.x - g.size.width * 0.18f
            val y = topLeft.y + g.size.height * 0.42f
            drawLine(color, Offset(stemX - h * 1.0f, y + h * 0.55f), Offset(stemX + h * 1.0f, y - h * 0.55f), stroke * 1.8f)
        }
        // Turkish notation counts the komas the sign is worth.
        note.komas?.let { k ->
            val n = measurer.measure(k.toString(), TextStyle(fontSize = (h * 1.6f).toSp(), color = color, fontWeight = FontWeight.Bold))
            drawText(n, topLeft = Offset(at.x + g.size.width / 2f - n.size.width / 3f, topLeft.y))
        }
    }
    when (note.accidental) {
        Accidental.None -> Unit
        Accidental.Flat -> glyph("♭", raise = h * 1.1f)
        Accidental.HalfFlat -> glyph("♭", raise = h * 1.1f, slashed = true)
        Accidental.Sharp -> glyph("♯", raise = h * 0.6f)
        Accidental.HalfSharp -> {
            // A sharp with one upright: two rising bars crossed by a single stem.
            drawLine(color, Offset(at.x, at.y - h * 2.2f), Offset(at.x, at.y + h * 2.2f), stroke * 1.2f)
            for (dy in listOf(-0.7f, 0.7f)) {
                drawLine(
                    color,
                    Offset(at.x - h * 0.9f, at.y + (dy + 0.3f) * h),
                    Offset(at.x + h * 0.9f, at.y + (dy - 0.3f) * h),
                    stroke * 2.2f,
                )
            }
        }
    }
}

/** Treble clef at [left] with its curl on the G line [yG]; [octaveClef] adds the 8 below. */
internal fun DrawScope.drawClef(left: Float, yG: Float, h: Float, color: Color, octaveClef: Boolean, measurer: TextMeasurer) {
    val clef = measurer.measure("𝄞", TextStyle(fontSize = (h * 6.6f).toSp(), color = color))
    drawText(clef, topLeft = Offset(left + h * 0.2f, yG - clef.size.height * 0.64f))
    if (octaveClef) {
        val eight = measurer.measure("8", TextStyle(fontSize = (h * 1.6f).toSp(), color = color, fontWeight = FontWeight.Bold))
        drawText(eight, topLeft = Offset(left + h * 1.6f, yG + h * 5 - eight.size.height / 2f))
    }
}

/**
 * A note head at ([x], [cy]) of [value], tilted as engraved: hollow for whole and half notes,
 * a stem for all but the whole, a flag on an eighth. Stems go up below the middle line.
 */
internal fun DrawScope.drawNoteHead(x: Float, cy: Float, h: Float, value: NoteValue, stemUp: Boolean, color: Color, stroke: Float) {
    val head = Size(h * 2.6f, h * 1.85f)
    val hollow = value == NoteValue.Whole || value == NoteValue.Half
    rotate(-20f, pivot = Offset(x, cy)) {
        drawOval(
            color,
            topLeft = Offset(x - head.width / 2, cy - head.height / 2),
            size = head,
            style = if (hollow) Stroke(stroke * 2.2f) else androidx.compose.ui.graphics.drawscope.Fill,
        )
    }
    if (value == NoteValue.Whole) return
    val stemX = if (stemUp) x + head.width / 2 - stroke else x - head.width / 2 + stroke
    val stemEnd = if (stemUp) cy - h * 7 else cy + h * 7
    drawLine(color, Offset(stemX, cy), Offset(stemX, stemEnd), stroke * 1.4f)
    if (value == NoteValue.Eighth) {
        // A single flag curving away from the stem's end.
        val dir = if (stemUp) 1f else -1f
        drawLine(color, Offset(stemX, stemEnd), Offset(stemX + h * 1.6f, stemEnd + dir * h * 2.6f), stroke * 2f)
    }
}
