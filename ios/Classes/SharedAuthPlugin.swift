import Flutter
import UIKit

public class SharedAuthPlugin: NSObject, FlutterPlugin {

    /// Set when this app is opened via scheme://open?reason=... .
    /// Consumed (read once, then cleared) via SharedAuth.consumeLaunchReason().
    /// AppDelegate must forward incoming URLs here — see handleIncomingURL below.
    private static var pendingLaunchReason: String?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "com.ekkademy.shared_auth", binaryMessenger: registrar.messenger())
        let instance = SharedAuthPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    /// Call this from AppDelegate.application(_:open:options:) — see the
    /// snippet in AppDelegate_snippet.swift in each example app folder.
    @discardableResult
    public static func handleIncomingURL(_ url: URL) -> Bool {
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        let reason = components?.queryItems?.first(where: { $0.name == "reason" })?.value
        if let reason = reason {
            pendingLaunchReason = reason
        }
        return true
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "configurePeer":
            // No-op on iOS: both apps already point at the same keychain
            // access group, so there's nothing to configure per-app.
            result(nil)

        case "saveTokens":
            guard let args = call.arguments as? [String: Any],
                  let access = args["accessToken"] as? String,
                  let refresh = args["refreshToken"] as? String else {
                result(FlutterError(code: "BAD_ARGS", message: "Missing tokens", details: nil))
                return
            }
            let expiresAt = args["expiresAt"] as? Double
            KeychainHelper.shared.save(accessToken: access, refreshToken: refresh, expiresAt: expiresAt)
            result(nil)

        case "getTokens":
            if let payload = KeychainHelper.shared.read() {
                result(payload)
            } else {
                result(nil)
            }

        case "clearTokens":
            KeychainHelper.shared.clear()
            result(nil)

        case "launchApp":
            guard let args = call.arguments as? [String: Any],
                  let scheme = args["iosUrlScheme"] as? String else {
                result(false)
                return
            }
            let reason = args["reason"] as? String
            let urlString = reason != nil ? "\(scheme)://open?reason=\(reason!)" : "\(scheme)://open"
            guard let url = URL(string: urlString) else {
                result(false)
                return
            }
            DispatchQueue.main.async {
                if UIApplication.shared.canOpenURL(url) {
                    UIApplication.shared.open(url, options: [:]) { success in
                        result(success)
                    }
                } else {
                    // Peer app not installed, or its scheme isn't listed in
                    // THIS app's Info.plist under LSApplicationQueriesSchemes.
                    result(false)
                }
            }

        case "consumeLaunchReason":
            let reason = SharedAuthPlugin.pendingLaunchReason
            SharedAuthPlugin.pendingLaunchReason = nil
            result(reason)

        default:
            result(FlutterMethodNotImplemented)
        }
    }
}
