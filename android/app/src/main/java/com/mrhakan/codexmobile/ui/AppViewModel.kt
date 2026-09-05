package com.mrhakan.codexmobile.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mrhakan.codexmobile.BuildConfig
import com.mrhakan.codexmobile.ServiceLocator
import com.mrhakan.codexmobile.auth.AuthRepository
import com.mrhakan.codexmobile.auth.DeviceCodeResponse
import com.mrhakan.codexmobile.auth.SecureTokenStore
import com.mrhakan.codexmobile.data.CodexTask
import com.mrhakan.codexmobile.data.CodexTaskRepository
import com.mrhakan.codexmobile.data.GitHubApi
import com.mrhakan.codexmobile.data.Repository
import com.mrhakan.codexmobile.data.TaskStatus
import com.mrhakan.codexmobile.data.TaskStore
import com.mrhakan.codexmobile.data.toReadableMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DeviceFlowState(
    val userCode: String,
    val verificationUri: String,
    val pending: Boolean = true,
)

data class AppUiState(
    val signedIn: Boolean = false,
    val login: String? = null,
    val controllerRepo: String = SecureTokenStore.DEFAULT_CONTROLLER_REPO,
    val workflowRef: String = SecureTokenStore.DEFAULT_WORKFLOW_REF,
    val repos: List<Repository> = emptyList(),
    val reposLoading: Boolean = false,
    val repoFilter: String = "",
    val selectedRepo: String? = null,
    val branches: List<String> = emptyList(),
    val branchesLoading: Boolean = false,
    val selectedBranch: String? = null,
    val prompt: String = "",
    val model: String = "",
    val submitting: Boolean = false,
    val signingIn: Boolean = false,
    val deviceFlow: DeviceFlowState? = null,
    val message: String? = null,
) {
    val deviceFlowSupported: Boolean get() = BuildConfig.GITHUB_OAUTH_CLIENT_ID.isNotBlank()

    val canSubmit: Boolean
        get() = signedIn && !submitting && prompt.isNotBlank() && !selectedRepo.isNullOrBlank()

    val filteredRepos: List<Repository>
        get() = if (repoFilter.isBlank()) {
            repos
        } else {
            repos.filter { it.fullName.contains(repoFilter, ignoreCase = true) }
        }
}

/**
 * Single view model for the whole app: auth, repo/branch pickers, task
 * submission, and the polling loop that follows each run.
 */
class AppViewModel(
    private val authRepository: AuthRepository,
    private val taskRepository: CodexTaskRepository,
    private val taskStore: TaskStore,
    private val api: GitHubApi,
    private val secureStore: SecureTokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AppUiState(
            signedIn = authRepository.isSignedIn,
            login = authRepository.login,
            controllerRepo = secureStore.controllerRepo,
            workflowRef = secureStore.workflowRef,
        ),
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    val tasks: StateFlow<List<CodexTask>> = taskStore.tasks

    /** One polling coroutine per task, keyed by task id. */
    private val pollers = mutableMapOf<String, Job>()

    init {
        if (authRepository.isSignedIn) {
            loadRepositories()
            resumePollingUnfinishedTasks()
        }
    }

    // region auth

    fun signInWithToken(token: String) {
        _uiState.value = _uiState.value.copy(signingIn = true, message = null)
        viewModelScope.launch {
            authRepository.signInWithToken(token)
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(
                        signedIn = true,
                        login = user.login,
                        signingIn = false,
                        message = null,
                    )
                    loadRepositories()
                    resumePollingUnfinishedTasks()
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        signingIn = false,
                        message = "Sign-in failed: ${throwable.toReadableMessage()}",
                    )
                }
        }
    }

    fun startDeviceFlow() {
        val clientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID
        if (clientId.isBlank()) {
            _uiState.value = _uiState.value.copy(
                message = "This build has no GitHub OAuth client id; paste a token instead.",
            )
            return
        }
        _uiState.value = _uiState.value.copy(signingIn = true, message = null)
        viewModelScope.launch {
            val deviceCode: DeviceCodeResponse = runCatching {
                authRepository.requestDeviceCode(clientId)
            }.getOrElse { throwable ->
                _uiState.value = _uiState.value.copy(
                    signingIn = false,
                    message = "Could not start device flow: ${throwable.toReadableMessage()}",
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                deviceFlow = DeviceFlowState(
                    userCode = deviceCode.userCode,
                    verificationUri = deviceCode.verificationUri,
                ),
            )

            authRepository.awaitDeviceAuthorization(clientId, deviceCode)
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(
                        signedIn = true,
                        login = user.login,
                        signingIn = false,
                        deviceFlow = null,
                    )
                    loadRepositories()
                    resumePollingUnfinishedTasks()
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        signingIn = false,
                        deviceFlow = null,
                        message = "Device flow failed: ${throwable.toReadableMessage()}",
                    )
                }
        }
    }

    fun signOut() {
        pollers.values.forEach(Job::cancel)
        pollers.clear()
        authRepository.signOut()
        _uiState.value = AppUiState(
            controllerRepo = secureStore.controllerRepo,
            workflowRef = secureStore.workflowRef,
        )
    }

    // endregion

    // region settings

    fun setControllerRepo(value: String) {
        secureStore.controllerRepo = value.trim()
        _uiState.value = _uiState.value.copy(controllerRepo = value.trim())
    }

    fun setWorkflowRef(value: String) {
        secureStore.workflowRef = value.trim()
        _uiState.value = _uiState.value.copy(workflowRef = value.trim())
    }

    // endregion

    // region repo + branch pickers

    fun loadRepositories() {
        _uiState.value = _uiState.value.copy(reposLoading = true)
        viewModelScope.launch {
            runCatching { api.listRepositories() }
                .onSuccess { repos ->
                    _uiState.value = _uiState.value.copy(repos = repos, reposLoading = false)
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        reposLoading = false,
                        message = "Could not list repositories: ${throwable.toReadableMessage()}",
                    )
                }
        }
    }

    fun setRepoFilter(value: String) {
        _uiState.value = _uiState.value.copy(repoFilter = value)
    }

    fun selectRepository(repo: Repository) {
        _uiState.value = _uiState.value.copy(
            selectedRepo = repo.fullName,
            selectedBranch = repo.defaultBranch,
            branches = emptyList(),
        )
        loadBranches(repo.fullName)
    }

    private fun loadBranches(fullName: String) {
        val (owner, name) = runCatching { CodexTaskRepository.splitRepo(fullName) }
            .getOrElse { return }
        _uiState.value = _uiState.value.copy(branchesLoading = true)
        viewModelScope.launch {
            runCatching { api.listBranches(owner, name) }
                .onSuccess { branches ->
                    _uiState.value = _uiState.value.copy(
                        branches = branches.map { it.name },
                        branchesLoading = false,
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        branchesLoading = false,
                        message = "Could not list branches: ${throwable.toReadableMessage()}",
                    )
                }
        }
    }

    fun selectBranch(branch: String) {
        _uiState.value = _uiState.value.copy(selectedBranch = branch)
    }

    // endregion

    // region task submission

    fun setPrompt(value: String) {
        _uiState.value = _uiState.value.copy(prompt = value)
    }

    fun setModel(value: String) {
        _uiState.value = _uiState.value.copy(model = value)
    }

    /** @param onSubmitted invoked with the new task id so the UI can navigate. */
    fun submitTask(onSubmitted: (String) -> Unit) {
        val state = _uiState.value
        val repo = state.selectedRepo ?: return
        if (!state.canSubmit) return

        _uiState.value = state.copy(submitting = true, message = null)
        viewModelScope.launch {
            val task = taskRepository.submit(
                prompt = state.prompt.trim(),
                targetRepo = repo,
                baseBranch = state.selectedBranch.orEmpty(),
                controllerRepo = state.controllerRepo,
                workflowRef = state.workflowRef,
                model = state.model,
            )
            _uiState.value = _uiState.value.copy(
                submitting = false,
                prompt = if (task.status == TaskStatus.DISPATCH_FAILED) state.prompt else "",
                message = task.error?.let { "Dispatch failed: $it" },
            )
            if (task.status != TaskStatus.DISPATCH_FAILED) {
                startPolling(task.id)
            }
            onSubmitted(task.id)
        }
    }

    // endregion

    // region run polling

    fun startPolling(taskId: String) {
        if (pollers[taskId]?.isActive == true) return
        pollers[taskId] = viewModelScope.launch {
            var attempt = 0
            while (isActive) {
                val task = taskStore.find(taskId) ?: break
                val updated = taskRepository.refresh(task)
                if (updated.status.isTerminal) break
                attempt++
                // Runs take a while; back off from 4s to 20s to stay well
                // inside GitHub's rate limits while the screen is open.
                delay((POLL_START_MILLIS * attempt).coerceAtMost(POLL_MAX_MILLIS))
            }
            pollers.remove(taskId)
        }
    }

    fun refreshTask(taskId: String) {
        viewModelScope.launch {
            taskStore.find(taskId)?.let { taskRepository.refresh(it) }
        }
    }

    private fun resumePollingUnfinishedTasks() {
        taskStore.tasks.value.filterNot { it.status.isTerminal }.forEach { startPolling(it.id) }
    }

    fun deleteTask(taskId: String) {
        pollers.remove(taskId)?.cancel()
        taskStore.remove(taskId)
    }

    // endregion

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
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
                        authRepository = graph.authRepository,
                        taskRepository = graph.taskRepository,
                        taskStore = graph.taskStore,
                        api = graph.api,
                        secureStore = graph.secureStore,
                    ) as T
                }
            }
    }
}
