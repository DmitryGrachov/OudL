package com.udl.oud.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Destination(val title: String, val glyph: String) {
    Tuner("Тюнер", "♪"),
    Fretboard("Гриф", "≡"),
    Maqamat("Макамы", "♮"),
    Scales("Гаммы", "♯"),
    Practice("Практика", "◉"), // drone and rhythm; the full name doesn't fit the rail
}

/** Top-level screens: a side rail on tablets, a bottom bar on phones. */
@Composable
fun OudApp() {
    var current by rememberSaveable { mutableStateOf(Destination.Tuner) }
    val screen: @Composable () -> Unit = {
        when (current) {
            Destination.Tuner -> TunerRoute()
            Destination.Fretboard -> FretboardRoute()
            Destination.Maqamat -> MaqamRoute()
            Destination.Scales -> WesternScaleRoute()
            Destination.Practice -> PracticeRoute()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    Destination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = current == destination,
                            onClick = { current = destination },
                            icon = { Text(destination.glyph, fontSize = 22.sp) },
                            label = { Text(destination.title) },
                        )
                    }
                }
                Box(Modifier.weight(1f)) { screen() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { screen() }
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination,
                            onClick = { current = destination },
                            icon = { Text(destination.glyph, fontSize = 22.sp) },
                            label = { Text(destination.title) },
                        )
                    }
                }
            }
        }
    }
}
