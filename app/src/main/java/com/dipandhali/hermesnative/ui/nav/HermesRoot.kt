package com.dipandhali.hermesnative.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dipandhali.hermesnative.core.model.AttachmentDto
import com.dipandhali.hermesnative.ui.chat.ChatScreen
import com.dipandhali.hermesnative.ui.components.FabMenuAction
import com.dipandhali.hermesnative.ui.connect.ConnectScreen
import com.dipandhali.hermesnative.ui.files.FilePreviewScreen
import com.dipandhali.hermesnative.ui.files.FilesScreen
import com.dipandhali.hermesnative.ui.panels.PanelKind
import com.dipandhali.hermesnative.ui.panels.PanelScreen
import com.dipandhali.hermesnative.ui.home.HomeScreen
import com.dipandhali.hermesnative.ui.sessions.SessionsListScreen
import com.dipandhali.hermesnative.ui.settings.SettingsScreen
import com.dipandhali.hermesnative.ui.theme.HermesNativeTheme
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Encodes the attachment list a seeded chat route carries. */
private val routeJson = Json { explicitNulls = false }

object Routes {
    const val CONNECT = "connect"

    /** The default screen: composer plus the five most recent conversations. */
    const val HOME = "home"

    /** The full session list, pushed from home. */
    const val SESSIONS = "sessions"
    const val SETTINGS = "settings"

    /** Panel routes double as the FAB-menu destination, one per panel kind. */
    const val PANEL = "panel"
    fun panel(kind: PanelKind): String = "$PANEL/${kind.name}"

    const val CHAT = "chat"

    /**
     * A chat route carrying an optional first message and its attachments.
     *
     * The message rides in the route because the home composer has to create the
     * session before it can hand the text over, and sending it from home would
     * leave the user watching a run they cannot see. The message is URL-encoded
     * because it is arbitrary user text and can contain `/`, which would
     * otherwise split the path. Attachments ride as a JSON array — they were
     * uploaded during creation and are small value objects.
     */
    fun chat(
        sessionId: String,
        initialMessage: String? = null,
        attachments: List<AttachmentDto> = emptyList(),
    ): String {
        val base = "$CHAT/${java.net.URLEncoder.encode(sessionId, "UTF-8")}"
        val params = mutableListOf<String>()
        if (!initialMessage.isNullOrBlank()) {
            params += "message=${java.net.URLEncoder.encode(initialMessage, "UTF-8")}"
        }
        if (attachments.isNotEmpty()) {
            val encoded = routeJson.encodeToString(ListSerializer(AttachmentDto.serializer()), attachments)
            params += "attachments=${java.net.URLEncoder.encode(encoded, "UTF-8")}"
        }
        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }

    /**
     * A chat opened as the REPLACEMENT for a mid-chat profile switch: same
     * chat route with a one-shot banner that shows in the new conversation,
     * plus a distinct arg so the destination is the composable decision.
     */
    fun chatSwitched(sessionId: String, profileName: String): String {
        val base = "$CHAT/${java.net.URLEncoder.encode(sessionId, "UTF-8")}"
        return "$base?switched=${java.net.URLEncoder.encode(
            "Switched to $profileName — new conversation",
            "UTF-8",
        )}"
    }

    /** The workspace Files panel for one session. */
    const val FILES = "files"
    fun files(sessionId: String): String = "$FILES/${Uri.encode(sessionId)}"

    const val FILE_PREVIEW = "file_preview"

    /**
     * `Uri.encode`, not `URLEncoder`: the route string is parsed as a URI and
     * Navigation decodes the arguments itself (`NavUriUtils` for path
     * segments, `Uri.getQueryParameters` for query values), so form
     * encoding's `+` for a space would arrive as a literal plus.
     */
    fun filePreview(sessionId: String, path: String): String =
        "$FILE_PREVIEW/${Uri.encode(sessionId)}?path=${Uri.encode(path)}"
}

/**
 * App shell: theme, navigation, and the sessions home screen as the default.
 *
 * There is no drawer and no rail — the sessions list owns the default screen, and
 * the nine side panels are reached from a FAB menu. The desktop rail exists
 * because a wide window has room for a permanent column of icons; a phone does
 * not, and a 300dp drawer showing the same list the user is about to open costs a
 * tap and buys nothing.
 *
 * Every panel is a real navigation destination rather than an overlay, so the
 * system back gesture does the expected thing on all nine without any custom
 * handling.
 */
@Composable
fun HermesRoot(viewModel: RootViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val startRoute by viewModel.startRoute.collectAsStateWithLifecycle()

    HermesNativeTheme(
        themeMode = theme.mode,
        dynamicColor = theme.dynamicColor,
    ) {
        when (val route = startRoute) {
            null -> StartUpPlaceholder()
            else -> HermesNavHost(startDestination = route)
        }
    }
}

/**
 * The FAB menu's contents, in the desktop rail's own order.
 *
 * Labels match the desktop tooltips ("Spaces", "Agent profiles", "Tasks") so a
 * user moving between the two clients does not have to re-learn the vocabulary.
 */
@Composable
private fun panelActions(navigate: (String) -> Unit): List<FabMenuAction> = remember {
    listOf(
        PanelKind.TASKS to Icons.Rounded.CalendarMonth,
        PanelKind.KANBAN to Icons.Rounded.ViewKanban,
        PanelKind.SKILLS to Icons.Rounded.Psychology,
        PanelKind.MEMORY to Icons.Rounded.Dashboard,
        PanelKind.SPACES to Icons.Rounded.Folder,
        PanelKind.PROFILES to Icons.Rounded.Person,
        PanelKind.TODOS to Icons.Rounded.Checklist,
        PanelKind.INSIGHTS to Icons.Rounded.Insights,
        PanelKind.LOGS to Icons.Rounded.Terminal,
    ).map { (kind, icon) ->
        FabMenuAction(
            label = kind.title,
            icon = icon,
            onClick = { navigate(Routes.panel(kind)) },
        )
    }
}

@Composable
private fun HermesNavHost(startDestination: String) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.CONNECT) {
            ConnectScreen(
                onConnected = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.CONNECT) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenSession = { sessionId -> navController.navigate(Routes.chat(sessionId)) },
                onOpenSessionAndSend = { sessionId, message, attachments ->
                    navController.navigate(Routes.chat(sessionId, message, attachments))
                },
                onSeeAllSessions = { navController.navigate(Routes.SESSIONS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                fabActions = panelActions { route -> navController.navigate(route) },
            )
        }

        composable(Routes.SESSIONS) {
            SessionsListScreen(
                onOpenSession = { sessionId -> navController.navigate(Routes.chat(sessionId)) },
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = "${Routes.CHAT}/{sessionId}?message={message}&attachments={attachments}&switched={switched}",
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("message") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("attachments") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("switched") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId")
            val message = entry.arguments?.getString("message")
            // Decoding is best-effort: the seed's attachments are a convenience,
            // and a route that cannot parse them still opens the conversation.
            val attachments = remember(entry) {
                entry.arguments?.getString("attachments")
                    ?.let { raw ->
                        runCatching {
                            routeJson.decodeFromString(
                                ListSerializer(AttachmentDto.serializer()),
                                raw,
                            )
                        }.getOrNull()
                    }
                    .orEmpty()
            }
            ChatScreen(
                sessionId = sessionId,
                initialMessage = message,
                initialAttachments = attachments,
                switchedNotice = entry.arguments?.getString("switched"),
                // The hamburger is gone: this is a home button, and it goes back to
                // the default screen rather than opening a drawer.
                onGoHome = {
                    navController.navigate(Routes.HOME) {
                        // One home in the stack, not a growing pile of them.
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onSwitchSession = { newId, profileName ->
                    // REPLACE this chat on the stack: back must not reopen a
                    // conversation the new profile cannot read.
                    val currentId = navController.currentBackStackEntry?.destination?.id
                    navController.navigate(Routes.chatSwitched(newId, profileName)) {
                        if (currentId != null) {
                            popUpTo(currentId) { inclusive = true }
                        }
                    }
                },
                onOpenNewSession = { newId ->
                    // A worktree chat is a NEW conversation: pushed, so back
                    // returns to the chat it was started from.
                    navController.navigate(Routes.chat(newId))
                },
                onOpenFiles = { navController.navigate(Routes.files(sessionId.orEmpty())) },
                onOpenAttachment = { name ->
                    // Only a real conversation can resolve attachment bytes:
                    // its id is what the raw file route demands.
                    sessionId?.let { navController.navigate(Routes.filePreview(it, name)) }
                },
            )
        }

        composable(
            route = "${Routes.PANEL}/{kind}",
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { entry ->
            val kind = entry.arguments?.getString("kind")
                ?.let { runCatching { PanelKind.valueOf(it) }.getOrNull() }
                ?: PanelKind.SKILLS
            PanelScreen(kind = kind, onBack = { navController.popBackStack() })
        }

        composable(
            route = "${Routes.FILES}/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            FilesScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onOpenPreview = { id, path -> navController.navigate(Routes.filePreview(id, path)) },
            )
        }

        composable(
            route = "${Routes.FILE_PREVIEW}/{sessionId}?path={path}",
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType },
            ),
        ) { entry ->
            // Navigation has already decoded both arguments; decoding again
            // here would turn a stored `+` into a space and crash on a stray
            // `%` (URLDecoder is strict, the platform decoder is not).
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            val path = entry.arguments?.getString("path").orEmpty()
            FilePreviewScreen(
                sessionId = sessionId,
                path = path,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Routes.CONNECT) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}

/** Shown for the one frame before persisted state resolves. */
@Composable
private fun StartUpPlaceholder() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {}
}
