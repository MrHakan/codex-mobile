package com.mrhakan.codexmobile.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject

/** Reads and writes Codex Cloud tasks, in the shapes the UI wants. */
class CodexCloudRepository(private val api: CodexCloudApi) {

    suspend fun listEnvironments(): List<CodeEnvironment> =
        api.listEnvironments().sortedWith(
            compareByDescending<CodeEnvironment> { it.isPinned }
                .thenBy { it.label ?: it.id },
        )

    suspend fun listTasks(environmentId: String? = null, limit: Int = 25): List<CloudTaskSummary> =
        api.listTasks(limit = limit, environmentId = environmentId)
            .items
            .filterNot { it.archived }
            .map(TaskMapper::summary)

    suspend fun getThread(taskId: String): CloudTaskThread =
        TaskMapper.thread(taskId, api.getTask(taskId))

    /**
     * Creates a task. The body matches what `codex cloud exec` sends, down to
     * the `input_items` message shape.
     */
    suspend fun createTask(
        environmentId: String,
        prompt: String,
        branch: String,
        qaMode: Boolean = false,
    ): String {
        val response = api.createTask(
            CreateTaskRequest(
                newTask = NewTask(
                    environmentId = environmentId,
                    branch = branch,
                    qaMode = qaMode,
                ),
                inputItems = listOf(userMessage(prompt)),
            ),
        )
        return response.taskId
            ?: error("Codex Cloud accepted the task but returned no id.")
    }

    private fun userMessage(prompt: String): JsonElement = buildJsonObject {
        put("type", "message")
        put("role", "user")
        putJsonArray("content") {
            addJsonObject {
                put("content_type", "text")
                put("text", prompt)
            }
        }
    }
}
