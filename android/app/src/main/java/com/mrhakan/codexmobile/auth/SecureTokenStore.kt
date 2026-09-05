package com.mrhakan.codexmobile.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mrhakan.codexmobile.data.TaskHistoryPersistence

/**
 * Keystore-backed storage for the GitHub token and the settings that go with
 * it. The token is only ever read into memory to build an `Authorization`
 * header; it is never written to logs, plain preferences, or backups (see
 * `data_extraction_rules.xml`).
 */
class SecureTokenStore(context: Context) : TaskHistoryPersistence {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }.apply()
        }

    var login: String?
        get() = prefs.getString(KEY_LOGIN, null)
        set(value) = prefs.edit().putString(KEY_LOGIN, value).apply()

    /** Repository that hosts `codex-cloud-agent.yml`. */
    var controllerRepo: String
        get() = prefs.getString(KEY_CONTROLLER_REPO, DEFAULT_CONTROLLER_REPO)
            ?: DEFAULT_CONTROLLER_REPO
        set(value) = prefs.edit().putString(KEY_CONTROLLER_REPO, value).apply()

    /** Branch of the controller repo the workflow file is read from. */
    var workflowRef: String
        get() = prefs.getString(KEY_WORKFLOW_REF, DEFAULT_WORKFLOW_REF) ?: DEFAULT_WORKFLOW_REF
        set(value) = prefs.edit().putString(KEY_WORKFLOW_REF, value).apply()

    /** Raw JSON for the local task history; see [com.mrhakan.codexmobile.data.TaskStore]. */
    override var taskHistoryJson: String?
        get() = prefs.getString(KEY_TASK_HISTORY, null)
        set(value) = prefs.edit().putString(KEY_TASK_HISTORY, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "codex_mobile_secure"
        private const val KEY_TOKEN = "github_token"
        private const val KEY_LOGIN = "github_login"
        private const val KEY_CONTROLLER_REPO = "controller_repo"
        private const val KEY_WORKFLOW_REF = "workflow_ref"
        private const val KEY_TASK_HISTORY = "task_history"

        const val DEFAULT_CONTROLLER_REPO = "MrHakan/codex-mobile"
        const val DEFAULT_WORKFLOW_REF = "main"
    }
}
