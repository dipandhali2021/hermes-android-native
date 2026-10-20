package com.dipandhali.hermesnative.kanban

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanBoardSummary
import com.dipandhali.hermesnative.core.model.KanbanBoardsResponse
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.ui.kanban.KanbanBoardsEffect
import com.dipandhali.hermesnative.ui.kanban.KanbanBoardsField
import com.dipandhali.hermesnative.ui.kanban.KanbanBoardsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanBoardsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = mutableListOf<KanbanBoardsViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.forEach {
            it.viewModelScope.cancel()
        }
        Dispatchers.resetMain()
    }

    private fun TestScope.started(
        fake: FakeKanbanRepository,
        slug: String? = null,
    ): KanbanBoardsViewModel {
        val handle = SavedStateHandle(
            buildMap { slug?.let { put("slug", it) } },
        )
        return KanbanBoardsViewModel(fake, handle).also {
            viewModels += it
            it.start()
            advanceUntilIdle()
        }
    }

    private fun TestScope.collectEffects(vm: KanbanBoardsViewModel): List<KanbanBoardsEffect> {
        val effects = mutableListOf<KanbanBoardsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { effects += it }
        }
        return effects
    }

    private fun summary(
        slug: String = "alpha",
        name: String? = "Alpha",
        description: String? = "A board",
        icon: String? = "🚀",
        color: String? = "#455A64",
        defaultWorkdir: String? = "/work/alpha",
        counts: Map<String, Int> = emptyMap(),
    ) = KanbanBoardSummary(
        slug = slug,
        name = name,
        description = description,
        icon = icon,
        color = color,
        defaultWorkdir = defaultWorkdir,
        counts = counts,
        total = counts.values.sum(),
    )

    private fun boards(vararg boards: KanbanBoardSummary, current: String = boards.first().slug) =
        KanbanBoardsResponse(boards = boards.toList(), current = current)

    @Test
    fun `new board slug suggestion normalizes the name`() {
        assertEquals("my-great-board", KanbanBoardsViewModel.suggestSlug("  My Great Board!! "))
        assertEquals("a-b", KanbanBoardsViewModel.suggestSlug("A___B"))
        assertEquals("", KanbanBoardsViewModel.suggestSlug("   "))
    }

    @Test
    fun `create sends switch true and saved effect`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        fake.createBoardHandler = { request ->
            Result.success(
                summary(
                    slug = request.slug,
                    name = request.name,
                    description = request.description,
                    icon = request.icon,
                    color = request.color,
                    defaultWorkdir = request.defaultWorkdir,
                ),
            )
        }
        val vm = started(fake)
        val effects = collectEffects(vm)

        vm.onNameChanged("My Board")
        vm.onDescriptionChanged("Hello")
        vm.onIconChanged("🚀")
        vm.onColorSelected("#00695C")
        vm.onDefaultWorkdirChanged("/work/mine")
        vm.onSave()
        advanceUntilIdle()

        val request = fake.createBoardCalls.single()
        assertEquals("my-board", request.slug)
        assertEquals("My Board", request.name)
        assertEquals("Hello", request.description)
        assertEquals("🚀", request.icon)
        assertEquals("#00695C", request.color)
        assertEquals("/work/mine", request.defaultWorkdir)
        assertTrue(request.switch)
        assertEquals(listOf(KanbanBoardsEffect.Saved), effects)
    }

    @Test
    fun `server validation failure stays in the form`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        fake.createBoardHandler = {
            Result.failure(ApiException(DataError.Server(400, "slug is already taken")))
        }
        val vm = started(fake)
        vm.onNameChanged("Taken")
        assertFalse(vm.state.value.saving)

        vm.onSave()
        advanceUntilIdle()

        assertEquals("slug is already taken", vm.state.value.error)
        assertFalse(vm.state.value.saving)
        assertTrue(vm.state.value.fieldErrors.isEmpty())
    }

    @Test
    fun `settings prefill uses the board summary and saves all fields`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val alpha = summary(counts = mapOf("ready" to 2, "running" to 1))
        fake.boardsHandler = { Result.success(boards(alpha, current = "alpha")) }
        fake.updateBoardHandler = { _, _ -> Result.success(alpha) }
        val vm = started(fake, slug = "alpha")
        val effects = collectEffects(vm)

        assertEquals("Alpha", vm.state.value.name)
        assertEquals("A board", vm.state.value.description)
        assertEquals("🚀", vm.state.value.icon)
        assertEquals("#455A64", vm.state.value.color)
        assertEquals("/work/alpha", vm.state.value.defaultWorkdir)
        assertEquals(1, vm.state.value.runningCount)
        assertNull(vm.state.value.archiveDisabledReason)

        vm.onNameChanged("Alpha renamed")
        vm.onDescriptionChanged("Updated")
        vm.onIconChanged("🧪")
        vm.onColorSelected("#7B1FA2")
        vm.onDefaultWorkdirChanged("/work/changed")
        vm.onSave()
        advanceUntilIdle()

        val (slug, patch) = fake.updateBoardCalls.single()
        assertEquals("alpha", slug)
        assertEquals("Alpha renamed", patch.name)
        assertEquals("Updated", patch.description)
        assertEquals("🧪", patch.icon)
        assertEquals("#7B1FA2", patch.color)
        assertEquals("/work/changed", patch.defaultWorkdir)
        assertEquals(listOf(KanbanBoardsEffect.Saved), effects)
    }

    @Test
    fun `archive warns when running workers and requires a second confirmation`() =
        runTest(dispatcher) {
            val fake = FakeKanbanRepository()
            val alpha = summary(counts = mapOf("running" to 2))
            fake.boardsHandler = { Result.success(boards(alpha)) }
            fake.archiveBoardHandler = { Result.success("alpha") }
            val vm = started(fake, slug = "alpha")

            vm.onArchiveClicked()
            assertTrue(vm.state.value.archiveDialog)
            assertFalse(vm.state.value.archiveSecondConfirmation)

            vm.onArchiveConfirmed()
            assertTrue(vm.state.value.archiveSecondConfirmation)
            assertTrue(fake.archiveBoardCalls.isEmpty())

            vm.onArchiveConfirmed()
            advanceUntilIdle()

            assertEquals(listOf("alpha"), fake.archiveBoardCalls)
        }

    @Test
    fun `default board archive is disabled`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val default = summary(slug = "default", name = "Default")
        fake.boardsHandler = { Result.success(boards(default)) }
        val vm = started(fake, slug = "default")

        assertEquals(
            "The default board can't be archived.",
            vm.state.value.archiveDisabledReason,
        )
        vm.onArchiveClicked()
        vm.onArchiveConfirmed()
        advanceUntilIdle()

        assertFalse(vm.state.value.archiveDialog)
        assertTrue(fake.archiveBoardCalls.isEmpty())
    }

    @Test
    fun `archive success returns the new current`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val beta = summary(slug = "beta", name = "Beta")
        fake.boardsHandler = { Result.success(boards(beta)) }
        fake.archiveBoardHandler = { Result.success("default") }
        val vm = started(fake, slug = "beta")
        val effects = collectEffects(vm)

        vm.onArchiveClicked()
        vm.onArchiveConfirmed()
        advanceUntilIdle()

        assertEquals(listOf(KanbanBoardsEffect.Archived("default")), effects)
        assertFalse(vm.state.value.archiving)
    }

    @Test
    fun `archive expired session emits SessionExpired`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val beta = summary(slug = "beta", name = "Beta")
        fake.boardsHandler = { Result.success(boards(beta)) }
        fake.archiveBoardHandler = {
            Result.failure(ApiException(DataError.Unauthorized))
        }
        val vm = started(fake, slug = "beta")
        val effects = collectEffects(vm)

        vm.onArchiveClicked()
        vm.onArchiveConfirmed()
        advanceUntilIdle()

        assertEquals(listOf(KanbanBoardsEffect.SessionExpired), effects)
    }

    @Test
    fun `invalid slug gets a field error and no request`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val vm = started(fake)
        vm.onNameChanged("Valid name")
        vm.onSlugChanged("-")
        vm.onSave()
        advanceUntilIdle()

        assertTrue(fake.createBoardCalls.isEmpty())
        assertEquals(
            "Use lowercase letters, numbers and dashes.",
            vm.state.value.fieldErrors[KanbanBoardsField.SLUG],
        )
    }

    @Test
    fun `settings save with no changes does nothing`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val alpha = summary()
        fake.boardsHandler = { Result.success(boards(alpha)) }
        val vm = started(fake, slug = "alpha")

        vm.onSave()
        advanceUntilIdle()

        assertTrue(fake.updateBoardCalls.isEmpty())
    }
}
