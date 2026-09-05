package com.mrhakan.codexmobile.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer

/** Where the serialized task history is kept; implemented by the encrypted store. */
interface TaskHistoryPersistence {
    var taskHistoryJson: String?
}

/**
 * Local task history. Small enough that a JSON blob in the encrypted
 * preferences beats pulling in a database; prompts can be sensitive, so it
 * shares the encrypted store with the token.
 */
class TaskStore(private val secureStore: TaskHistoryPersistence) {

    private val serializer = ListSerializer(CodexTask.serializer())

    private val _tasks = MutableStateFlow(load())
    val tasks: StateFlow<List<CodexTask>> = _tasks.asStateFlow()

    fun upsert(task: CodexTask) {
        val updated = buildList {
            add(task)
            addAll(_tasks.value.filterNot { it.id == task.id })
        }.take(MAX_TASKS)
        _tasks.value = updated
        persist(updated)
    }

    fun find(taskId: String): CodexTask? = _tasks.value.firstOrNull { it.id == taskId }

    fun remove(taskId: String) {
        val updated = _tasks.value.filterNot { it.id == taskId }
        _tasks.value = updated
        persist(updated)
    }

    fun clear() {
        _tasks.value = emptyList()
        persist(emptyList())
    }

    private fun load(): List<CodexTask> {
        val raw = secureStore.taskHistoryJson ?: return emptyList()
        return runCatching { GitHubClients.json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
    }

    private fun persist(tasks: List<CodexTask>) {
        secureStore.taskHistoryJson = GitHubClients.json.encodeToString(serializer, tasks)
    }

    private companion object {
        const val MAX_TASKS = 50
    }
}
