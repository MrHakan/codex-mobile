package com.mrhakan.codexmobile.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Codex Cloud, the same backend `codex cloud` and chatgpt.com/codex talk to.
 * Base URL `https://chatgpt.com/backend-api/`; every call carries the ChatGPT
 * access token and the account id header.
 */
interface CodexCloudApi {

    /** Environments configured in Codex settings — each is a connected repo. */
    @GET("wham/environments")
    suspend fun listEnvironments(): List<CodeEnvironment>

    @GET("wham/environments/by-repo/github/{owner}/{repo}")
    suspend fun listEnvironmentsByRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): List<CodeEnvironment>

    @GET("wham/tasks/list")
    suspend fun listTasks(
        @Query("limit") limit: Int = 25,
        @Query("environment_id") environmentId: String? = null,
        @Query("cursor") cursor: String? = null,
    ): TaskListPage

    @GET("wham/tasks/{id}")
    suspend fun getTask(@Path("id") id: String): TaskDetails

    @POST("wham/tasks")
    suspend fun createTask(@Body body: CreateTaskRequest): CreateTaskResponse
}
