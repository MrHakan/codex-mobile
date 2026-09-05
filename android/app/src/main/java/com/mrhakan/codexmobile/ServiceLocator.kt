package com.mrhakan.codexmobile

import android.content.Context
import com.mrhakan.codexmobile.auth.AuthRepository
import com.mrhakan.codexmobile.auth.SecureTokenStore
import com.mrhakan.codexmobile.data.CodexTaskRepository
import com.mrhakan.codexmobile.data.GitHubClients
import com.mrhakan.codexmobile.data.TaskStore

/**
 * Hand-rolled dependency wiring. The app has one graph and one screen stack, so
 * a DI framework would cost more than it saves.
 */
object ServiceLocator {

    @Volatile
    private var graph: Graph? = null

    class Graph(context: Context) {
        val secureStore = SecureTokenStore(context)
        val authRepository = AuthRepository(secureStore)
        val taskStore = TaskStore(secureStore)
        val api = GitHubClients.createApi { secureStore.token }
        val taskRepository = CodexTaskRepository(api, taskStore)
    }

    fun graph(context: Context): Graph =
        graph ?: synchronized(this) {
            graph ?: Graph(context.applicationContext).also { graph = it }
        }
}
