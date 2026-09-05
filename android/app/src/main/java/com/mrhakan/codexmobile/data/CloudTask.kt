package com.mrhakan.codexmobile.data

/** Task state as the UI shows it, derived from `task_status_display`. */
enum class CloudTaskStatus {
    PENDING,
    READY,
    APPLIED,
    ERROR,
    ;

    val isRunning: Boolean get() = this == PENDING
}

data class DiffStat(
    val filesChanged: Int = 0,
    val linesAdded: Int = 0,
    val linesRemoved: Int = 0,
) {
    val isEmpty: Boolean get() = filesChanged == 0 && linesAdded == 0 && linesRemoved == 0
}

/** A row in the task list. */
data class CloudTaskSummary(
    val id: String,
    val title: String,
    val status: CloudTaskStatus,
    val updatedAtMillis: Long?,
    val diffStat: DiffStat,
    val environmentLabel: String? = null,
    val pullRequestUrl: String? = null,
    val hasUnread: Boolean = false,
    val archived: Boolean = false,
)

/** One message in the thread view. */
data class ThreadMessage(
    val author: Author,
    val text: String,
) {
    enum class Author { USER, ASSISTANT }
}

/** The full conversation for one task. */
data class CloudTaskThread(
    val id: String,
    val title: String,
    val status: CloudTaskStatus,
    val messages: List<ThreadMessage>,
    val diff: String? = null,
    val diffStat: DiffStat = DiffStat(),
    val errorMessage: String? = null,
    val environmentId: String? = null,
    val pullRequestUrl: String? = null,
    val updatedAtMillis: Long? = null,
)
