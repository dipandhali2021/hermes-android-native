package com.dipandhali.hermesnative.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.BuildConfig
import com.dipandhali.hermesnative.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Server")
            ListItem(
                headlineContent = { Text("Address") },
                supportingContent = {
                    Text(
                        text = state.serverUrl ?: "Not connected",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Change server") },
                supportingContent = { Text("Sign out and connect to a different server") },
                modifier = Modifier.clickable { confirmSignOut = true },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeader("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = { Text("Follow the system, or force light or dark") },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size,
                            ),
                        ) {
                            Text(mode.label())
                        }
                    }
                }
            }

            // Material You only colours a palette on Android 12+; offering the
            // switch below that would be a control that silently does nothing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text("Dynamic colour") },
                    supportingContent = {
                        Text("Match the palette to your wallpaper")
                    },
                    trailingContent = {
                        Switch(
                            checked = state.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeader("About")
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
            ListItem(
                headlineContent = { Text("Build") },
                supportingContent = { Text(if (BuildConfig.DEBUG) "Debug" else "Release") },
            )

            TextButton(
                onClick = { confirmSignOut = true },
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            ) {
                Icon(Icons.Rounded.Logout, contentDescription = null)
                Text("Sign out", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("The saved server and session will be cleared from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
