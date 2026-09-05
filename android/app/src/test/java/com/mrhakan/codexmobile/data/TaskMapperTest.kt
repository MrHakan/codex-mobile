package com.mrhakan.codexmobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Codex Cloud payloads are not publicly documented, so these fixtures are
 * modelled on what `codex-rs/backend-client` deserializes.
 */
class TaskMapperTest {

    private val json = CodexCloudClient.json

    @Test
    fun `thread pairs the user prompt with assistant messages`() {
        val details = json.decodeFromString<TaskDetails>(
            """
            {
              "task": {"id": "t_1", "title": "Fix the flaky test", "environment_id": "env_1"},
              "current_user_turn": {
                "input_items": [
                  {"type": "message", "role": "user",
                   "content": [{"content_type": "text", "text": "First line"},
                               {"content_type": "text", "text": "Second line"}]}
                ]
              },
              "current_assistant_turn": {
                "turn_status": "completed",
                "output_items": [
                  {"type": "message",
                   "content": [{"content_type": "text", "text": "Done, see the diff."}]}
                ]
              }
            }
            """.trimIndent(),
        )

        val thread = TaskMapper.thread("t_1", details)

        assertEquals("Fix the flaky test", thread.title)
        assertEquals(2, thread.messages.size)
        assertEquals(ThreadMessage.Author.USER, thread.messages[0].author)
        assertEquals("First line\n\nSecond line", thread.messages[0].text)
        assertEquals("Done, see the diff.", thread.messages[1].text)
    }

    @Test
    fun `worklog assistant messages are picked up when output items carry none`() {
        val details = json.decodeFromString<TaskDetails>(
            """
            {
              "current_assistant_turn": {
                "worklog": {
                  "messages": [
                    {"author": {"role": "user"}, "content": {"parts": ["ignored"]}},
                    {"author": {"role": "assistant"},
                     "content": {"parts": ["from worklog", {"content_type": "text", "text": "and structured"}]}}
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf("from worklog", "and structured"),
            TaskMapper.assistantMessages(details),
        )
    }

    @Test
    fun `the diff turn wins over the assistant turn`() {
        val details = json.decodeFromString<TaskDetails>(
            """
            {
              "current_diff_task_turn": {
                "output_items": [{"type": "output_diff", "diff": "diff --git a/a b/a\n+one\n"}]
              },
              "current_assistant_turn": {
                "output_items": [{"type": "pr", "output_diff": {"diff": "diff --git a/b b/b\n"}}]
              }
            }
            """.trimIndent(),
        )

        assertTrue(TaskMapper.unifiedDiff(details)!!.contains("a/a"))
    }

    @Test
    fun `a pull request output diff is used when no plain diff exists`() {
        val details = json.decodeFromString<TaskDetails>(
            """
            {
              "current_assistant_turn": {
                "output_items": [{"type": "pr", "output_diff": {"diff": "diff --git a/b b/b\n-old\n"}}]
              }
            }
            """.trimIndent(),
        )

        assertTrue(TaskMapper.unifiedDiff(details)!!.contains("a/b"))
    }

    @Test
    fun `status comes from the latest turn, then from state`() {
        fun status(body: String) =
            TaskMapper.status(json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject)

        assertEquals(
            CloudTaskStatus.READY,
            status("""{"latest_turn_status_display": {"turn_status": "completed"}}"""),
        )
        assertEquals(
            CloudTaskStatus.PENDING,
            status("""{"latest_turn_status_display": {"turn_status": "in_progress"}}"""),
        )
        assertEquals(
            CloudTaskStatus.ERROR,
            status("""{"latest_turn_status_display": {"turn_status": "failed"}}"""),
        )
        assertEquals(CloudTaskStatus.APPLIED, status("""{"state": "applied"}"""))
        assertEquals(CloudTaskStatus.PENDING, status("""{}"""))
    }

    @Test
    fun `list rows carry status, diff stats and the pull request`() {
        val page = json.decodeFromString<TaskListPage>(
            """
            {
              "items": [
                {
                  "id": "t_1",
                  "title": "Add a flag",
                  "updated_at": 1700000000.5,
                  "archived": false,
                  "has_unread_turn": true,
                  "task_status_display": {
                    "environment_label": "openai/codex",
                    "latest_turn_status_display": {
                      "turn_status": "completed",
                      "diff_stats": {"files_modified": 2, "lines_added": 10, "lines_removed": 3}
                    }
                  },
                  "pull_requests": [
                    {"id": "p1", "assistant_turn_id": "a1",
                     "pull_request": {"number": 7, "url": "https://github.com/o/r/pull/7",
                                      "state": "open", "merged": false, "mergeable": true}}
                  ]
                }
              ],
              "cursor": "next"
            }
            """.trimIndent(),
        )

        val summary = TaskMapper.summary(page.items.single())

        assertEquals(CloudTaskStatus.READY, summary.status)
        assertEquals(DiffStat(2, 10, 3), summary.diffStat)
        assertEquals("openai/codex", summary.environmentLabel)
        assertEquals("https://github.com/o/r/pull/7", summary.pullRequestUrl)
        assertEquals(1700000000500L, summary.updatedAtMillis)
        assertTrue(summary.hasUnread)
        assertEquals("next", page.cursor)
    }

    @Test
    fun `diff stats fall back to counting the diff`() {
        val stat = TaskMapper.diffStat(
            """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1,2 +1,2 @@
            -old
            +new
            +extra
            """.trimIndent(),
        )

        assertEquals(DiffStat(filesChanged = 1, linesAdded = 2, linesRemoved = 1), stat)
    }

    @Test
    fun `a failed turn surfaces its error`() {
        val details = json.decodeFromString<TaskDetails>(
            """
            {"current_assistant_turn": {"turn_status": "failed",
             "error": {"code": "sandbox_error", "message": "container died"}}}
            """.trimIndent(),
        )

        val thread = TaskMapper.thread("t_1", details)

        assertEquals("sandbox_error: container died", thread.errorMessage)
        assertNull(thread.diff)
    }
}
