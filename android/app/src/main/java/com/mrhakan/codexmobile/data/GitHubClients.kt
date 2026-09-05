package com.mrhakan.codexmobile.data

import com.mrhakan.codexmobile.auth.DeviceFlowApi
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Builds the two Retrofit clients the app talks to. */
object GitHubClients {

    const val API_BASE_URL = "https://api.github.com/"
    const val WEB_BASE_URL = "https://github.com/"

    private const val USER_AGENT = "codex-mobile-android"
    private const val API_VERSION = "2022-11-28"

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val converterFactory = json.asConverterFactory("application/json".toMediaType())

    /**
     * @param tokenProvider consulted per request so a re-login takes effect
     *   without rebuilding the client. Returning null sends an unauthenticated
     *   request, which GitHub answers with 401 rather than leaking anything.
     */
    fun createApi(tokenProvider: () -> String?): GitHubApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .header("User-Agent", USER_AGENT)
                tokenProvider()?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(client)
            .addConverterFactory(converterFactory)
            .build()
            .create(GitHubApi::class.java)
    }

    fun createDeviceFlowApi(): DeviceFlowApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build(),
                )
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(WEB_BASE_URL)
            .client(client)
            .addConverterFactory(converterFactory)
            .build()
            .create(DeviceFlowApi::class.java)
    }
}
