package com.mrhakan.codexmobile.data

import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class CodexTaskRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: GitHubApi
    private lateinit var store: TaskStore
    private lateinit var repository: CodexTaskRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                GitHubClients.json.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(GitHubApi::class.java)
        store = TaskStore(InMemoryPersistence())
        repository = CodexTaskRepository(
            api = api,
            store = store,
            clock = { 1_700_000_000_000L },
            random = Random(7),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `submit posts workflow inputs and records the task`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val task = repository.submit(
            prompt = "Add a README section",
            targetRepo = "octo/app",
            baseBranch = "main",
            controllerRepo = "octo/controller",
            workflowRef = "main",
        )

        val request = server.takeRequest()
        assertEquals(
            "/repos/octo/controller/actions/workflows/codex-cloud-agent.yml/dispatches",
            request.path,
        )
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"prompt\":\"Add a README section\""))
        assertTrue(body.contains("\"task_id\":\"${task.id}\""))
        assertTrue(body.contains("\"target_repo\":\"octo/app\""))
        assertEquals(TaskStatus.DISPATCHED, task.status)
        assertEquals(task, store.find(task.id))
        assertTrue(task.branch.startsWith("codex/"))
    }

    @Test
    fun `submit records a dispatch failure without starting a run`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("no access"))

        val task = repository.submit(
            prompt = "Do a thing",
            targetRepo = "octo/controller",
            baseBranch = "main",
            controllerRepo = "octo/controller",
            workflowRef = "main",
        )

        assertEquals(TaskStatus.DISPATCH_FAILED, task.status)
        assertTrue(task.error!!.contains("403"))
        assertNull(task.runId)
    }

    @Test
    fun `refresh matches the run by the task id in run-name`() = runTest {
        val task = dispatchedTask()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "total_count": 2,
                  "workflow_runs": [
                    {"id": 1, "name": "Codex task other", "status": "completed",
                     "conclusion": "success", "html_url": "https://gh/1"},
                    {"id": 42, "name": "Codex task ${task.id}", "status": "in_progress",
                     "html_url": "https://gh/42"}
                  ]
                }
                """.trimIndent(),
            ),
        )
        // jobs lookup for the step summary
        server.enqueue(
            MockResponse().setBody(
                """
                {"total_count": 1, "jobs": [{"id": 9, "name": "Run Codex", "status": "in_progress",
                 "steps": [{"name": "Run Codex", "number": 1, "status": "in_progress"}]}]}
                """.trimIndent(),
            ),
        )

        val updated = repository.refresh(task)

        assertEquals(42L, updated.runId)
        assertEquals(TaskStatus.RUNNING, updated.status)
        assertEquals(listOf("Run Codex: in_progress"), updated.jobSummary)
    }

    @Test
    fun `refresh surfaces the pull request once the run succeeds`() = runTest {
        val task = dispatchedTask().copy(runId = 42L)
        server.enqueue(
            MockResponse().setBody(
                """
                {"id": 42, "name": "Codex task ${task.id}", "status": "completed",
                 "conclusion": "success", "html_url": "https://gh/42"}
                """.trimIndent(),
            ),
        )
        server.enqueue(MockResponse().setBody("""{"total_count": 0, "jobs": []}"""))
        server.enqueue(
            MockResponse().setBody(
                """
                [{"number": 7, "title": "Codex: task ${task.id}", "state": "open",
                  "html_url": "https://github.com/octo/app/pull/7"}]
                """.trimIndent(),
            ),
        )

        val updated = repository.refresh(task)

        assertEquals(TaskStatus.SUCCEEDED, updated.status)
        assertEquals("https://github.com/octo/app/pull/7", updated.pullRequestUrl)

        server.takeRequest() // run
        server.takeRequest() // jobs
        val pullsRequest = server.takeRequest()
        assertTrue(pullsRequest.path!!.startsWith("/repos/octo/app/pulls?"))
        assertTrue(pullsRequest.path!!.contains("head=octo%3Acodex%2F${task.id}"))
    }

    @Test
    fun `refresh keeps the task when the run has not appeared yet`() = runTest {
        val task = dispatchedTask()
        server.enqueue(MockResponse().setBody("""{"total_count": 0, "workflow_runs": []}"""))

        val updated = repository.refresh(task)

        assertEquals(TaskStatus.DISPATCHED, updated.status)
        assertNull(updated.runId)
    }

    @Test
    fun `refresh reports a failed run`() = runTest {
        val task = dispatchedTask().copy(runId = 42L)
        server.enqueue(
            MockResponse().setBody(
                """
                {"id": 42, "status": "completed", "conclusion": "failure", "html_url": "https://gh/42"}
                """.trimIndent(),
            ),
        )
        server.enqueue(MockResponse().setBody("""{"total_count": 0, "jobs": []}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("[]"))

        val updated = repository.refresh(task)

        assertEquals(TaskStatus.FAILED, updated.status)
        assertEquals("failure", updated.conclusion)
        assertNull(updated.pullRequestUrl)
    }

    private suspend fun dispatchedTask(): CodexTask {
        server.enqueue(MockResponse().setResponseCode(204))
        val task = repository.submit(
            prompt = "Do a thing",
            targetRepo = "octo/app",
            baseBranch = "main",
            controllerRepo = "octo/controller",
            workflowRef = "main",
        )
        server.takeRequest()
        return task
    }

    private class InMemoryPersistence : TaskHistoryPersistence {
        override var taskHistoryJson: String? = null
    }
}
