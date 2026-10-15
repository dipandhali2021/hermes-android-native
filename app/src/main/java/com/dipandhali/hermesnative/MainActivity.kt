package com.dipandhali.hermesnative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dipandhali.hermesnative.ui.nav.HermesRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge with Scaffold-provided insets: the transcript should run
        // under the status bar and the composer above the navigation bar, which is
        // what the reference design does.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { HermesRoot() }
    }
}
