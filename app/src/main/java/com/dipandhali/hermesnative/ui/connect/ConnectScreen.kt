package com.dipandhali.hermesnative.ui.connect

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SmartToy
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.ColorFilter
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
import com.dipandhali.hermesnative.data.settings.BackendMode
import com.dipandhali.hermesnative.ui.theme.HermesMotion

/**
 * The Connect screen.
 *
 * One form with two honest backends. WebUI is the full client; the direct agent
 * is the agent's own API server and therefore needs a different address and a
 * Bearer key. The selector is the only place that choice is made — the rest of
 * the app reads the saved mode from [ServerState] and adapts its capabilities.
 *
 * The copy here is the feature. A self-hosted app's most common first failure is
 * pointing at the wrong server, and "connection failed" sends people to the wrong
 * place. So the mode cards say what each mode includes, and the error names the
 * specific cause.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    viewModel: ConnectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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

            // The combined Hermes Agent lockup (mark + wordmark), matching the
            // HermesAgent.Combine artwork.
            Image(
                painter = painterResource(R.drawable.ic_hermes_agent),
                contentDescription = "Hermes Agent",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .height(48.dp)
                    .aspectRatio(73f / 24f),
            )
            Text(
                text = "Connect to Hermes",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Choose how this device reaches your agent.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ModeSelector(
                    mode = state.mode,
                    onModeChange = viewModel::onModeChange,
                )

                ModeSummary(mode = state.mode)

                AnimatedContent(
                    targetState = state.mode,
                    transitionSpec = {
                        val direction = if (targetState == BackendMode.AGENT) 1 else -1
                        (
                            slideInHorizontally(HermesMotion.spatial()) { width ->
                                direction * width / 8
                            } + fadeIn(HermesMotion.effects())
                            ) togetherWith (
                            slideOutHorizontally(HermesMotion.spatial()) { width ->
                                -direction * width / 8
                            } + fadeOut(HermesMotion.effects())
                            )
                    },
                    label = "connect-form",
                ) { mode ->
                    when (mode) {
                        BackendMode.WEBUI -> WebUiFields(
                            state = state,
                            onUrlChange = viewModel::onUrlChange,
                            onPasswordChange = viewModel::onPasswordChange,
                            onSubmit = viewModel::onSubmit,
                        )

                        BackendMode.AGENT -> AgentFields(
                            state = state,
                            onUrlChange = viewModel::onUrlChange,
                            onApiKeyChange = viewModel::onApiKeyChange,
                            onSubmit = viewModel::onSubmit,
                        )
                    }
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
                        Text(
                            when {
                                state.needsPassword -> "Sign in"
                                state.mode == BackendMode.AGENT -> "Connect to agent"
                                else -> "Connect to WebUI"
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            ServerHelp(mode = state.mode)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    mode: BackendMode,
    onModeChange: (BackendMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == BackendMode.WEBUI,
            onClick = { onModeChange(BackendMode.WEBUI) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Dashboard,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            label = { Text("WebUI") },
        )
        SegmentedButton(
            selected = mode == BackendMode.AGENT,
            onClick = { onModeChange(BackendMode.AGENT) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            label = { Text("Direct agent") },
        )
    }
}

/**
 * A quiet capability line under the selector, animated on the effects spring so
 * the copy change reads as one control changing state, not a new screen.
 */
@Composable
private fun ModeSummary(mode: BackendMode) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(HermesMotion.contentSize),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (mode == BackendMode.AGENT) {
                    Icons.Rounded.SmartToy
                } else {
                    Icons.Rounded.Dashboard
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        fadeIn(HermesMotion.effects()) togetherWith fadeOut(HermesMotion.effects())
                    },
                    label = "mode-title",
                ) { current ->
                    Text(
                        text = if (current == BackendMode.AGENT) {
                            "Straight to the agent"
                        } else {
                            "Through the WebUI"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        fadeIn(HermesMotion.effects()) togetherWith fadeOut(HermesMotion.effects())
                    },
                    label = "mode-body",
                ) { current ->
                    Text(
                        text = if (current == BackendMode.AGENT) {
                            "Chat, sessions, tools and approvals. Uploads, files, voice and " +
                                "profiles stay in WebUI mode."
                        } else {
                            "The full client: chat, uploads, files, voice, profiles and the " +
                                "model catalog."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WebUiFields(
    state: ConnectUiState,
    onUrlChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = state.webuiUrl,
            onValueChange = onUrlChange,
            label = { Text("WebUI address") },
            placeholder = { Text("http://192.168.1.10:8787") },
            singleLine = true,
            isError = state.error != null,
            supportingText = { Text("The full Hermes WebUI, usually port 8787.") },
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
            SecretField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = "Password",
                leadingIcon = Icons.Rounded.Lock,
                isError = state.error != null,
                imeAction = ImeAction.Done,
                onDone = onSubmit,
            )
        }
    }
}

@Composable
private fun AgentFields(
    state: ConnectUiState,
    onUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = state.agentUrl,
            onValueChange = onUrlChange,
            label = { Text("Agent API address") },
            placeholder = { Text("https://hermes-agent.example.com") },
            singleLine = true,
            isError = state.error != null,
            supportingText = {
                Text("The agent's own API, usually port 8642 — not the WebUI on 8787.")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        SecretField(
            value = state.apiKey,
            onValueChange = onApiKeyChange,
            label = "API key",
            leadingIcon = Icons.Rounded.Key,
            isError = state.error != null,
            supportingText = "From API_SERVER_KEY. Stored encrypted on this device.",
            imeAction = ImeAction.Done,
            onDone = onSubmit,
        )
    }
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    isError: Boolean,
    imeAction: ImeAction,
    onDone: () -> Unit,
    supportingText: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide $label" else "Show $label",
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
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
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
private fun ServerHelp(mode: BackendMode) {
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
                    text = if (mode == BackendMode.AGENT) {
                        "Can't reach the agent?"
                    } else {
                        "Can't reach your server?"
                    },
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
                    if (mode == BackendMode.AGENT) {
                        AgentHelp()
                    } else {
                        WebUiHelp()
                    }
                }
            }
        }
    }
}

@Composable
private fun WebUiHelp() {
    HelpItem(
        title = "The server only listens on its own machine",
        body = "By default Hermes binds to localhost, so nothing on your phone can " +
            "reach it. Publish it with Tailscale, which gives you a real HTTPS address " +
            "and keeps the traffic encrypted.",
        command = "tailscale serve --bg 8787",
    )
    HelpItem(
        title = "On the same Wi-Fi?",
        body = "Bind it to your network and always set a password, since anything on " +
            "that network could otherwise reach it.",
        command = "HERMES_WEBUI_HOST=0.0.0.0 \\\n" +
            "HERMES_WEBUI_PASSWORD=your-secret ./start.sh",
    )
    HelpItem(
        title = "Sending a password over plain http://",
        body = "It travels unencrypted. Prefer the Tailscale address above; only use " +
            "http:// on a network you trust.",
    )
}

@Composable
private fun AgentHelp() {
    HelpItem(
        title = "Use the agent's port, not the WebUI",
        body = "Direct mode talks to the agent API server (usually 8642). The WebUI on " +
            "8787 does not serve /v1/runs and does not accept an API key.",
    )
    HelpItem(
        title = "The key is API_SERVER_KEY",
        body = "Enter the same value the agent host was started with. It is sent as " +
            "Authorization: Bearer to that address only.",
        command = "API_SERVER_KEY=your-secret",
    )
    HelpItem(
        title = "What direct mode includes",
        body = "Chat, sessions, tools and approval prompts. Uploads, workspace files, " +
            "voice notes and profiles need the WebUI mode.",
    )
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
