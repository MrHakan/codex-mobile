package com.mrhakan.codexmobile.auth

import com.mrhakan.codexmobile.data.GitHubClients
import com.mrhakan.codexmobile.data.GitHubUser
import kotlinx.coroutines.delay

/**
 * GitHub sign-in. Two paths, both ending in a token that only ever lives in
 * [SecureTokenStore]:
 *
 *  * the OAuth **device flow**, which suits an app with no backend and so no
 *    redirect URI (requires an OAuth app client id at build time), and
 *  * a pasted fine-grained or classic **personal access token**, which is the
 *    quickest path for a single-user tool.
 */
class AuthRepository(
    private val store: SecureTokenStore,
    private val deviceFlowApi: DeviceFlowApi = GitHubClients.createDeviceFlowApi(),
) {

    val token: String? get() = store.token
    val isSignedIn: Boolean get() = store.token != null
    val login: String? get() = store.login

    /** Validates [token] against `GET /user` before storing it. */
    suspend fun signInWithToken(token: String): Result<GitHubUser> = runCatching {
        val trimmed = token.trim()
        require(trimmed.isNotEmpty()) { "Token must not be empty" }
        val user = GitHubClients.createApi { trimmed }.getAuthenticatedUser()
        store.token = trimmed
        store.login = user.login
        user
    }

    suspend fun requestDeviceCode(clientId: String): DeviceCodeResponse =
        deviceFlowApi.requestDeviceCode(clientId = clientId, scope = DEVICE_FLOW_SCOPES)

    /**
     * Polls GitHub until the user approves the device code, honouring the
     * server-provided interval and `slow_down` backoff.
     */
    suspend fun awaitDeviceAuthorization(
        clientId: String,
        deviceCode: DeviceCodeResponse,
    ): Result<GitHubUser> {
        var intervalMillis = deviceCode.interval.coerceAtLeast(1) * 1000L
        val deadline = System.currentTimeMillis() + deviceCode.expiresIn * 1000L

        while (System.currentTimeMillis() < deadline) {
            delay(intervalMillis)
            val response = runCatching {
                deviceFlowApi.pollForAccessToken(
                    clientId = clientId,
                    deviceCode = deviceCode.deviceCode,
                )
            }.getOrElse { return Result.failure(it) }

            val accessToken = response.accessToken
            if (!accessToken.isNullOrBlank()) {
                return signInWithToken(accessToken)
            }
            when (response.error) {
                "authorization_pending" -> Unit
                "slow_down" -> intervalMillis += (response.interval?.times(1000L) ?: 5000L)
                else -> return Result.failure(
                    IllegalStateException(
                        response.errorDescription ?: response.error ?: "Device flow failed",
                    ),
                )
            }
        }
        return Result.failure(IllegalStateException("Device code expired; start again."))
    }

    fun signOut() {
        store.token = null
        store.login = null
    }

    companion object {
        /**
         * `repo` covers reading private repos and their branches; `workflow` is
         * what lets the token trigger `workflow_dispatch`.
         */
        const val DEVICE_FLOW_SCOPES = "repo workflow"
    }
}
