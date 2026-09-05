package com.mrhakan.codexmobile.data

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class CodexCloudRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: CodexCloudRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/backend-api/"))
            .addConverterFactory(
                CodexCloudClient.json.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(CodexCloudApi::class.java)
        repository = CodexCloudRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `createTask posts the same body the CLI sends`() = runTest {
        server.enqueue(MockResponse().setBody("""{"task": {"id": "task_123"}}"""))

        val id = repository.createTask(
            environmentId = "env_1",
            prompt = "Add a --version flag",
            branch = "main",
        )

        assertEquals("task_123", id)
        val request = server.takeRequest()
        assertEquals("/backend-api/wham/tasks", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"environment_id\":\"env_1\""))
        assertTrue(body.contains("\"branch\":\"main\""))
        assertTrue(body.contains("\"run_environment_in_qa_mode\":false"))
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("\"content_type\":\"text\""))
        assertTrue(body.contains("Add a --version flag"))
    }

    @Test
    fun `createTask accepts a top-level id`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id": "task_456"}"""))

        assertEquals(
            "task_456",
            repository.createTask("env_1", "prompt", ""),
        )
    }

    @Test
    fun `listTasks drops archived rows`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"items": [
                  {"id": "t1", "title": "Live", "archived": false, "has_unread_turn": false},
                  {"id": "t2", "title": "Old", "archived": true, "has_unread_turn": false}
                ]}
                """.trimIndent(),
            ),
        )

        val tasks = repository.listTasks()

        assertEquals(listOf("t1"), tasks.map { it.id })
        assertTrue(server.takeRequest().path!!.startsWith("/backend-api/wham/tasks/list?"))
    }

    @Test
    fun `listEnvironments puts pinned entries first`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [{"id": "e2", "label": "zeta"},
                 {"id": "e1", "label": "alpha"},
                 {"id": "e3", "label": "pinned", "is_pinned": true}]
                """.trimIndent(),
            ),
        )

        assertEquals(
            listOf("e3", "e1", "e2"),
            repository.listEnvironments().map { it.id },
        )
        assertEquals("/backend-api/wham/environments", server.takeRequest().path)
    }

    @Test
    fun `getThread reads the task details endpoint`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"task": {"id": "t1", "title": "A task"},
                 "current_assistant_turn": {"turn_status": "completed",
                   "output_items": [{"type": "message",
                     "content": [{"content_type": "text", "text": "done"}]}]}}
                """.trimIndent(),
            ),
        )

        val thread = repository.getThread("t1")

        assertEquals("A task", thread.title)
        assertEquals("done", thread.messages.single().text)
        assertEquals("/backend-api/wham/tasks/t1", server.takeRequest().path)
    }
}
