package com.clinty.client.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clinty.client.models.ThreadData
import com.clinty.client.services.InboxStore
import com.clinty.client.ui.inbox.InboxScreen
import com.clinty.client.ui.settings.SettingsScreen
import com.clinty.client.ui.thread.ThreadDetailScreen
import com.clinty.client.viewmodels.InboxListViewModel
import com.clinty.client.viewmodels.InboxSettingsViewModel
import com.clinty.client.viewmodels.ThreadDetailViewModel

object Routes {
    const val INBOX = "inbox"
    const val THREAD = "thread/{threadId}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClintyNavHost(store: InboxStore) {
    val navController = rememberNavController()
    var showSettings by remember { mutableStateOf(false) }
    var threadInitialData by remember { mutableStateOf<ThreadData?>(null) }

    val inboxes by store.inboxes.collectAsState()
    val isConfigured = inboxes.any { it.selected }
    val application = LocalContext.current.applicationContext as android.app.Application

    val inboxViewModel: InboxListViewModel = viewModel(
        factory = InboxViewModelFactory(store, application),
    )
    val settingsViewModel: InboxSettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(store),
    )

    NavHost(navController = navController, startDestination = Routes.INBOX) {
        composable(Routes.INBOX) {
            InboxScreen(
                viewModel = inboxViewModel,
                isConfigured = isConfigured,
                onOpenSettings = { showSettings = true },
                onThreadClick = { thread ->
                    threadInitialData = thread
                    navController.navigate("thread/${thread.id}")
                },
            )
        }

        composable(
            route = Routes.THREAD,
            arguments = listOf(
                navArgument("threadId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getString("threadId").orEmpty()
            val initial = threadInitialData?.takeIf { it.id == threadId }

            val detailViewModel: ThreadDetailViewModel = viewModel(
                key = threadId,
                factory = ThreadDetailViewModelFactory(threadId, initial, store),
            )

            ThreadDetailScreen(
                viewModel = detailViewModel,
                onNavigateBack = {
                    threadInitialData = null
                    inboxViewModel.refresh()
                    navController.popBackStack()
                },
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = {
                showSettings = false
                inboxViewModel.refresh()
            },
        ) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onDismiss = {
                    showSettings = false
                    inboxViewModel.refresh()
                },
            )
        }
    }
}
