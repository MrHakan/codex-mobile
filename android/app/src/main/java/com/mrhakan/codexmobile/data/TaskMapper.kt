package com.mrhakan.codexmobile.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns the backend's loosely-typed payloads into what the UI renders.
 *
 * The extraction rules mirror `codex-rs/cloud-tasks-client` and
 * `codex-rs/backend-client` field for field, because the response shape is not
 * publicly documented and the Rust client is the reference for it.
 */
object TaskMapper {

    fun summary(item: TaskListItem): CloudTaskSummary {
        val statusDisplay = item.statusDisplay
        return CloudTaskSummary(
            id = item.id,
            title = item.title.ifBlank { "(untitled)" },
            status = status(statusDisplay),
            updatedAtMillis = epochMillis(item.updatedAt ?: item.createdAt)
                ?: epochMillis(latestTurnTimestamp(statusDisplay)),
            diffStat = diffStat(statusDisplay),
            environmentLabel = environmentLabel(statusDisplay),
            pullRequestUrl = pullRequestUrl(item.pullRequests),
            hasUnread = item.hasUnreadTurn,
            archived = item.archived,
        )
    }

    fun thread(id: String, details: TaskDetails): CloudTaskThread {
        val meta = details.task
        val statusDisplay = details.statusDisplay ?: meta?.statusDisplay
        val diff = unifiedDiff(details)
        val statFromDisplay = diffStat(statusDisplay)

        val messages = buildList {
            userPrompt(details)?.let { add(ThreadMessage(ThreadMessage.Author.USER, it)) }
            assistantMessages(details).forEach {
                add(ThreadMessage(ThreadMessage.Author.ASSISTANT, it))
            }
        }

        return CloudTaskThread(
            id = meta?.id ?: id,
            title = meta?.title?.ifBlank { null } ?: "(untitled)",
            status = status(statusDisplay),
            messages = messages,
            diff = diff,
            diffStat = if (statFromDisplay.isEmpty && diff != null) diffStat(diff) else statFromDisplay,
            errorMessage = details.currentAssistantTurn?.error?.summary(),
            environmentId = meta?.environmentId,
            pullRequestUrl = pullRequestUrl(meta?.pullRequests.orEmpty()),
            updatedAtMillis = epochMillis(meta?.updatedAt ?: meta?.createdAt),
        )
    }

    /** `latest_turn_status_display.turn_status`, falling back to `state`. */
    fun status(statusDisplay: JsonObject?): CloudTaskStatus {
        val display = statusDisplay ?: return CloudTaskStatus.PENDING
        latestTurn(display)?.get("turn_status")?.stringOrNull()?.let { turnStatus ->
            return when (turnStatus) {
                "completed" -> CloudTaskStatus.READY
                "failed", "cancelled" -> CloudTaskStatus.ERROR
                else -> CloudTaskStatus.PENDING
            }
        }
        return when (display["state"]?.stringOrNull()) {
            "ready" -> CloudTaskStatus.READY
            "applied" -> CloudTaskStatus.APPLIED
            "error" -> CloudTaskStatus.ERROR
            else -> CloudTaskStatus.PENDING
        }
    }

    fun diffStat(statusDisplay: JsonObject?): DiffStat {
        val stats = latestTurn(statusDisplay ?: return DiffStat())
            ?.get("diff_stats")?.asObjectOrNull()
            ?: return DiffStat()
        return DiffStat(
            filesChanged = stats["files_modified"]?.intOrNull()?.coerceAtLeast(0) ?: 0,
            linesAdded = stats["lines_added"]?.intOrNull()?.coerceAtLeast(0) ?: 0,
            linesRemoved = stats["lines_removed"]?.intOrNull()?.coerceAtLeast(0) ?: 0,
        )
    }

    /** Counts a unified diff when the backend did not send diff stats. */
    fun diffStat(diff: String): DiffStat {
        var files = 0
        var added = 0
        var removed = 0
        diff.lineSequence().forEach { line ->
            when {
                line.startsWith("diff --git ") -> files++
                line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") -> Unit
                line.startsWith("+") -> added++
                line.startsWith("-") -> removed++
            }
        }
        if (files == 0 && diff.isNotBlank()) files = 1
        return DiffStat(files, added, removed)
    }

    /** The diff turn wins over the assistant turn, as in the Rust client. */
    fun unifiedDiff(details: TaskDetails): String? =
        listOfNotNull(details.currentDiffTaskTurn, details.currentAssistantTurn)
            .firstNotNullOfOrNull { turn -> turn.outputItems.firstNotNullOfOrNull(::diffOf) }

    fun assistantMessages(details: TaskDetails): List<String> =
        listOfNotNull(details.currentDiffTaskTurn, details.currentAssistantTurn)
            .flatMap { turn ->
                turn.outputItems
                    .filter { it.kind == "message" }
                    .flatMap { item -> item.content.mapNotNull { it.text?.ifBlank { null } } } +
                    turn.worklog?.messages.orEmpty()
                        .filter { it.isAssistant }
                        .flatMap { message ->
                            message.content?.parts.orEmpty()
                                .mapNotNull { it.text?.ifBlank { null } }
                        }
            }

    fun userPrompt(details: TaskDetails): String? {
        val turn = details.currentUserTurn ?: return null
        val parts = turn.inputItems
            .filter { it.kind == "message" }
            .filter { it.role == null || it.role.equals("user", ignoreCase = true) }
            .flatMap { item -> item.content.mapNotNull { it.text?.ifBlank { null } } }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    private fun diffOf(item: TurnItem): String? = when (item.kind) {
        "output_diff" -> item.diff?.ifBlank { null }
        "pr" -> item.outputDiff?.diff?.ifBlank { null }
        else -> null
    }

    private fun pullRequestUrl(entries: List<PullRequestEntry>): String? =
        entries.firstNotNullOfOrNull { it.pullRequest?.url?.ifBlank { null } }

    private fun environmentLabel(statusDisplay: JsonObject?): String? =
        statusDisplay?.get("environment_label")?.stringOrNull()

    private fun latestTurnTimestamp(statusDisplay: JsonObject?): Double? {
        val latest = latestTurn(statusDisplay ?: return null) ?: return null
        return (latest["updated_at"] ?: latest["created_at"])?.doubleOrNull()
    }

    private fun latestTurn(statusDisplay: JsonObject): JsonObject? =
        statusDisplay["latest_turn_status_display"]?.asObjectOrNull()

    /** Backend timestamps are fractional epoch seconds. */
    private fun epochMillis(value: Double?): Long? =
        value?.takeIf { it > 0 }?.let { (it * 1000.0).toLong() }
}

private fun kotlinx.serialization.json.JsonElement.asObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()

private fun kotlinx.serialization.json.JsonElement.stringOrNull(): String? =
    runCatching { jsonPrimitive.takeIf { it.isString }?.content }.getOrNull()

private fun kotlinx.serialization.json.JsonElement.intOrNull(): Int? =
    runCatching { jsonPrimitive.intOrNull }.getOrNull()

private fun kotlinx.serialization.json.JsonElement.doubleOrNull(): Double? =
    runCatching { jsonPrimitive.content.toDoubleOrNull() }.getOrNull()

@Suppress("unused")
private fun kotlinx.serialization.json.JsonElement.booleanOrNull(): Boolean? =
    runCatching { jsonPrimitive.booleanOrNull }.getOrNull()
