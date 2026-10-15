package com.dipandhali.hermesnative.data.panels

import com.dipandhali.hermesnative.core.chat.TodoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The live session checklist.
 *
 * A singleton rather than view-model state because the todos have no HTTP
 * endpoint to re-fetch from: they arrive only on the chat stream, and the stream
 * lives in a different screen's view model. Anything scoped to the panel
 * destination would be destroyed on back and show an empty list on the next
 * visit, with no way to recover it.
 */
@Singleton
class TodoStore @Inject constructor() {

    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos.asStateFlow()

    fun update(items: List<TodoItem>) {
        _todos.value = items
    }

    fun clear() {
        _todos.value = emptyList()
    }
}
