package com.mrhakan.codexmobile.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * The ChatGPT device-authorization flow, mirroring what `codex login
 * --device-auth` does against `https://auth.openai.com`:
 *
 *  1. `POST /api/accounts/deviceauth/usercode` -> a short code to type in a
 *     browser at https://auth.openai.com/codex/device
 *  2. `POST /api/accounts/deviceauth/token` polled until approval. The server
 *     keeps the PKCE pair and hands it back with the authorization code, so the
 *     app never generates one itself.
 *  3. `POST /oauth/token` exchanges that code for the id/access/refresh tokens.
 *
 * No OpenAI API key is involved anywhere: usage is billed to the signed-in
 * ChatGPT account.
 */
interface ChatGptAuthApi {

    @POST("api/accounts/deviceauth/usercode")
    suspend fun requestUserCode(@Body body: UserCodeRequest): UserCodeResponse

    /**
     * 403/404 means "not approved yet" — the caller waits `interval` seconds and
     * tries again, which is why this returns a raw [Response].
     */
    @POST("api/accounts/deviceauth/token")
    suspend fun pollDeviceToken(@Body body: DeviceTokenRequest): Response<DeviceTokenResponse>

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeCode(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String,
    ): TokenResponse

    @POST("oauth/token")
    suspend fun refresh(@Body body: RefreshRequest): RefreshResponse
}

@Serializable
data class UserCodeRequest(@SerialName("client_id") val clientId: String)

@Serializable
data class UserCodeResponse(
    @SerialName("device_auth_id") val deviceAuthId: String,
    @SerialName("user_code") val userCode: String,
    /** Seconds between polls. The server sends it as a string. */
    val interval: String? = null,
) {
    val intervalSeconds: Long get() = interval?.trim()?.toLongOrNull()?.coerceAtLeast(1) ?: 5
}

@Serializable
data class DeviceTokenRequest(
    @SerialName("device_auth_id") val deviceAuthId: String,
    @SerialName("user_code") val userCode: String,
)

@Serializable
data class DeviceTokenResponse(
    @SerialName("authorization_code") val authorizationCode: String,
    @SerialName("code_challenge") val codeChallenge: String,
    @SerialName("code_verifier") val codeVerifier: String,
)

@Serializable
data class TokenResponse(
    @SerialName("id_token") val idToken: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class RefreshRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("grant_type") val grantType: String = "refresh_token",
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class RefreshResponse(
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)
