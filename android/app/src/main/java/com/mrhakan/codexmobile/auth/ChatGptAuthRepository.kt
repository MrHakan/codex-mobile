package com.mrhakan.codexmobile.auth

import kotlinx.coroutines.delay

/** What the sign-in screen shows while the user approves the device code. */
data class DeviceAuthPrompt(
    val userCode: String,
    val verificationUrl: String,
    val intervalSeconds: Long,
)

/**
 * Drives the ChatGPT device-auth flow and keeps the tokens fresh.
 *
 * Values are the ones the Codex CLI itself uses, so a session created here is
 * the same kind of session `codex login --device-auth` creates.
 */
class ChatGptAuthRepository(
    private val api: ChatGptAuthApi,
    private val store: TokenStorage,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    val authState: AuthState? get() = store.authState
    val isSignedIn: Boolean get() = store.authState != null

    suspend fun requestDeviceCode(): Pair<DeviceAuthPrompt, UserCodeResponse> {
        val response = api.requestUserCode(UserCodeRequest(clientId = CLIENT_ID))
        val prompt = DeviceAuthPrompt(
            userCode = response.userCode,
            verificationUrl = VERIFICATION_URL,
            intervalSeconds = response.intervalSeconds,
        )
        return prompt to response
    }

    /**
     * Polls until the user approves the code in a browser, then exchanges the
     * returned authorization code for tokens and stores them.
     */
    suspend fun awaitApproval(userCode: UserCodeResponse): Result<AuthState> {
        val deadline = clock() + MAX_WAIT_MILLIS
        val intervalMillis = userCode.intervalSeconds * 1000L

        while (clock() < deadline) {
            val response = runCatching {
                api.pollDeviceToken(
                    DeviceTokenRequest(
                        deviceAuthId = userCode.deviceAuthId,
                        userCode = userCode.userCode,
                    ),
                )
            }.getOrElse { return Result.failure(it) }

            val body = response.body()
            when {
                response.isSuccessful && body != null -> return exchange(body)
                // The device-auth endpoint answers 403/404 until approval.
                response.code() == 403 || response.code() == 404 -> delay(intervalMillis)
                else -> return Result.failure(
                    IllegalStateException("Device auth failed with HTTP ${response.code()}"),
                )
            }
        }
        return Result.failure(IllegalStateException("Sign-in timed out. Start again."))
    }

    private suspend fun exchange(code: DeviceTokenResponse): Result<AuthState> = runCatching {
        val tokens = api.exchangeCode(
            code = code.authorizationCode,
            redirectUri = DEVICE_REDIRECT_URI,
            clientId = CLIENT_ID,
            codeVerifier = code.codeVerifier,
        )
        val state = AuthState.from(
            idToken = tokens.idToken,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            nowMillis = clock(),
        )
        store.authState = state
        state
    }

    /**
     * Exchanges the refresh token for a new access token. Returns null when the
     * refresh token is no longer usable, which means the user must sign in
     * again.
     */
    suspend fun refresh(): AuthState? {
        val current = store.authState ?: return null
        val response = runCatching {
            api.refresh(RefreshRequest(clientId = CLIENT_ID, refreshToken = current.refreshToken))
        }.getOrElse { return null }

        val idToken = response.idToken ?: current.idToken
        val accessToken = response.accessToken ?: return null
        val refreshed = AuthState.from(
            idToken = idToken,
            accessToken = accessToken,
            // The endpoint rotates refresh tokens; keep the old one when it does not.
            refreshToken = response.refreshToken ?: current.refreshToken,
            nowMillis = clock(),
        )
        store.authState = refreshed
        return refreshed
    }

    fun signOut() {
        store.clear()
    }

    companion object {
        /** The Codex CLI's OAuth client id. */
        const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        const val ISSUER = "https://auth.openai.com/"
        const val VERIFICATION_URL = "https://auth.openai.com/codex/device"
        const val DEVICE_REDIRECT_URI = "https://auth.openai.com/deviceauth/callback"

        private const val MAX_WAIT_MILLIS = 15 * 60 * 1000L
    }
}
