package com.mrhakan.codexmobile.data

import kotlinx.serialization.Serializable

/** Terminal-or-not status of a task, derived from the workflow run. */
enum class TaskStatus {
    /** Dispatched, but the matching workflow run has not been found yet. */
    DISPATCHED,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    /** The dispatch call itself failed; nothing was started. */
    DISPATCH_FAILED,
    ;

    val isTerminal: Boolean
        get() = this == SUCCEEDED || this == FAILED || this == CANCELLED || this == DISPATCH_FAILED
}

/**
 * One Codex task as the phone sees it: what was asked, which workflow run is
 * carrying it out, and what came back.
 */
@Serializable
data class CodexTask(
    val id: String,
    val prompt: String,
    val targetRepo: String,
    val baseBranch: String,
    val controllerRepo: String,
    val createdAtEpochMillis: Long,
    val model: String? = null,
    val runId: Long? = null,
    val runUrl: String? = null,
    val status: TaskStatus = TaskStatus.DISPATCHED,
    val conclusion: String? = null,
    val pullRequestUrl: String? = null,
    val error: String? = null,
    val jobSummary: List<String> = emptyList(),
) {
    val branch: String get() = "codex/$id"

    companion object {
        /**
         * Task ids travel through the workflow into a git ref, so they are kept
         * to the charset the workflow validates: `^[A-Za-z0-9._-]{1,64}$`.
         */
        fun newId(nowMillis: Long, randomSuffix: String): String =
            "t$nowMillis-$randomSuffix"
    }
}
