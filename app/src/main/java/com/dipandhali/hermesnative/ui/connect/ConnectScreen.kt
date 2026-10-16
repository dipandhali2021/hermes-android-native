package com.dipandhali.hermesnative.ui.connect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.R
import com.dipandhali.hermesnative.ui.theme.HermesMotion

/**
 * Where the user points the app at their server.
 *
 * The copy here is the feature. A self-hosted app's most common first failure is
 * an unreachable server, and "connection failed" sends people to the wrong place.
 * So the error names the specific cause and the help section prints the exact
 * command that fixes the most common one — the server binds to loopback by
 * default, which means nothing works from a phone until it is published.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    viewModel: ConnectViewModel = hiltViewModel(),
    initialUrl: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialUrl) { viewModel.prefill(initialUrl) }
    LaunchedEffect(state.connected) { if (state.connected) onConnected() }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            Icon(
                painter = painterResource(R.drawable.ic_hermes_agent),
                contentDescription = "Hermes",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "Connect to your Hermes WebUI server",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::onUrlChange,
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.10:8787") },
                    singleLine = true,
                    isError = state.error != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                AnimatedVisibility(
                    visible = state.needsPassword,
                    enter = expandVertically(HermesMotion.spatial()) + fadeIn(),
                    exit = shrinkVertically(HermesMotion.spatial()) + fadeOut(),
                ) {
                    PasswordField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        isError = state.error != null,
                    )
                }

                AnimatedVisibility(visible = state.error != null) {
                    state.error?.let { ErrorCard(it) }
                }

                Button(
                    onClick = viewModel::onSubmit,
                    enabled = state.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (state.isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(if (state.needsPassword) "Sign in" else "Connect")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            ServerHelp()
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Password") },
        singleLine = true,
        isError = isError,
        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * The "how do I reach my server" disclosure.
 *
 * Collapsed by default so it does not bury the form, but it is the answer to the
 * single most likely problem, and it belongs where the user hits it rather than in
 * a README they are not reading on their phone.
 */
@Composable
private fun ServerHelp() {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = HermesMotion.spatialFast(),
        label = "help-chevron",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .animateContentSize(HermesMotion.contentSize),
    ) {
        Column {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Can't reach your server?",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(HermesMotion.spatial()) + fadeIn(),
                exit = shrinkVertically(HermesMotion.spatial()) + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    HelpItem(
                        title = "The server only listens on its own machine",
                        body = "By default Hermes binds to localhost, so nothing on your " +
                            "phone can reach it. Publish it with Tailscale, which gives you " +
                            "a real HTTPS address and keeps the traffic encrypted.",
                        command = "tailscale serve --bg 8787",
                    )
                    HelpItem(
                        title = "On the same Wi-Fi?",
                        body = "Bind it to your network and always set a password, since " +
                            "anything on that network could otherwise reach it.",
                        command = "HERMES_WEBUI_HOST=0.0.0.0 \\\n" +
                            "HERMES_WEBUI_PASSWORD=your-secret ./start.sh",
                    )
                    HelpItem(
                        title = "Sending a password over plain http://",
                        body = "It travels unencrypted. Prefer the Tailscale address above; " +
                            "only use http:// on a network you trust.",
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpItem(title: String, body: String, command: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (command != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = command,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}
