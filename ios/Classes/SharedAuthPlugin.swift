import Flutter
import UIKit

public class SharedAuthPlugin: NSObject, FlutterPlugin {

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "com.ekkademy.shared_auth", binaryMessenger: registrar.messenger())
        let instance = SharedAuthPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
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

        default:
            result(FlutterMethodNotImplemented)
        }
    }
}
