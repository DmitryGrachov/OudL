package com.udl.oud

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.udl.oud.tuner.OudSettings
import com.udl.oud.ui.OudApp
import com.udl.oud.ui.theme.UdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OudSettings.init(this)
        enableEdgeToEdge()
        // The screen must not dim while both hands are on the instrument.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            UdTheme {
                OudApp()
            }
        }
    }
}
