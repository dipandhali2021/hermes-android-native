package com.dipandhali.hermesnative.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.dipandhali.hermesnative.ui.theme.HermesNativeTheme

@Composable
fun HermesRoot() {
    HermesNativeTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Hermes",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HermesRootPreview() {
    HermesNativeTheme { HermesRoot() }
}
