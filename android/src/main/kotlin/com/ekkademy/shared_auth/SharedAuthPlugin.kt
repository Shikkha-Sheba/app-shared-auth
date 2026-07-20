package com.ekkademy.shared_auth

import android.content.ContentValues
import android.content.Context
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class SharedAuthPlugin : FlutterPlugin, MethodCallHandler {

    private lateinit var channel: MethodChannel
    private lateinit var appContext: Context

    /**
     * Set by App B via SharedAuth.configurePeer("com.ekkademy.auth")
     * i.e. App A's applicationId. App A never needs to call this —
     * it just reads/writes its own local provider.
     */
    private var peerApplicationId: String? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        appContext = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "com.ekkademy.shared_auth")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun authorityUri(): android.net.Uri {
        val authorityApp = peerApplicationId ?: appContext.packageName
        return android.net.Uri.parse("content://$authorityApp.shared_auth.provider/tokens")
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "configurePeer" -> {
                peerApplicationId = call.argument<String>("applicationId")
                result.success(null)
            }
            "saveTokens" -> {
                val access = call.argument<String>("accessToken")
                val refresh = call.argument<String>("refreshToken")
                val expiresAt = call.argument<Number>("expiresAt")?.toLong()
                val values = ContentValues().apply {
                    put("accessToken", access)
                    put("refreshToken", refresh)
                    if (expiresAt != null) put("expiresAt", expiresAt)
                }
                appContext.contentResolver.insert(authorityUri(), values)
                result.success(null)
            }
            "getTokens" -> {
                val cursor = appContext.contentResolver.query(authorityUri(), null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst() && it.getString(it.getColumnIndexOrThrow("accessToken")) != null) {
                        val map = mapOf(
                            "accessToken" to it.getString(it.getColumnIndexOrThrow("accessToken")),
                            "refreshToken" to it.getString(it.getColumnIndexOrThrow("refreshToken")),
                            "expiresAt" to it.getLong(it.getColumnIndexOrThrow("expiresAt"))
                        )
                        result.success(map)
                        return
                    }
                }
                result.success(null)
            }
            "clearTokens" -> {
                appContext.contentResolver.delete(authorityUri(), null, null)
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }
}
