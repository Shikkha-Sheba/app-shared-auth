import Foundation
import Security

/// Both apps must:
///  1. Be in the same Apple Developer team.
///  2. Enable "Keychain Sharing" capability in Xcode (adds Runner.entitlements).
///  3. Use the SAME keychain-access-group string, e.g. "$(AppIdentifierPrefix)com.ekkademy.shared"
/// Once that's true, both apps read/write the exact same keychain item.
class KeychainHelper {

    static let shared = KeychainHelper()
    private init() {}

    // Must match the group in both apps' Runner.entitlements (without the team-id prefix).
    private let accessGroup = "com.ekkademy.shared"
    private let account = "ekkademy_shared_auth_tokens"
    private let service = "com.ekkademy.shared_auth"

    func save(accessToken: String, refreshToken: String, expiresAt: Double?) {
        var payload: [String: Any] = [
            "accessToken": accessToken,
            "refreshToken": refreshToken
        ]
        if let expiresAt = expiresAt { payload["expiresAt"] = expiresAt }

        guard let data = try? JSONSerialization.data(withJSONObject: payload) else { return }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrService as String: service,
            kSecAttrAccessGroup as String: accessGroup
        ]

        SecItemDelete(query as CFDictionary) // overwrite semantics

        var attributes = query
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock

        SecItemAdd(attributes as CFDictionary, nil)
    }

    func read() -> [String: Any]? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrService as String: service,
            kSecAttrAccessGroup as String: accessGroup,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrService as String: service,
            kSecAttrAccessGroup as String: accessGroup
        ]
        SecItemDelete(query as CFDictionary)
    }
}
