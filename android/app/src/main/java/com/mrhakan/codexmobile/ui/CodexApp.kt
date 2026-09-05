package com.mrhakan.codexmobile.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrhakan.codexmobile.ui.screens.ComposerScreen
import com.mrhakan.codexmobile.ui.screens.SignInScreen
import com.mrhakan.codexmobile.ui.screens.TaskListScreen
import com.mrhakan.codexmobile.ui.screens.ThreadScreen

/** Screen stack: list -> composer or thread. Small enough to hold by hand. */
private enum class Screen { LIST, COMPOSER, THREAD }

@Composable
fun CodexApp(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var screen by rememberSaveable { mutableStateOf(Screen.LIST) }

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
            onSignIn = viewModel::startSignIn,
            onOpenUrl = openUrl,
            snackbarHostState = snackbarHostState,
        )
        return
    }

    BackHandler(enabled = screen != Screen.LIST) {
        if (screen == Screen.THREAD) viewModel.closeThread()
        screen = Screen.LIST
    }

    when (screen) {
        Screen.LIST -> TaskListScreen(
            state = state,
            onOpenTask = { taskId ->
                viewModel.openThread(taskId)
                screen = Screen.THREAD
            },
            onNewTask = { screen = Screen.COMPOSER },
            onRefresh = {
                viewModel.loadTasks()
                viewModel.loadEnvironments()
            },
            onSignOut = {
                viewModel.signOut()
                screen = Screen.LIST
            },
            onOpenUrl = openUrl,
            snackbarHostState = snackbarHostState,
        )

        Screen.COMPOSER -> ComposerScreen(
            state = state,
            onPromptChange = viewModel::setPrompt,
            onBranchChange = viewModel::setBranch,
            onSelectEnvironment = viewModel::selectEnvironment,
            onSubmit = { viewModel.submit { screen = Screen.THREAD } },
            onBack = { screen = Screen.LIST },
        )

        Screen.THREAD -> ThreadScreen(
            thread = state.thread,
            loading = state.threadLoading,
            onRefresh = viewModel::refreshThread,
            onOpenUrl = openUrl,
            onBack = {
                viewModel.closeThread()
                screen = Screen.LIST
            },
        )
    }
}
