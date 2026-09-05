package com.mrhakan.codexmobile.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrhakan.codexmobile.ui.screens.RepoPickerScreen
import com.mrhakan.codexmobile.ui.screens.RunDetailScreen
import com.mrhakan.codexmobile.ui.screens.RunListScreen
import com.mrhakan.codexmobile.ui.screens.SettingsScreen
import com.mrhakan.codexmobile.ui.screens.SignInScreen
import com.mrhakan.codexmobile.ui.screens.TaskComposerScreen

/** Screen stack. Small enough that a nav library would be overkill. */
private enum class Tab { RUNS, COMPOSE, SETTINGS }

@Composable
fun CodexApp(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(Tab.COMPOSE) }
    var pickingRepo by rememberSaveable { mutableStateOf(false) }
    var openTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { throwable ->
            if (throwable !is ActivityNotFoundException) throw throwable
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    if (!state.signedIn) {
        SignInScreen(
            state = state,
            onSignInWithToken = viewModel::signInWithToken,
            onStartDeviceFlow = viewModel::startDeviceFlow,
            onOpenUrl = openUrl,
            snackbarHostState = snackbarHostState,
        )
        return
    }

    val openedTask = openTaskId?.let { id -> tasks.firstOrNull { it.id == id } }

    BackHandler(enabled = pickingRepo || openedTask != null) {
        when {
            pickingRepo -> pickingRepo = false
            else -> openTaskId = null
        }
    }

    when {
        pickingRepo -> RepoPickerScreen(
            state = state,
            onFilterChange = viewModel::setRepoFilter,
            onRefresh = viewModel::loadRepositories,
            onSelect = { repo ->
                viewModel.selectRepository(repo)
                pickingRepo = false
            },
            onBack = { pickingRepo = false },
        )

        openedTask != null -> RunDetailScreen(
            task = openedTask,
            onRefresh = { viewModel.refreshTask(openedTask.id) },
            onOpenUrl = openUrl,
            onDelete = {
                viewModel.deleteTask(openedTask.id)
                openTaskId = null
            },
            onBack = { openTaskId = null },
        )

        else -> Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.COMPOSE,
                        onClick = { tab = Tab.COMPOSE },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        label = { Text("New task") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.RUNS,
                        onClick = { tab = Tab.RUNS },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Runs") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.SETTINGS,
                        onClick = { tab = Tab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                    )
                }
            },
        ) { padding ->
            val contentModifier = Modifier.padding(padding)
            when (tab) {
                Tab.COMPOSE -> TaskComposerScreen(
                    modifier = contentModifier,
                    state = state,
                    onPromptChange = viewModel::setPrompt,
                    onModelChange = viewModel::setModel,
                    onPickRepo = { pickingRepo = true },
                    onSelectBranch = viewModel::selectBranch,
                    onSubmit = {
                        viewModel.submitTask { taskId ->
                            tab = Tab.RUNS
                            openTaskId = taskId
                        }
                    },
                )

                Tab.RUNS -> RunListScreen(
                    modifier = contentModifier,
                    tasks = tasks,
                    onOpen = { openTaskId = it },
                    onRefresh = { tasks.forEach { viewModel.refreshTask(it.id) } },
                )

                Tab.SETTINGS -> SettingsScreen(
                    modifier = contentModifier,
                    state = state,
                    onControllerRepoChange = viewModel::setControllerRepo,
                    onWorkflowRefChange = viewModel::setWorkflowRef,
                    onSignOut = viewModel::signOut,
                    onOpenUrl = openUrl,
                )
            }
        }
    }
}
