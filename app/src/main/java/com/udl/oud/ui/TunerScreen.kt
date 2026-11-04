@file:OptIn(ExperimentalMaterial3Api::class)

package com.udl.oud.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udl.oud.tuner.Note
import com.udl.oud.tuner.Tuning
import com.udl.oud.tuner.Tunings
import com.udl.oud.tuner.TunerMode
import com.udl.oud.tuner.TunerUiState
import com.udl.oud.tuner.TunerViewModel
import com.udl.oud.ui.theme.InTuneGreen
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TunerRoute(vm: TunerViewModel = viewModel()) {
    val mic = rememberMicPermission()
    if (mic.granted) {
        LifecycleResumeEffect(Unit) {
            @Suppress("MissingPermission")
            vm.start()
            onPauseOrDispose { vm.stop() }
        }
    }

    val state by vm.state.collectAsStateWithLifecycle()
    TunerScreen(
        state = state,
        hasPermission = mic.granted,
        onRequestPermission = mic.request,
        actions = TunerActions(
            onSelectTuning = vm::selectTuning,
            onSetMode = vm::setMode,
            onAuto = vm::setAuto,
            onRestartCourse = vm::restartCourse,
            onCourseTap = vm::onCourseTap,
            onChangeA4 = vm::changeA4,
        ),
    )
}

class TunerActions(
    val onSelectTuning: (Tuning) -> Unit,
    val onSetMode: (TunerMode) -> Unit,
    val onAuto: () -> Unit,
    val onRestartCourse: () -> Unit,
    val onCourseTap: (Int) -> Unit,
    val onChangeA4: (Int) -> Unit,
)

@Composable
fun TunerScreen(
    state: TunerUiState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    actions: TunerActions,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Тюнер уда") }) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 600.dp
            if (!hasPermission) {
                PermissionRequest(onRequestPermission, Modifier.align(Alignment.Center))
            } else if (wide) {
                // Tablet / landscape: gauge on the left, controls on the right.
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Readout(state, Modifier.weight(1f).align(Alignment.CenterVertically))
                    // Scrolls when the controls outgrow a landscape tablet.
                    Controls(
                        state, actions,
                        Modifier.weight(1f).align(Alignment.CenterVertically).verticalScroll(rememberScrollState()),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Readout(state, Modifier.fillMaxWidth())
                    Controls(state, actions, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Readout(state: TunerUiState, modifier: Modifier = Modifier) {
    val cents = state.cents
    val target = state.targetCourse?.let { state.tuning.courses[it] }
    val inTune = state.inTune

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CentsGauge(cents?.toFloat(), state.toleranceCents, Modifier.fillMaxWidth().widthIn(max = 520.dp))
        Row(Modifier.fillMaxWidth().widthIn(max = 520.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("−50", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("+50", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            target?.solfege ?: "—",
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = if (inTune) InTuneGreen else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            target?.let { "${it.latin}${it.octave}" } ?: " ",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        val firstCents = state.firstStringCents
        if (firstCents != null && target != null) {
            Text(
                "Первая струна: ${signedCents(firstCents)} от ${target.solfege}${target.octave}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = hint(state),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            color = when {
                state.error != null -> MaterialTheme.colorScheme.error
                inTune -> InTuneGreen
                cents == null -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.tertiary
            },
        )
        val heard = state.heardNote
        val frequency = state.frequency
        Text(
            if (heard != null && frequency != null) {
                "Слышу: ${heard.solfege}${heard.octave} · ${String.format(Locale.US, "%.1f", frequency)} Гц"
            } else {
                " "
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun hint(state: TunerUiState): String {
    val cents = state.cents
    return when {
        state.error != null -> state.error
        state.mode == TunerMode.Course && state.firstString == null ->
            if (cents == null) "Приглуши одну струну хора и дёрни другую" else "Запоминаю первую струну…"
        state.mode == TunerMode.Course -> when {
            cents == null -> "Теперь приглуши её и дёрни вторую"
            state.inTune -> "Хор чистый ✓"
            cents < 0 -> "Вторая ниже на ${abs(cents).toInt()} ц — подтяни"
            else -> "Вторая выше на ${cents.toInt()} ц — ослабь"
        }
        cents == null -> "Дёрни струну"
        state.inTune -> "Строит ✓"
        cents < 0 -> "Низко на ${abs(cents).toInt()} ц — подтяни"
        else -> "Высоко на ${cents.toInt()} ц — ослабь"
    }
}

internal fun signedCents(cents: Double): String {
    val value = cents.roundToInt()
    return when {
        value > 0 -> "+$value ц"
        value < 0 -> "−${-value} ц"
        else -> "0 ц"
    }
}

/** Half-dial from −50 to +50 cents with a green zone of ±[tolerance] cents. */
@Composable
private fun CentsGauge(cents: Float?, tolerance: Double, modifier: Modifier = Modifier) {
    val needle by animateFloatAsState(
        targetValue = (cents ?: 0f).coerceIn(-50f, 50f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "needle",
    )
    val inTune = cents != null && abs(cents) <= tolerance
    val tickColor = MaterialTheme.colorScheme.outline
    val needleColor = when {
        cents == null -> MaterialTheme.colorScheme.outline
        inTune -> InTuneGreen
        else -> MaterialTheme.colorScheme.tertiary
    }
    val zoneDegrees = (tolerance / 50 * 90).toFloat()

    Canvas(modifier.aspectRatio(2f)) {
        val pivotInset = 12.dp.toPx()
        val radius = min(size.width / 2, size.height - pivotInset) * 0.95f
        val center = Offset(size.width / 2, size.height - pivotInset)
        fun direction(c: Float): Offset {
            val angle = Math.toRadians(270.0 + c / 50.0 * 90.0)
            return Offset(cos(angle).toFloat(), sin(angle).toFloat())
        }

        drawArc(
            color = InTuneGreen.copy(alpha = 0.25f),
            startAngle = 270f - zoneDegrees,
            sweepAngle = zoneDegrees * 2,
            useCenter = true,
            topLeft = center - Offset(radius, radius),
            size = Size(radius * 2, radius * 2),
        )
        for (c in -50..50 step 5) {
            val major = c % 25 == 0
            val dir = direction(c.toFloat())
            drawLine(
                color = if (c == 0) InTuneGreen else tickColor,
                start = center + dir * radius * (if (major) 0.80f else 0.88f),
                end = center + dir * radius,
                strokeWidth = (if (major) 3.dp else 1.5.dp).toPx(),
            )
        }
        drawLine(
            color = needleColor,
            start = center,
            end = center + direction(needle) * radius * 0.95f,
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(needleColor, radius = 8.dp.toPx(), center = center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    state: TunerUiState,
    actions: TunerActions,
    modifier: Modifier = Modifier,
) {
    val courseMode = state.mode == TunerMode.Course
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Режим", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !courseMode,
                onClick = { actions.onSetMode(TunerMode.Tuning) },
                label = { Text("По строю") },
            )
            FilterChip(
                selected = courseMode,
                onClick = { actions.onSetMode(TunerMode.Course) },
                label = { Text("Хор: две струны") },
            )
        }

        Text("Строй", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tunings.arabic.forEach { tuning ->
                FilterChip(
                    selected = tuning == state.tuning,
                    onClick = { actions.onSelectTuning(tuning) },
                    label = { Text("${tuning.name}  (${tuning.courses.joinToString(" ") { it.latin }})") },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (courseMode) "Хор" else "Струны",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (courseMode) {
                TextButton(onClick = actions.onRestartCourse) { Text("Заново") }
            } else {
                FilterChip(
                    selected = state.lockedCourse == null,
                    onClick = actions.onAuto,
                    label = { Text("Авто") },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.tuning.courses.forEachIndexed { index, note ->
                CourseButton(
                    note = note,
                    isTarget = index == state.targetCourse,
                    onClick = { actions.onCourseTap(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            if (courseMode) {
                "Выбери хор. Тюнер запомнит первую струну и покажет, насколько вторая от неё отличается."
            } else {
                "Нажми на струну — услышишь эталон, и тюнер будет ждать только её."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ля4 (A4)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { actions.onChangeA4(-1) }) { Text("−", fontSize = 22.sp) }
            Text("${state.a4} Гц", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { actions.onChangeA4(1) }) { Text("+", fontSize = 22.sp) }
        }
    }
}

@Composable
private fun CourseButton(note: Note, isTarget: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(note.solfege, maxLines = 1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("${note.latin}${note.octave}", fontSize = 11.sp)
        }
    }
    val padding = PaddingValues(horizontal = 2.dp, vertical = 10.dp)
    if (isTarget) {
        Button(onClick = onClick, modifier = modifier, contentPadding = padding) { content() }
    } else {
        FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = padding) { content() }
    }
}
