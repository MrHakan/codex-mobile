package com.mrhakan.codexmobile.data

import kotlin.random.Random
import retrofit2.HttpException

/**
 * Turns a prompt into a `codex-cloud-agent` workflow run and follows it to its
 * pull request.
 *
 * `workflow_dispatch` is used instead of `repository_dispatch` because it
 * accepts named inputs and shows them in the run. Neither trigger returns the
 * id of the run it created, so the workflow stamps the client-supplied task id
 * into `run-name:` and the app matches on that.
 */
class CodexTaskRepository(
    private val api: GitHubApi,
    private val store: TaskStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {

    suspend fun submit(
        prompt: String,
        targetRepo: String,
        baseBranch: String,
        controllerRepo: String,
        workflowRef: String,
        model: String? = null,
    ): CodexTask {
        val now = clock()
        val id = CodexTask.newId(now, random.nextInt(0x10000, 0xFFFFF).toString(16))
        val task = CodexTask(
            id = id,
            prompt = prompt,
            targetRepo = targetRepo,
            baseBranch = baseBranch,
            controllerRepo = controllerRepo,
            createdAtEpochMillis = now,
            model = model?.takeIf { it.isNotBlank() },
        )
        store.upsert(task)

        val (controllerOwner, controllerName) = splitRepo(controllerRepo)
        val inputs = buildMap {
            put("prompt", prompt)
            put("task_id", id)
            if (baseBranch.isNotBlank()) put("base_branch", baseBranch)
            if (targetRepo.isNotBlank() && targetRepo != controllerRepo) {
                put("target_repo", targetRepo)
            }
            task.model?.let { put("model", it) }
        }

        val dispatched = runCatching {
            val response = api.dispatchWorkflow(
                owner = controllerOwner,
                repo = controllerName,
                workflowFileName = WORKFLOW_FILE,
                body = WorkflowDispatchRequest(ref = workflowRef, inputs = inputs),
            )
            if (!response.isSuccessful) {
                error(dispatchErrorMessage(response.code(), response.errorBody()?.string()))
            }
        }

        val result = dispatched.fold(
            onSuccess = { task.copy(status = TaskStatus.DISPATCHED) },
            onFailure = { throwable ->
                task.copy(
                    status = TaskStatus.DISPATCH_FAILED,
                    error = throwable.message ?: throwable::class.java.simpleName,
                )
            },
        )
        store.upsert(result)
        return result
    }

    /** Fetches the current state of [task] from GitHub and persists it. */
    suspend fun refresh(task: CodexTask): CodexTask {
        if (task.status == TaskStatus.DISPATCH_FAILED) return task

        val (controllerOwner, controllerName) = splitRepo(task.controllerRepo)
        val updated = runCatching {
            val run = task.runId
                ?.let { api.getWorkflowRun(controllerOwner, controllerName, it) }
                ?: findRun(controllerOwner, controllerName, task)

            if (run == null) {
                // The run can take a few seconds to appear after a dispatch.
                return@runCatching task.copy(status = TaskStatus.DISPATCHED, error = null)
            }

            val status = statusOf(run)
            var result = task.copy(
                runId = run.id,
                runUrl = run.htmlUrl,
                status = status,
                conclusion = run.conclusion,
                error = null,
            )

            if (status == TaskStatus.RUNNING || status.isTerminal) {
                result = result.copy(jobSummary = summarize(controllerOwner, controllerName, run.id))
            }
            if (status.isTerminal && result.pullRequestUrl == null) {
                result = result.copy(pullRequestUrl = findPullRequest(result))
            }
            result
        }.getOrElse { throwable ->
            task.copy(error = throwable.toReadableMessage())
        }

        store.upsert(updated)
        return updated
    }

    private suspend fun findRun(owner: String, repo: String, task: CodexTask): WorkflowRun? {
        val runs = api.listWorkflowRuns(
            owner = owner,
            repo = repo,
            workflowFileName = WORKFLOW_FILE,
            event = null,
            perPage = 30,
        )
        val expectedName = RUN_NAME_PREFIX + task.id
        return runs.workflowRuns.firstOrNull { run ->
            run.name?.trim() == expectedName || run.displayTitle?.trim() == expectedName
        }
    }

    private suspend fun summarize(owner: String, repo: String, runId: Long): List<String> =
        runCatching {
            api.listRunJobs(owner, repo, runId).jobs.flatMap { job ->
                job.steps.map { step ->
                    "${step.name}: ${step.conclusion ?: step.status ?: "pending"}"
                }
            }
        }.getOrDefault(emptyList())

    private suspend fun findPullRequest(task: CodexTask): String? {
        val (owner, repo) = splitRepo(task.targetRepo)
        return runCatching {
            api.listPullRequests(
                owner = owner,
                repo = repo,
                head = "$owner:${task.branch}",
            ).firstOrNull()?.htmlUrl
        }.getOrNull()
    }

    private fun statusOf(run: WorkflowRun): TaskStatus = when (run.status) {
        "completed" -> when (run.conclusion) {
            "success" -> TaskStatus.SUCCEEDED
            "cancelled", "skipped" -> TaskStatus.CANCELLED
            else -> TaskStatus.FAILED
        }

        "in_progress" -> TaskStatus.RUNNING
        "queued", "waiting", "requested", "pending" -> TaskStatus.QUEUED
        else -> TaskStatus.DISPATCHED
    }

    private fun dispatchErrorMessage(code: Int, body: String?): String = when (code) {
        401 -> "GitHub rejected the token (401). Sign in again."
        403 -> "Token lacks Actions write access to the controller repo (403)."
        404 -> "Workflow $WORKFLOW_FILE not found on the configured branch (404)."
        422 -> "GitHub rejected the inputs (422): ${body.orEmpty().take(300)}"
        else -> "Dispatch failed with HTTP $code: ${body.orEmpty().take(300)}"
    }

    companion object {
        const val WORKFLOW_FILE = "codex-cloud-agent.yml"
        const val RUN_NAME_PREFIX = "Codex task "

        fun splitRepo(fullName: String): Pair<String, String> {
            val parts = fullName.split('/')
            require(parts.size == 2 && parts.all { it.isNotBlank() }) {
                "Repository must look like owner/repo (got: $fullName)"
            }
            return parts[0] to parts[1]
        }
    }
}

internal fun Throwable.toReadableMessage(): String = when (this) {
    is HttpException -> "GitHub returned HTTP ${code()}"
    else -> message ?: this::class.java.simpleName
}
