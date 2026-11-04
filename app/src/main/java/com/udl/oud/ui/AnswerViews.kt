@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.udl.oud.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.udl.oud.taqsim.AnswerPhase
import com.udl.oud.taqsim.AnswerVerdict
import com.udl.oud.taqsim.HeardNote
import com.udl.oud.ui.theme.InTuneGreen

/**
 * The main button of an answer exercise — stop while the question plays, "done" while
 * listening, otherwise ask (again) — then the screen's own buttons in [extra].
 */
@Composable
fun AnswerControls(
    phase: AnswerPhase,
    stopShown: Boolean,
    askLabel: String,
    againLabel: String,
    onStop: () -> Unit,
    onFinish: () -> Unit,
    onAsk: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            stopShown -> Button(onClick = onStop) { Text("■ Стоп") }
            phase == AnswerPhase.Listening -> Button(onClick = onFinish) { Text("✋ Готово") }
            else -> Button(onClick = onAsk) { Text(if (phase == AnswerPhase.Idle) askLabel else againLabel) }
        }
        extra()
    }
}

/**
 * What was played and how it went: the notes, green inside the scale and orange outside it, then
 * each check passed or not; with [summary], a word on the whole answer.
 */
@Composable
fun AnswerResult(notes: List<HeardNote>, verdict: AnswerVerdict?, name: (HeardNote) -> String, summary: Boolean = false) {
    if (notes.isNotEmpty()) {
        Text(
            buildAnnotatedString {
                notes.forEachIndexed { i, n ->
                    val outside = verdict?.outside?.contains(i) ?: false
                    withStyle(SpanStyle(color = if (outside) MaterialTheme.colorScheme.tertiary else InTuneGreen, fontWeight = FontWeight.SemiBold)) {
                        append(" ${name(n)} ")
                    }
                }
            },
            style = MaterialTheme.typography.titleLarge,
        )
    }
    val v = verdict ?: return
    v.points.forEach { p ->
        Text(
            "${if (p.passed) "✓" else "✗"}  ${p.text}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (p.passed) InTuneGreen else MaterialTheme.colorScheme.tertiary,
        )
    }
    if (summary && v.points.size == 4) {
        Text(
            if (v.passed == 4) "Отличный ответ! Попробуй следующий вопрос или ответь иначе." else "${v.passed} из 4. Послушай пример и попробуй ещё раз.",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
