package com.dipandhali.hermesnative.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dipandhali.hermesnative.ui.chat.ChatScreen
import com.dipandhali.hermesnative.ui.connect.ConnectScreen
import com.dipandhali.hermesnative.ui.sessions.SessionsDrawer
import com.dipandhali.hermesnative.ui.settings.SettingsScreen
import com.dipandhali.hermesnative.ui.theme.HermesNativeTheme
import kotlinx.coroutines.launch

object Routes {
    const val CONNECT = "connect"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}

/**
 * App shell: theme, navigation, and the session drawer.
 *
 * The start destination is resolved from persisted state so a returning user lands
 * in a conversation rather than on the connect screen every launch. The theme is
 * read here as a live value rather than captured once, so changing it in Settings
 * reproduces immediately — including the dynamic-colour switch, which would
 * otherwise appear to do nothing until the next cold start.
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

@Composable
private fun HermesNavHost(startDestination: String) {
    val navController: NavHostController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = startDestination == Routes.CHAT,
        drawerContent = {
            SessionsDrawer(
                currentSessionId = null,
                onOpenSession = { /* session selection drives the chat screen */ },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.SETTINGS)
                },
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.CONNECT) {
                ConnectScreen(
                    onConnected = {
                        navController.navigate(Routes.CHAT) {
                            popUpTo(Routes.CONNECT) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.CHAT) {
                ChatScreen(
                    sessionId = null,
                    onOpenDrawer = openDrawer,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
}

/** Shown for the one frame before persisted state resolves. */
@Composable
private fun StartUpPlaceholder() {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
    ) {}
}
