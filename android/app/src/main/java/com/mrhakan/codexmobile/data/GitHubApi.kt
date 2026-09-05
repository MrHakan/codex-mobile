package com.mrhakan.codexmobile.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The slice of api.github.com the app needs: list repos/branches, trigger the
 * `codex-cloud-agent` workflow, and follow the resulting run and pull request.
 */
interface GitHubApi {
    @GET("user")
    suspend fun getAuthenticatedUser(): GitHubUser

    @GET("user/repos")
    suspend fun listRepositories(
        @Query("sort") sort: String = "pushed",
        @Query("direction") direction: String = "desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("affiliation") affiliation: String = "owner,collaborator,organization_member",
    ): List<Repository>

    @GET("repos/{owner}/{repo}/branches")
    suspend fun listBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
    ): List<Branch>

    /** Returns 204 with no body on success. */
    @POST("repos/{owner}/{repo}/actions/workflows/{workflow}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow") workflowFileName: String,
        @Body body: WorkflowDispatchRequest,
    ): Response<Unit>

    /**
     * Alternative trigger for clients that cannot use `workflow_dispatch`. Also
     * returns 204 and, unlike `workflow_dispatch`, gives no hint about which
     * run it started — the app matches on the task id either way.
     */
    @POST("repos/{owner}/{repo}/dispatches")
    suspend fun dispatchRepositoryEvent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: RepositoryDispatchRequest,
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/workflows/{workflow}/runs")
    suspend fun listWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow") workflowFileName: String,
        @Query("event") event: String? = null,
        @Query("per_page") perPage: Int = 30,
    ): WorkflowRunsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{runId}")
    suspend fun getWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long,
    ): WorkflowRun

    @GET("repos/{owner}/{repo}/actions/runs/{runId}/jobs")
    suspend fun listRunJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long,
    ): JobsResponse

    @GET("repos/{owner}/{repo}/pulls")
    suspend fun listPullRequests(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        /** `owner:branch` */
        @Query("head") head: String,
        @Query("state") state: String = "all",
        @Query("per_page") perPage: Int = 10,
    ): List<PullRequest>
}
