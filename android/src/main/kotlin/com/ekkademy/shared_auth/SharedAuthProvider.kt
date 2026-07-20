package com.ekkademy.shared_auth

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Lives inside App A's process. App B never runs this code; it just
 * queries/inserts/deletes through Android's cross-process ContentResolver,
 * which the OS enforces against the signature-level permission declared
 * in AndroidManifest.xml. Two apps signed with DIFFERENT keys get a
 * SecurityException here — that's the whole security model.
 */
class SharedAuthProvider : ContentProvider() {

    private lateinit var storage: SecureStorage
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
    private val CODE_TOKENS = 1

    override fun onCreate(): Boolean {
        storage = SecureStorage(context!!)
        uriMatcher.addURI("${context!!.packageName}.shared_auth.provider", "tokens", CODE_TOKENS)
        return true
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        if (uriMatcher.match(uri) != CODE_TOKENS) return null
        val data = storage.read() ?: return MatrixCursor(arrayOf("accessToken", "refreshToken", "expiresAt")).apply {
            // empty cursor = "no tokens / logged out"
        }
        val cursor = MatrixCursor(arrayOf("accessToken", "refreshToken", "expiresAt"))
        cursor.addRow(arrayOf(data["accessToken"], data["refreshToken"], data["expiresAt"]))
        return cursor
    }

    /** App A writes new tokens through this (called by SharedAuthPlugin on the same device/process). */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (uriMatcher.match(uri) != CODE_TOKENS || values == null) return null
        val access = values.getAsString("accessToken") ?: return null
        val refresh = values.getAsString("refreshToken") ?: return null
        val expiresAt = values.getAsLong("expiresAt")
        storage.save(access, refresh, expiresAt)
        context?.contentResolver?.notifyChange(uri, null)
        return uri
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        if (uriMatcher.match(uri) != CODE_TOKENS) return 0
        storage.clear()
        context?.contentResolver?.notifyChange(uri, null)
        return 1
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/vnd.ekkademy.token"
}
