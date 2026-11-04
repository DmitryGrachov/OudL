package com.udl.oud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.udl.oud.taqsim.NoteValue
import com.udl.oud.taqsim.ScoreLayout
import com.udl.oud.taqsim.ScoreLine
import com.udl.oud.taqsim.TaqsimUiState
import com.udl.oud.taqsim.TaqsimViewModel
import kotlin.math.abs

/** The whole taqsim in notes, full screen: play it and follow along, or tap a phrase to hear it. */
@Composable
fun TaqsimScoreDialog(state: TaqsimUiState, vm: TaqsimViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Ноты таксима: ${state.title}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.playing) {
                        Button(onClick = vm::stop) { Text("■ Стоп") }
                    } else {
                        Button(onClick = vm::playAll) { Text("▶ Слушать") }
                    }
                    TextButton(onClick = onDismiss) { Text("✕ Закрыть") }
                }
                Text(
                    "Ритм свободный — длительности записаны приблизительно: целая в конце, половинные на опорах, " +
                        "восьмые в быстрых пассажах. Записано октавой выше звука. Нажми на фразу — она прозвучит. " +
                        "Цветом выделена тема, над нотами — украшения, ’ — дыхание.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TaqsimScore(state, onPhrase = vm::playPhrase, modifier = Modifier.fillMaxSize().padding(top = 8.dp))
            }
        }
    }
}

/** The score itself: lines of staff, the note sounding lit, scrolled to follow the playing. */
@Composable
fun TaqsimScore(state: TaqsimUiState, onPhrase: (Int) -> Unit, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val themeInk = MaterialTheme.colorScheme.primary
    val lit = MaterialTheme.colorScheme.tertiary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ornaments = state.ornaments
    BoxWithConstraints(modifier) {
        val h = with(LocalDensity.current) { HALF_SPACE.toPx() }
        val widthUnits = with(LocalDensity.current) { maxWidth.toPx() } / h
        val lines = remember(state.taqsim, ornaments, widthUnits) { ScoreLayout.layout(state.taqsim, ornaments, widthUnits) }
        val listState = rememberLazyListState()
        // Follow the playing note from line to line.
        val playingLine = state.playingNote?.let { n -> lines.indexOfFirst { l -> l.items.any { it.note == n } } }
        LaunchedEffect(playingLine) { if (playingLine != null && playingLine >= 0) listState.animateScrollToItem(playingLine) }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(lines) { _, line ->
                val heightDp = with(LocalDensity.current) { ((line.high - line.low) * h).toDp() } + 8.dp
                Canvas(
                    Modifier.fillMaxWidth().height(heightDp).pointerInput(line) {
                        detectTapGestures { tap ->
                            line.items.minByOrNull { abs(it.x * h - tap.x) }?.let { onPhrase(it.phrase) }
                        }
                    },
                ) {
                    drawScoreLine(line, h, state, lineColor, ink, themeInk, lit, muted, measurer)
                }
            }
        }
    }
}

private val HALF_SPACE = 7.dp

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScoreLine(
    line: ScoreLine,
    h: Float,
    state: TaqsimUiState,
    lineColor: Color,
    ink: Color,
    themeInk: Color,
    lit: Color,
    muted: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    fun y(position: Int) = (line.high - position) * h
    val stroke = maxOf(1f, h / 7)
    val right = size.width - h
    for (p in 0..8 step 2) drawLine(lineColor, Offset(0f, y(p)), Offset(right, y(p)), stroke)
    drawClef(0f, y(2), h, lineColor, octaveClef = true, measurer)

    line.bars.forEach { bx ->
        val x = bx * h
        if (x < right) drawLine(lineColor.copy(alpha = 0.6f), Offset(x, y(8)), Offset(x, y(0)), stroke)
    }
    val label = TextStyle(fontSize = (h * 1.7f).toSp(), color = muted, fontWeight = FontWeight.SemiBold)
    val signStyle = TextStyle(fontSize = (h * 1.9f).toSp(), color = muted)

    line.items.forEach { item ->
        val x = item.x * h
        val p = item.staff.position
        val cy = y(p)
        val playing = state.playingNote == item.note
        val color = when {
            playing -> lit
            item.theme -> themeInk
            else -> ink
        }
        if (playing) drawCircle(lit.copy(alpha = 0.25f), radius = h * 2.6f, center = Offset(x, cy))
        for (l in item.staff.ledgerLines) drawLine(lineColor, Offset(x - h * 2.1f, y(l)), Offset(x + h * 2.1f, y(l)), stroke)
        drawNoteHead(x, cy, h, item.value, stemUp = p < 4, color = color, stroke = stroke)
        drawAccidental(item.staff, Offset(x - h * 3.2f, cy), h, color, stroke, measurer)

        // Above the staff: the phrase's number at its start, ornament signs, breaths.
        val above = y(maxOf(p + 6, 11))
        if (item.phraseStart) {
            val phrase = state.taqsim.phrases[item.phrase]
            val text = measurer.measure("${item.phrase + 1}. ${phrase.section.title}", label)
            drawText(text, topLeft = Offset(x - h * 2f, y(line.high - 1) - text.size.height / 2f))
        }
        item.sign?.let { s ->
            val t = measurer.measure(s, signStyle)
            drawText(t, topLeft = Offset(x - t.size.width / 2f, above - t.size.height))
        }
        if (item.breath) {
            val t = measurer.measure("’", TextStyle(fontSize = (h * 3f).toSp(), color = ink, fontWeight = FontWeight.Bold))
            drawText(t, topLeft = Offset(x + h * 2.4f, y(10) - t.size.height / 2f))
        }
        // A fermata over the last note of the qafla.
        if (item.value == NoteValue.Whole) {
            val fy = y(maxOf(p + 4, 10))
            drawArc(ink, 180f, 180f, false, Offset(x - h * 1.8f, fy - h * 1.2f), androidx.compose.ui.geometry.Size(h * 3.6f, h * 2.4f), style = Stroke(stroke * 1.5f))
            drawCircle(ink, radius = h * 0.35f, center = Offset(x, fy - h * 0.2f))
        }
    }
}
