package com.mrhakan.codexmobile.auth

import com.mrhakan.codexmobile.data.CodexCloudClient
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ChatGptAuthTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ChatGptAuthApi
    private lateinit var store: FakeStore
    private lateinit var repository: ChatGptAuthRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                CodexCloudClient.json.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(ChatGptAuthApi::class.java)
        store = FakeStore()
        repository = ChatGptAuthRepository(api, store, clock = { clock })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private var clock = 1_700_000_000_000L

    @Test
    fun `device auth polls until approval and exchanges the code`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"device_auth_id": "dev_1", "user_code": "ABCD-1234", "interval": "1"}""",
            ),
        )
        // Not approved yet, then approved.
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(
            MockResponse().setBody(
                """{"authorization_code": "code_1", "code_challenge": "chal", "code_verifier": "ver"}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"id_token": "$ID_TOKEN", "access_token": "at", "refresh_token": "rt"}""",
            ),
        )

        val (prompt, userCode) = repository.requestDeviceCode()
        assertEquals("ABCD-1234", prompt.userCode)
        assertEquals(ChatGptAuthRepository.VERIFICATION_URL, prompt.verificationUrl)

        val state = repository.awaitApproval(userCode).getOrThrow()

        assertEquals("at", state.accessToken)
        assertEquals("acc_123", state.accountId)
        assertEquals("user@example.com", state.email)
        assertEquals("plus", state.planType)

        val userCodeRequest = server.takeRequest()
        assertEquals("/api/accounts/deviceauth/usercode", userCodeRequest.path)
        assertTrue(
            userCodeRequest.body.readUtf8()
                .contains(ChatGptAuthRepository.CLIENT_ID),
        )
        assertEquals("/api/accounts/deviceauth/token", server.takeRequest().path)
        assertEquals("/api/accounts/deviceauth/token", server.takeRequest().path)

        val exchange = server.takeRequest()
        assertEquals("/oauth/token", exchange.path)
        val body = exchange.body.readUtf8()
        assertTrue(body.contains("grant_type=authorization_code"))
        assertTrue(body.contains("code=code_1"))
        assertTrue(body.contains("code_verifier=ver"))
        assertTrue(body.contains("deviceauth%2Fcallback"))
    }

    @Test
    fun `refresh keeps the old refresh token when the endpoint does not rotate it`() = runTest {
        store.authState = AuthState(
            idToken = ID_TOKEN,
            accessToken = "old",
            refreshToken = "rt",
            accountId = "acc_123",
        )
        server.enqueue(MockResponse().setBody("""{"access_token": "new"}"""))

        val refreshed = repository.refresh()!!

        assertEquals("new", refreshed.accessToken)
        assertEquals("rt", refreshed.refreshToken)
        assertEquals("acc_123", refreshed.accountId)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"grant_type\":\"refresh_token\""))
    }

    @Test
    fun `refresh gives up when the refresh token is rejected`() = runTest {
        store.authState = AuthState(ID_TOKEN, "old", "rt")
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))

        assertNull(repository.refresh())
    }

    @Test
    fun `id token claims are read from the OpenAI auth claim`() {
        val claims = IdTokenClaims.parse(ID_TOKEN)!!
        assertEquals("acc_123", claims.accountId)
        assertEquals("user@example.com", claims.emailAddress)
        assertEquals("plus", claims.planType)
    }

    @Test
    fun `a malformed id token yields no claims`() {
        assertNull(IdTokenClaims.parse("not-a-jwt"))
    }

    private class FakeStore : TokenStorage {
        override var authState: AuthState? = null
        override var lastEnvironmentId: String? = null
        override fun clear() {
            authState = null
            lastEnvironmentId = null
        }
    }

    private companion object {
        /** header.payload.signature with an unpadded base64url payload. */
        val ID_TOKEN: String = buildString {
            append("e30.")
            append(
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    """
                    {"email":"user@example.com",
                     "https://api.openai.com/auth":{"chatgpt_account_id":"acc_123",
                                                    "chatgpt_plan_type":"plus"}}
                    """.trimIndent().toByteArray(),
                ),
            )
            append(".sig")
        }
    }
}
