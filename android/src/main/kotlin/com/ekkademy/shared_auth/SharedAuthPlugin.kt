package com.ekkademy.shared_auth

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
     * Set when this app is opened by the peer app with a "reason" extra
     * (e.g. "auth_handoff"). Consumed (read once, then cleared) via
     * SharedAuth.consumeLaunchReason(). Null on a normal icon-tap launch.
     *
     * IMPORTANT: we deliberately use a plain launcher Intent + putExtra()
     * here, NOT an ACTION_VIEW/custom-scheme Intent. Flutter's engine
     * auto-forwards ACTION_VIEW intents with a data URI to the framework
     * as a navigation route (for go_router/Navigator 2.0 deep linking),
     * which causes a "no routes for location" crash since this URI was
     * never meant to be a real app route. Using extras avoids that
     * entirely — Flutter has no opinion about Intent extras.
     */
    private var pendingLaunchReason: String? = null

    private companion object {
        const val EXTRA_REASON = "shared_auth_reason"
    }

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
            captureReasonFromIntent(intent) // already-running app re-opened by peer
            false
        }
    }

    override fun onDetachedFromActivityForConfigChanges() { activityBinding = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }
    override fun onDetachedFromActivity() { activityBinding = null }

    private fun captureReasonFromIntent(intent: Intent?) {
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: return
        pendingLaunchReason = reason
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
                // Plain launcher intent, NOT ACTION_VIEW — see comment on
                // pendingLaunchReason above for why.
                val intent = appContext.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    // NEW_TASK alone often fails to bring an already-running
                    // peer task to the foreground (you just see the home
                    // screen / current app "closes" instead). REORDER_TO_FRONT
                    // forces Android to actually surface the existing task.
                    intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                    if (reason != null) intent.putExtra(EXTRA_REASON, reason)
                    appContext.startActivity(intent)
                    result.success(true)
                } else {
                    // Not installed, or blocked by Android 11+ package visibility
                    // (add the peer's package to <queries> in AndroidManifest.xml).
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
}
