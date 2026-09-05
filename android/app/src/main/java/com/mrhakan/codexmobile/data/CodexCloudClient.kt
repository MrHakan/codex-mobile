package com.mrhakan.codexmobile.data

import com.mrhakan.codexmobile.auth.ChatGptAuthApi
import com.mrhakan.codexmobile.auth.ChatGptAuthRepository
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Builds the auth and Codex Cloud clients. */
object CodexCloudClient {

    const val CLOUD_BASE_URL = "https://chatgpt.com/backend-api/"

    /** Where a task lives in the web UI, for the "open in browser" action. */
    fun taskUrl(taskId: String): String = "https://chatgpt.com/codex/tasks/$taskId"

    private const val USER_AGENT = "codex-mobile-android"

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        // The backend expects the full request body, defaults included, exactly
        // as the Rust client sends it.
        encodeDefaults = true
    }

    private val converterFactory = json.asConverterFactory("application/json".toMediaType())

    fun createAuthApi(): ChatGptAuthApi = Retrofit.Builder()
        .baseUrl(ChatGptAuthRepository.ISSUER)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", USER_AGENT)
                            .header("Accept", "application/json")
                            .build(),
                    )
                }
                .build(),
        )
        .addConverterFactory(converterFactory)
        .build()
        .create(ChatGptAuthApi::class.java)

    fun createCloudApi(auth: ChatGptAuthRepository): CodexCloudApi = Retrofit.Builder()
        .baseUrl(CLOUD_BASE_URL)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(ChatGptAuthInterceptor(auth))
                .build(),
        )
        .addConverterFactory(converterFactory)
        .build()
        .create(CodexCloudApi::class.java)
}

/**
 * Signs every request with the stored ChatGPT access token, and retries once
 * with a refreshed token when the backend answers 401 — access tokens are
 * short-lived, so this is the normal path after the app has been closed for a
 * while, not an error case.
 */
class ChatGptAuthInterceptor(private val auth: ChatGptAuthRepository) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val state = auth.authState
        val response = chain.proceed(
            chain.request().signed(state?.accessToken, state?.accountId),
        )
        if (response.code != 401) return response

        val refreshed = runCatching {
            kotlinx.coroutines.runBlocking { auth.refresh() }
        }.getOrNull() ?: return response

        response.close()
        return chain.proceed(
            chain.request().signed(refreshed.accessToken, refreshed.accountId),
        )
    }

    private fun Request.signed(accessToken: String?, accountId: String?): Request =
        newBuilder()
            .header("User-Agent", "codex-mobile-android")
            .header("Accept", "application/json")
            .apply {
                accessToken?.let { header("Authorization", "Bearer $it") }
                accountId?.let { header("ChatGPT-Account-Id", it) }
            }
            .build()
}
