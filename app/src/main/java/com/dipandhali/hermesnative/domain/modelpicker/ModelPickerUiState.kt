package com.dipandhali.hermesnative.domain.modelpicker

/**
 * Transient state of the model picker.
 *
 * Shared by the chat and home ViewModels: both expose the same sheet over the
 * same catalog, and only what a pick *means* differs (a session change versus
 * the default for new chats). The catalog is built once per ViewModel pass.
 */
data class ModelPickerUiState(
    val open: Boolean = false,
    val catalog: PickerCatalog? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)
