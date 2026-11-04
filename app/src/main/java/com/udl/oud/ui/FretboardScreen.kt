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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udl.oud.fretboard.FretboardUiState
import com.udl.oud.fretboard.FretboardViewModel
import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.StaffNote
import com.udl.oud.fretboard.Scale
import com.udl.oud.fretboard.ScaleGroup
import com.udl.oud.fretboard.WesternScales
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Pitch
import com.udl.oud.ui.theme.InTuneGreen
import kotlin.math.abs

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
    // The maqam tab can also show one jins at a time (the building blocks on their own), or the
    // moves between them (talwin).
    var view by rememberSaveable { mutableStateOf(0) }
    if (mode == BoardMode.Maqamat) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 24.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Макамы", "Джинсы", "Тальвин").forEachIndexed { i, title ->
                    FilterChip(view == i, onClick = { view = i }, label = { Text(title) })
                }
            }
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                when (view) {
                    1 -> JinsRoute(onOpenMaqam = { m ->
                        vm.select(m)
                        view = 0
                    })
                    2 -> TalwinRoute()
                    else -> MaqamBoard(vm, state, mode, mic)
                }
            }
        }
        return
    }
    MaqamBoard(vm, state, mode, mic)
}

@Composable
private fun MaqamBoard(vm: FretboardViewModel, state: FretboardUiState, mode: BoardMode, mic: MicPermission) {
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
        // A narrow staff in portrait leaves the board its width.
        val staffWidth = if (maxWidth >= 900.dp) 200.dp else 120.dp
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
                Row(boardModifier) {
                    FretboardCanvas(state, onTap = vm::play, modifier = Modifier.weight(1f).fillMaxHeight())
                    if (state.showStaff) BoardStaff(state, Modifier.width(staffWidth).fillMaxHeight().padding(start = 8.dp))
                }
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
        listOfNotNull(
            NoteLabels.Solfege to "До-Ре-Ми",
            NoteLabels.Latin to "C-D-E",
            NoteLabels.Arabic to "Арабские",
        ).forEach { (labels, title) ->
            FilterChip(state.labels == labels, onClick = { vm.setLabels(labels) }, label = { Text(title) })
        }
        Text("·", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.outline)
        // A scale decides by itself which notes are shown, so the note layers only make sense without one.
        if (!scaleMode) {
            FilterChip(state.showAccidentals, onClick = vm::toggleAccidentals, label = { Text("♯ ♭") })
            run {
                FilterChip(state.showQuarterTones, onClick = vm::toggleQuarterTones, label = { Text("Четвертитоны") })
            }
        }
        FilterChip(state.showFingers, onClick = vm::toggleFingers, label = { Text("Пальцы") })
        FilterChip(state.showStaff, onClick = vm::toggleStaff, label = { Text("Ноты") })
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

/**
 * The note on a staff beside the board: what the app is sounding, else what the drill asks for,
 * else what the mic hears. A maqam degree keeps the maqam's own spelling.
 */
@Composable
private fun BoardStaff(state: FretboardUiState, modifier: Modifier) {
    val heard = state.heard
    val heardStep = heard?.let { (positions, _) ->
        positions.firstOrNull()?.pitch?.step
            ?: state.frequency?.let { Pitch.nearest(it, state.a4.toDouble(), state.snapToQuarterTones).step }
    }
    val step = state.playingStep ?: state.exercise?.target ?: heardStep
    val scale = state.degrees
    val note = step?.let { s -> scale?.get(s.mod(24))?.spelling?.let { StaffNote.arabic(s, it) } ?: StaffNote.arabic(s) }
    val color = when {
        step == null -> MaterialTheme.colorScheme.onSurfaceVariant
        step != heardStep || heard == null -> MaterialTheme.colorScheme.primary
        abs(heard.second) <= FretboardUiState.IN_TUNE_CENTS -> InTuneGreen
        else -> MaterialTheme.colorScheme.onSurface
    }
    NoteStaff(
        note,
        step?.let { state.labelFor(Pitch(it), scale) },
        color,
        hint = "записано октавой выше звука",
        octaveClef = true,
        modifier = modifier,
    )
}
