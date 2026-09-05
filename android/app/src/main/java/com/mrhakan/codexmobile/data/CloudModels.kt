package com.mrhakan.codexmobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Wire models for the Codex Cloud backend (`/backend-api/wham/...`), kept
 * deliberately close to what `codex-rs/backend-client` deserializes. The
 * payloads carry far more than this; everything unread stays unmodelled and
 * `ignoreUnknownKeys` drops it.
 */

@Serializable
data class CodeEnvironment(
    val id: String,
    val label: String? = null,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("task_count") val taskCount: Long? = null,
)

@Serializable
data class TaskListPage(
    val items: List<TaskListItem> = emptyList(),
    val cursor: String? = null,
)

@Serializable
data class TaskListItem(
    val id: String,
    val title: String = "",
    @SerialName("updated_at") val updatedAt: Double? = null,
    @SerialName("created_at") val createdAt: Double? = null,
    @SerialName("task_status_display") val statusDisplay: JsonObject? = null,
    val archived: Boolean = false,
    @SerialName("has_unread_turn") val hasUnreadTurn: Boolean = false,
    @SerialName("pull_requests") val pullRequests: List<PullRequestEntry> = emptyList(),
)

@Serializable
data class PullRequestEntry(
    val id: String? = null,
    @SerialName("assistant_turn_id") val assistantTurnId: String? = null,
    @SerialName("pull_request") val pullRequest: GitPullRequest? = null,
)

@Serializable
data class GitPullRequest(
    val number: Int? = null,
    val url: String? = null,
    val state: String? = null,
    val merged: Boolean = false,
    val draft: Boolean? = null,
    val title: String? = null,
)

/** `GET /wham/tasks/{id}` — turns carry the prompt, the messages and the diff. */
@Serializable
data class TaskDetails(
    val task: TaskMeta? = null,
    @SerialName("task_status_display") val statusDisplay: JsonObject? = null,
    @SerialName("current_user_turn") val currentUserTurn: Turn? = null,
    @SerialName("current_assistant_turn") val currentAssistantTurn: Turn? = null,
    @SerialName("current_diff_task_turn") val currentDiffTaskTurn: Turn? = null,
)

@Serializable
data class TaskMeta(
    val id: String? = null,
    val title: String? = null,
    @SerialName("environment_id") val environmentId: String? = null,
    @SerialName("updated_at") val updatedAt: Double? = null,
    @SerialName("created_at") val createdAt: Double? = null,
    @SerialName("task_status_display") val statusDisplay: JsonObject? = null,
    @SerialName("pull_requests") val pullRequests: List<PullRequestEntry> = emptyList(),
)

@Serializable
data class Turn(
    val id: String? = null,
    @SerialName("turn_status") val turnStatus: String? = null,
    @SerialName("attempt_placement") val attemptPlacement: Long? = null,
    @SerialName("input_items") val inputItems: List<TurnItem> = emptyList(),
    @SerialName("output_items") val outputItems: List<TurnItem> = emptyList(),
    val worklog: Worklog? = null,
    val error: TurnError? = null,
)

@Serializable
data class TurnItem(
    @SerialName("type") val kind: String = "",
    val role: String? = null,
    val content: List<ContentFragment> = emptyList(),
    val diff: String? = null,
    @SerialName("output_diff") val outputDiff: DiffPayload? = null,
)

/** Content parts arrive either as bare strings or as `{content_type, text}`. */
@Serializable(with = ContentFragmentSerializer::class)
data class ContentFragment(
    val contentType: String? = null,
    val text: String? = null,
)

@Serializable
data class DiffPayload(val diff: String? = null)

@Serializable
data class Worklog(val messages: List<WorklogMessage> = emptyList())

@Serializable
data class WorklogMessage(
    val author: Author? = null,
    val content: WorklogContent? = null,
) {
    val isAssistant: Boolean get() = author?.role == "assistant"
}

@Serializable
data class Author(val role: String? = null)

@Serializable
data class WorklogContent(val parts: List<ContentFragment> = emptyList())

@Serializable
data class TurnError(
    val code: String? = null,
    val message: String? = null,
) {
    fun summary(): String? = when {
        code != null && message != null -> "$code: $message"
        else -> message ?: code
    }
}

/** `POST /wham/tasks` request body, shaped exactly as the CLI sends it. */
@Serializable
data class CreateTaskRequest(
    @SerialName("new_task") val newTask: NewTask,
    @SerialName("input_items") val inputItems: List<JsonElement>,
)

@Serializable
data class NewTask(
    @SerialName("environment_id") val environmentId: String,
    val branch: String,
    @SerialName("run_environment_in_qa_mode") val qaMode: Boolean = false,
)

@Serializable
data class CreateTaskResponse(
    val id: String? = null,
    val task: TaskMeta? = null,
) {
    val taskId: String? get() = task?.id ?: id
}
