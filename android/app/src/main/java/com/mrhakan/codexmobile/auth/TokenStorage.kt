package com.mrhakan.codexmobile.auth

/**
 * Storage the auth layer needs. Implemented on-device by [SecureTokenStore];
 * split out so the flow can be exercised without Android's Keystore.
 */
interface TokenStorage {
    var authState: AuthState?
    var lastEnvironmentId: String?
    fun clear()
}
