package com.udl.oud.tuner

import kotlinx.coroutines.flow.MutableStateFlow

/** Instrument settings shared by every screen: the tuner sets them, the fretboard follows. */
object OudSettings {
    val tuning = MutableStateFlow(Tunings.all.first())
    val a4 = MutableStateFlow(440)
}
