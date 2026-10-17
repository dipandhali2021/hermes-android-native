package com.dipandhali.hermesnative.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.ProfileCreateRequest
import com.dipandhali.hermesnative.domain.composer.ProfilesUiState
import com.dipandhali.hermesnative.domain.modelpicker.ModelRow
import com.dipandhali.hermesnative.domain.modelpicker.PickerCatalog

/**
 * Create-profile form, web parity in four fields: name (lowercase, server
 * regex), clone-config checkbox, optional model with a catalog dropdown, and
 * the base URL + API key pair for a bring-your-own endpoint. `clone_from`
 * never rides the request — the web does not send it either; the server's
 * own rules apply the rest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCreateSheet(
    state: ProfilesUiState,
    catalog: PickerCatalog?,
    onSubmit: (ProfileCreateRequest) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf("") }
    var cloneConfig by remember { mutableStateOf(true) }
    var modelText by remember { mutableStateOf("") }
    var modelProvider by remember { mutableStateOf<String?>(null) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to profiles")
                }
                Text("New profile", style = MaterialTheme.typography.titleMedium)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.lowercase() },
                    label = { Text("Name") },
                    placeholder = { Text("lowercase-name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = cloneConfig, onCheckedChange = { cloneConfig = it })
                    Text(
                        text = "Clone config from the active profile",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                OutlinedTextField(
                    value = modelText,
                    onValueChange = {
                        modelText = it
                        modelProvider = null
                    },
                    label = { Text("Default model (optional)") },
                    placeholder = { Text("Use the profile default") },
                    singleLine = true,
                    trailingIcon = {
                        if (!catalog?.flat.isNullOrEmpty()) {
                            IconButton(onClick = { modelMenuOpen = true }) {
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Pick a model")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    DropdownMenu(
                        expanded = modelMenuOpen,
                        onDismissRequest = { modelMenuOpen = false },
                    ) {
                        catalog?.flat.orEmpty().forEach { row: ModelRow ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = listOfNotNull(
                                            row.displayName,
                                            row.model.takeIf { it != row.displayName },
                                        ).joinToString(" · "),
                                        maxLines = 1,
                                    )
                                },
                                onClick = {
                                    modelText = row.model
                                    modelProvider = row.providerId
                                    modelMenuOpen = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (optional)") },
                    placeholder = { Text("https://api.example.org/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        autoCorrect = false,
                        keyboardType = KeyboardType.Password,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                formError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        val trimmedName = name.trim()
                        profileFormError(trimmedName, baseUrl.trim())?.let { complaint ->
                            formError = complaint
                        } ?: run {
                            formError = null
                            onSubmit(
                                ProfileCreateRequest(
                                    name = trimmedName,
                                    cloneConfig = cloneConfig,
                                    // Unset extras serialize to absent keys; the
                                    // API key must never ride a request it does
                                    // not belong to.
                                    baseUrl = baseUrl.trim().takeIf { it.isNotBlank() },
                                    apiKey = apiKey.takeIf { it.isNotBlank() },
                                    defaultModel = modelText.trim().takeIf { it.isNotBlank() },
                                    // Free-typed ids let the server split the
                                    // provider pair; dropdown picks carry their
                                    // provider explicitly.
                                    modelProvider = modelProvider,
                                ),
                            )
                        }
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Create profile")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The server's own profile-name rule, applied client-side to save a roundtrip. */
private val profileNameRegex = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

/** First client-side complaint, or null when the request can go out. */
internal fun profileFormError(name: String, baseUrl: String): String? = when {
    !profileNameRegex.matches(name) ->
        "Name must be lowercase letters, digits, dashes or underscores."
    baseUrl.isNotBlank() && !baseUrl.startsWith("http") ->
        "Base URL must start with http:// or https://."
    else -> null
}

