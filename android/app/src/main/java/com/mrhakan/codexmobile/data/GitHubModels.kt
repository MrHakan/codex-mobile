package com.mrhakan.codexmobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Subset of the GitHub REST payloads the client actually reads. */
@Serializable
data class GitHubUser(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class RepoOwner(val login: String)

@Serializable
data class Repository(
    val name: String,
    @SerialName("full_name") val fullName: String,
    val owner: RepoOwner,
    @SerialName("default_branch") val defaultBranch: String = "main",
    val private: Boolean = false,
    @SerialName("pushed_at") val pushedAt: String? = null,
) {
    val ownerLogin: String get() = owner.login
}

@Serializable
data class Branch(val name: String)

@Serializable
data class WorkflowDispatchRequest(
    val ref: String,
    val inputs: Map<String, String>,
)

@Serializable
data class RepositoryDispatchRequest(
    @SerialName("event_type") val eventType: String,
    @SerialName("client_payload") val clientPayload: Map<String, String>,
)

@Serializable
data class WorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<WorkflowRun> = emptyList(),
)

@Serializable
data class WorkflowRun(
    val id: Long,
    /** Value of `run-name:`, which carries the client task id. */
    val name: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    /** queued | in_progress | completed | waiting | requested | pending */
    val status: String? = null,
    /** success | failure | cancelled | skipped | timed_out | action_required */
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("run_started_at") val runStartedAt: String? = null,
    val event: String? = null,
)

@Serializable
data class JobsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val jobs: List<Job> = emptyList(),
)

@Serializable
data class Job(
    val id: Long,
    val name: String,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val steps: List<JobStep> = emptyList(),
)

@Serializable
data class JobStep(
    val name: String,
    val number: Int = 0,
    val status: String? = null,
    val conclusion: String? = null,
)

@Serializable
data class PullRequest(
    val number: Int,
    val title: String,
    val state: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("merged_at") val mergedAt: String? = null,
)
