package com.mrhakan.codexmobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexTaskTest {

    @Test
    fun `generated ids satisfy the charset the workflow validates`() {
        val id = CodexTask.newId(1_700_000_000_000L, "beef")
        assertTrue(id.matches(Regex("^[A-Za-z0-9._-]{1,64}$")))
    }

    @Test
    fun `branch name mirrors the workflow`() {
        val task = CodexTask(
            id = "t1-beef",
            prompt = "p",
            targetRepo = "octo/app",
            baseBranch = "main",
            controllerRepo = "octo/app",
            createdAtEpochMillis = 0,
        )
        assertEquals("codex/t1-beef", task.branch)
    }

    @Test
    fun `only finished states are terminal`() {
        assertFalse(TaskStatus.DISPATCHED.isTerminal)
        assertFalse(TaskStatus.QUEUED.isTerminal)
        assertFalse(TaskStatus.RUNNING.isTerminal)
        assertTrue(TaskStatus.SUCCEEDED.isTerminal)
        assertTrue(TaskStatus.FAILED.isTerminal)
        assertTrue(TaskStatus.CANCELLED.isTerminal)
        assertTrue(TaskStatus.DISPATCH_FAILED.isTerminal)
    }

    @Test
    fun `splitRepo rejects malformed names`() {
        assertEquals("octo" to "app", CodexTaskRepository.splitRepo("octo/app"))
        runCatching { CodexTaskRepository.splitRepo("octo") }
            .onSuccess { error("expected failure") }
    }
}
