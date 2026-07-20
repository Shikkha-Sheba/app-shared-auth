package com.ekkademy.shared_auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted-at-rest local storage, private to whichever app process calls it
 * (in practice: App A, right after login). App B never touches this class
 * directly — it goes through SharedAuthProvider instead.
 *
 * Requires: implementation "androidx.security:security-crypto:1.1.0-alpha06"
 * in the plugin's android/build.gradle.
 */
class SecureStorage(context: Context) {

    companion object {
        private const val PREFS_NAME = "shared_auth_secure_prefs"
        const val KEY_ACCESS_TOKEN = "accessToken"
        const val KEY_REFRESH_TOKEN = "refreshToken"
        const val KEY_EXPIRES_AT = "expiresAt"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(accessToken: String, refreshToken: String, expiresAt: Long?) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply {
                if (expiresAt != null) putLong(KEY_EXPIRES_AT, expiresAt) else remove(KEY_EXPIRES_AT)
            }
            .apply()
    }

    fun read(): Map<String, Any?>? {
        val access = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        val expiresAt = if (prefs.contains(KEY_EXPIRES_AT)) prefs.getLong(KEY_EXPIRES_AT, 0L) else null
        return mapOf(
            KEY_ACCESS_TOKEN to access,
            KEY_REFRESH_TOKEN to refresh,
            KEY_EXPIRES_AT to expiresAt
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
