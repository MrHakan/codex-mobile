package com.mrhakan.codexmobile.auth

import kotlinx.serialization.Serializable

/** Everything a signed-in session needs, as persisted in the encrypted store. */
@Serializable
data class AuthState(
    val idToken: String,
    val accessToken: String,
    val refreshToken: String,
    val accountId: String? = null,
    val email: String? = null,
    val planType: String? = null,
    val obtainedAtEpochMillis: Long = 0L,
) {
    companion object {
        fun from(
            idToken: String,
            accessToken: String,
            refreshToken: String,
            nowMillis: Long,
        ): AuthState {
            val claims = IdTokenClaims.parse(idToken)
            return AuthState(
                idToken = idToken,
                accessToken = accessToken,
                refreshToken = refreshToken,
                accountId = claims?.accountId,
                email = claims?.emailAddress,
                planType = claims?.planType,
                obtainedAtEpochMillis = nowMillis,
            )
        }
    }
}
