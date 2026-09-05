package com.mrhakan.codexmobile.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mrhakan.codexmobile.ServiceLocator
import com.mrhakan.codexmobile.auth.AuthState
import com.mrhakan.codexmobile.auth.ChatGptAuthRepository
import com.mrhakan.codexmobile.auth.DeviceAuthPrompt
import com.mrhakan.codexmobile.auth.TokenStorage
import com.mrhakan.codexmobile.data.CloudTaskStatus
import com.mrhakan.codexmobile.data.CloudTaskSummary
import com.mrhakan.codexmobile.data.CloudTaskThread
import com.mrhakan.codexmobile.data.CodeEnvironment
import com.mrhakan.codexmobile.data.CodexCloudRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class AppUiState(
    val signedIn: Boolean = false,
    val account: AuthState? = null,
    val signingIn: Boolean = false,
    val deviceAuth: DeviceAuthPrompt? = null,
    val environments: List<CodeEnvironment> = emptyList(),
    val environmentsLoading: Boolean = false,
    val selectedEnvironmentId: String? = null,
    val branch: String = "",
    val prompt: String = "",
    val submitting: Boolean = false,
    val tasks: List<CloudTaskSummary> = emptyList(),
    val tasksLoading: Boolean = false,
    val thread: CloudTaskThread? = null,
    val threadLoading: Boolean = false,
    val message: String? = null,
) {
    val selectedEnvironment: CodeEnvironment?
        get() = environments.firstOrNull { it.id == selectedEnvironmentId }

    val canSubmit: Boolean
        get() = !submitting && prompt.isNotBlank() && selectedEnvironmentId != null
}

/**
 * Drives sign-in, the environment/task lists and the thread view, and keeps a
 * running task's thread up to date while it is open.
 */
class AppViewModel(
    private val auth: ChatGptAuthRepository,
    private val cloud: CodexCloudRepository,
    private val store: TokenStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AppUiState(
            signedIn = auth.isSignedIn,
            account = auth.authState,
            selectedEnvironmentId = store.lastEnvironmentId,
        ),
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var threadPoller: Job? = null

    init {
        if (auth.isSignedIn) {
            loadEnvironments()
            loadTasks()
        }
    }

    // region sign-in

    fun startSignIn() {
        _uiState.value = _uiState.value.copy(signingIn = true, message = null)
        viewModelScope.launch {
            val (prompt, userCode) = runCatching { auth.requestDeviceCode() }
                .getOrElse { throwable ->
                    _uiState.value = _uiState.value.copy(
                        signingIn = false,
                        message = "Could not start sign-in: ${throwable.readable()}",
                    )
                    return@launch
                }

            _uiState.value = _uiState.value.copy(deviceAuth = prompt)

            auth.awaitApproval(userCode)
                .onSuccess { state ->
                    _uiState.value = _uiState.value.copy(
                        signedIn = true,
                        account = state,
                        signingIn = false,
                        deviceAuth = null,
                    )
                    loadEnvironments()
                    loadTasks()
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        signingIn = false,
                        deviceAuth = null,
                        message = "Sign-in failed: ${throwable.readable()}",
                    )
                }
        }
    }

    fun signOut() {
        threadPoller?.cancel()
        auth.signOut()
        _uiState.value = AppUiState()
    }

    // endregion

    // region environments and task list

    fun loadEnvironments() {
        _uiState.value = _uiState.value.copy(environmentsLoading = true)
        viewModelScope.launch {
            runCatching { cloud.listEnvironments() }
                .onSuccess { environments ->
                    val current = _uiState.value.selectedEnvironmentId
                    val selected = environments.firstOrNull { it.id == current }?.id
                        ?: environments.firstOrNull()?.id
                    _uiState.value = _uiState.value.copy(
                        environments = environments,
                        environmentsLoading = false,
                        selectedEnvironmentId = selected,
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        environmentsLoading = false,
                        message = "Could not load environments: ${throwable.readable()}",
                    )
                }
        }
    }

    fun loadTasks() {
        _uiState.value = _uiState.value.copy(tasksLoading = true)
        viewModelScope.launch {
            runCatching { cloud.listTasks() }
                .onSuccess { tasks ->
                    _uiState.value = _uiState.value.copy(tasks = tasks, tasksLoading = false)
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        tasksLoading = false,
                        message = "Could not load tasks: ${throwable.readable()}",
                    )
                }
        }
    }

    fun selectEnvironment(id: String) {
        store.lastEnvironmentId = id
        _uiState.value = _uiState.value.copy(selectedEnvironmentId = id)
    }

    fun setBranch(value: String) {
        _uiState.value = _uiState.value.copy(branch = value)
    }

    fun setPrompt(value: String) {
        _uiState.value = _uiState.value.copy(prompt = value)
    }

    // endregion

    // region task submission and thread

    /** @param onCreated receives the new task id so the UI can open its thread. */
    fun submit(onCreated: (String) -> Unit) {
        val state = _uiState.value
        val environmentId = state.selectedEnvironmentId ?: return
        if (!state.canSubmit) return

        _uiState.value = state.copy(submitting = true, message = null)
        viewModelScope.launch {
            runCatching {
                cloud.createTask(
                    environmentId = environmentId,
                    prompt = state.prompt.trim(),
                    branch = state.branch.trim(),
                )
            }
                .onSuccess { taskId ->
                    _uiState.value = _uiState.value.copy(submitting = false, prompt = "")
                    openThread(taskId)
                    loadTasks()
                    onCreated(taskId)
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        submitting = false,
                        message = "Could not start the task: ${throwable.readable()}",
                    )
                }
        }
    }

    fun openThread(taskId: String) {
        threadPoller?.cancel()
        _uiState.value = _uiState.value.copy(threadLoading = true, thread = null)
        threadPoller = viewModelScope.launch {
            var attempt = 0
            while (isActive) {
                val thread = runCatching { cloud.getThread(taskId) }.getOrElse { throwable ->
                    _uiState.value = _uiState.value.copy(
                        threadLoading = false,
                        message = "Could not load the task: ${throwable.readable()}",
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(thread = thread, threadLoading = false)
                if (!thread.status.isRunning) break
                attempt++
                // A cloud task takes minutes; back off from 4s to 20s.
                delay((POLL_START_MILLIS * attempt).coerceAtMost(POLL_MAX_MILLIS))
            }
            // Reflect the finished state in the list behind the thread.
            loadTasks()
        }
    }

    fun refreshThread() {
        _uiState.value.thread?.let { openThread(it.id) }
    }

    fun closeThread() {
        threadPoller?.cancel()
        threadPoller = null
        _uiState.value = _uiState.value.copy(thread = null, threadLoading = false)
    }

    // endregion

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun Throwable.readable(): String = when (this) {
        is HttpException -> when (code()) {
            401 -> "session expired — sign in again"
            403 -> "your ChatGPT account does not have access to Codex Cloud"
            429 -> "rate limited by ChatGPT; try again shortly"
            else -> "HTTP ${code()}"
        }

        else -> message ?: this::class.java.simpleName
    }

    companion object {
        private const val POLL_START_MILLIS = 4_000L
        private const val POLL_MAX_MILLIS = 20_000L

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val graph = ServiceLocator.graph(application)
                    return AppViewModel(
                        auth = graph.authRepository,
                        cloud = graph.cloudRepository,
                        store = graph.secureStore,
                    ) as T
                }
            }
    }
}

/** Status text shown next to a task. */
fun CloudTaskStatus.label(): String = when (this) {
    CloudTaskStatus.PENDING -> "Working"
    CloudTaskStatus.READY -> "Ready"
    CloudTaskStatus.APPLIED -> "Applied"
    CloudTaskStatus.ERROR -> "Failed"
}
