package com.ekkademy.shared_auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.KeyStoreException
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

/**
 * Encrypted-at-rest local storage, private to whichever app process calls it
 * (in practice: App A, right after login). App B never touches this class
 * directly — it goes through SharedAuthProvider instead.
 *
 * Requires: implementation "androidx.security:security-crypto:1.1.0-alpha06"
 * in the plugin's android/build.gradle.
 *
 * IMPORTANT: the AES key backing this file lives in the Android Keystore and
 * is hardware-bound — it is NEVER included in backup/restore, even though the
 * encrypted prefs XML itself may be. If the OS restores this file onto a
 * device/keystore that doesn't have the original key (backup-restore, OS
 * upgrade invalidating the key, etc.) every read/write throws
 * KeyStoreException("Signature/MAC verification failed") and the file is
 * permanently unreadable. There is no way to recover the old data in that
 * case — the only fix is to wipe the file + key and start fresh, which is
 * what getPrefs() below does automatically, once, on first failure.
 */
class SecureStorage(private val context: Context) {

    companion object {
        private const val TAG = "SecureStorage"
        private const val PREFS_NAME = "shared_auth_secure_prefs"
        const val KEY_ACCESS_TOKEN = "accessToken"
        const val KEY_REFRESH_TOKEN = "refreshToken"
        const val KEY_EXPIRES_AT = "expiresAt"
    }

    // Not `by lazy` on purpose — lazy can't be reset, and we need to rebuild
    // this after wiping a corrupted file.
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun buildPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Deletes the corrupted prefs file so a fresh key + file can be created. */
    private fun resetCorruptedPrefs() {
        Log.w(TAG, "Encrypted prefs unreadable, wiping and recreating: $PREFS_NAME")
        context.deleteSharedPreferences(PREFS_NAME)
        cachedPrefs = null
    }

    /** Returns cached prefs, or builds it — self-healing once if the key is invalidated. */
    private fun getPrefs(): SharedPreferences {
        cachedPrefs?.let { return it }
        synchronized(this) {
            cachedPrefs?.let { return it }
            return try {
                buildPrefs().also { cachedPrefs = it }
            } catch (e: Exception) {
                if (isKeystoreCorruption(e)) {
                    resetCorruptedPrefs()
                    // Retry once with a brand-new master key + empty file.
                    buildPrefs().also { cachedPrefs = it }
                } else {
                    throw e
                }
            }
        }
    }

    private fun isKeystoreCorruption(e: Throwable?): Boolean {
        var cause = e
        while (cause != null) {
            if (cause is KeyPermanentlyInvalidatedException ||
                cause is AEADBadTagException ||
                cause is KeyStoreException ||
                (cause is GeneralSecurityException) ||
                cause.javaClass.name.contains("KeyStoreException")
            ) return true
            cause = cause.cause
        }
        return false
    }

    fun save(accessToken: String, refreshToken: String, expiresAt: Long?) {
        runSelfHealing {
            getPrefs().edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply {
                    if (expiresAt != null) putLong(KEY_EXPIRES_AT, expiresAt) else remove(KEY_EXPIRES_AT)
                }
                .apply()
        }
    }

    fun read(): Map<String, Any?>? = runSelfHealing {
        val prefs = getPrefs()
        val access = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return@runSelfHealing null
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return@runSelfHealing null
        val expiresAt = if (prefs.contains(KEY_EXPIRES_AT)) prefs.getLong(KEY_EXPIRES_AT, 0L) else null
        mapOf(
            KEY_ACCESS_TOKEN to access,
            KEY_REFRESH_TOKEN to refresh,
            KEY_EXPIRES_AT to expiresAt
        )
    }

    fun clear() {
        runSelfHealing { getPrefs().edit().clear().apply() }
    }

    /**
     * Runs [block] against the (possibly rebuilt) prefs. If [block] itself
     * throws a keystore corruption error mid-read (e.g. AEADBadTagException
     * while decrypting a specific value, not just at creation time), wipe
     * and retry exactly once before giving up.
     */
    private fun <T> runSelfHealing(block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            if (isKeystoreCorruption(e)) {
                resetCorruptedPrefs()
                block()
            } else {
                throw e
            }
        }
    }
}