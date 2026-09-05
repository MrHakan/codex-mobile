package com.mrhakan.codexmobile

import android.content.Context
import com.mrhakan.codexmobile.auth.ChatGptAuthRepository
import com.mrhakan.codexmobile.auth.SecureTokenStore
import com.mrhakan.codexmobile.data.CodexCloudClient
import com.mrhakan.codexmobile.data.CodexCloudRepository

/**
 * Hand-rolled dependency wiring. The app has one graph and one screen stack, so
 * a DI framework would cost more than it saves.
 */
object ServiceLocator {

    @Volatile
    private var graph: Graph? = null

    class Graph(context: Context) {
        val secureStore = SecureTokenStore(context)
        val authRepository = ChatGptAuthRepository(
            api = CodexCloudClient.createAuthApi(),
            store = secureStore,
        )
        val cloudRepository = CodexCloudRepository(
            CodexCloudClient.createCloudApi(authRepository),
        )
    }

    fun graph(context: Context): Graph =
        graph ?: synchronized(this) {
            graph ?: Graph(context.applicationContext).also { graph = it }
        }
}
