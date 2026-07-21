package com.ekkademy.shared_auth

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class SharedAuthPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    private lateinit var channel: MethodChannel
    private lateinit var appContext: Context
    private var activityBinding: ActivityPluginBinding? = null

    /** Set by edroom via SharedAuth.configurePeer("com.ekkademy.student"). */
    private var peerApplicationId: String? = null

    /**
     * Set when this app is opened via a shared_auth deep link
     * (scheme://open?reason=...), e.g. "auth_handoff". Consumed (read once,
     * then cleared) via SharedAuth.consumeLaunchReason(). Null on a normal
     * icon-tap launch.
     */
    private var pendingLaunchReason: String? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        appContext = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "com.ekkademy.shared_auth")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    // --- ActivityAware: needed to read the launching/new Intent ---

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        captureReasonFromIntent(binding.activity.intent) // cold start
        binding.addOnNewIntentListener { intent ->
            captureReasonFromIntent(intent) // already-running app re-opened via deep link
            false
        }
    }

    override fun onDetachedFromActivityForConfigChanges() { activityBinding = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }
    override fun onDetachedFromActivity() { activityBinding = null }

    private fun captureReasonFromIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val reason = data.getQueryParameter("reason")
        if (reason != null) pendingLaunchReason = reason
    }

    private fun authorityUri(): Uri {
        val authorityApp = peerApplicationId ?: appContext.packageName
        return Uri.parse("content://$authorityApp.shared_auth.provider/tokens")
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
            "launchApp" -> {
                val pkg = call.argument<String>("androidPackage")
                val reason = call.argument<String>("reason")
                if (pkg == null) {
                    result.success(false)
                    return
                }
                // Deep-link intent (not getLaunchIntentForPackage) so we can
                // attach ?reason=... and the peer app can read WHY it was opened.
                val scheme = call.argument<String>("androidPackage") // fallback only
                val uriScheme = androidSchemeFor(pkg)
                val uri = if (reason != null) {
                    Uri.parse("$uriScheme://open?reason=$reason")
                } else {
                    Uri.parse("$uriScheme://open")
                }
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkg) // target exactly this app, no chooser
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    appContext.startActivity(intent)
                    result.success(true)
                } catch (e: ActivityNotFoundException) {
                    // Not installed, no matching intent-filter, or blocked by
                    // Android 11+ package visibility (<queries> missing).
                    result.success(false)
                }
            }
            "consumeLaunchReason" -> {
                val reason = pendingLaunchReason
                pendingLaunchReason = null
                result.success(reason)
            }
            else -> result.notImplemented()
        }
    }

    // com.ekkademy.student -> ekkademystudent, com.ekkademy.edroom -> ekkademyedroom
    private fun androidSchemeFor(applicationId: String): String {
        return when {
            applicationId.startsWith("com.ekkademy.student") -> "ekkademystudent"
            applicationId.startsWith("com.ekkademy.edroom") -> "ekkademyedroom"
            else -> applicationId.replace(".", "")
        }
    }
}
