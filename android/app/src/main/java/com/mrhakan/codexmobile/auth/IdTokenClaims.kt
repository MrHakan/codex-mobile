package com.mrhakan.codexmobile.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The parts of the OAuth id_token the app needs. The ChatGPT account id is
 * required as a `ChatGPT-Account-Id` header on every backend call, and it only
 * exists inside the token's custom `https://api.openai.com/auth` claim.
 *
 * The token is not verified here — it came straight from the token endpoint
 * over TLS and is only read for display and routing, never for authorization.
 */
@Serializable
data class IdTokenClaims(
    val email: String? = null,
    @SerialName("https://api.openai.com/auth") val auth: AuthClaims? = null,
    @SerialName("https://api.openai.com/profile") val profile: ProfileClaims? = null,
) {
    val accountId: String? get() = auth?.chatgptAccountId
    val planType: String? get() = auth?.chatgptPlanType
    val emailAddress: String? get() = email ?: profile?.email

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Returns null for anything that is not a readable JWT payload. */
        fun parse(idToken: String): IdTokenClaims? {
            val payload = idToken.split('.').getOrNull(1) ?: return null
            return runCatching {
                // java.util.Base64 is available from API 26, the app's minSdk,
                // and unlike android.util.Base64 it also works in JVM tests.
                val bytes = java.util.Base64.getUrlDecoder().decode(payload)
                json.decodeFromString<IdTokenClaims>(String(bytes, Charsets.UTF_8))
            }.getOrNull()
        }
    }
}

@Serializable
data class AuthClaims(
    @SerialName("chatgpt_account_id") val chatgptAccountId: String? = null,
    @SerialName("chatgpt_plan_type") val chatgptPlanType: String? = null,
    @SerialName("chatgpt_user_id") val chatgptUserId: String? = null,
)

@Serializable
data class ProfileClaims(val email: String? = null)
