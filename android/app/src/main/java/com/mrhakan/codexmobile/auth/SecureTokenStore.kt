@file:Suppress("DEPRECATION")

package com.mrhakan.codexmobile.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json

/**
 * Keystore-backed storage for the ChatGPT OAuth tokens. Tokens are read into
 * memory only to build request headers; they are never logged and are excluded
 * from cloud backup and device transfer (see `data_extraction_rules.xml`).
 *
 * `EncryptedSharedPreferences` is deprecated in security-crypto 1.1.0 without a
 * drop-in replacement; it still does what it says (AES-GCM under a Keystore
 * master key), so the deprecation is suppressed here rather than hand-rolling
 * the same thing over DataStore.
 */
class SecureTokenStore(context: Context) : TokenStorage {

    private val json = Json { ignoreUnknownKeys = true }

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

    override var authState: AuthState?
        get() = prefs.getString(KEY_AUTH, null)
            ?.let { runCatching { json.decodeFromString<AuthState>(it) }.getOrNull() }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_AUTH)
                } else {
                    putString(KEY_AUTH, json.encodeToString(AuthState.serializer(), value))
                }
            }.apply()
        }

    /** Last environment the user submitted a task to, so the composer can reuse it. */
    override var lastEnvironmentId: String?
        get() = prefs.getString(KEY_LAST_ENV, null)
        set(value) = prefs.edit().putString(KEY_LAST_ENV, value).apply()

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "codex_mobile_secure"
        const val KEY_AUTH = "chatgpt_auth"
        const val KEY_LAST_ENV = "last_environment_id"
    }
}
