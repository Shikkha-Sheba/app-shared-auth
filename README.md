# shared_auth

Share a login session (access + refresh token) between two separate Flutter
apps on the same device — App A owns auth, App B just reads it.

## How it works

| Platform | Mechanism |
|---|---|
| Android | App A stores tokens in `EncryptedSharedPreferences`. A `ContentProvider`, protected by a **signature-level custom permission**, exposes them cross-process. App B queries it through `ContentResolver`. Only apps signed with the same key can be granted the permission. |
| iOS | Both apps enable **Keychain Sharing** with the same access group. They literally read/write the same Keychain item — no IPC needed. |

## Setup checklist

### 1. Both apps depend on this package
```yaml
dependencies:
  shared_auth:
    path: ../shared_auth   # or git: url
```

### 2. Android — signing
Both APKs **must** be signed with the same key (same debug keystore locally;
same release keystore, or the same Play App Signing upload key, in production).
Different keys → the OS silently refuses the permission and App B gets `null`.

### 3. Android — manifests
In **both** App A's and App B's `android/app/src/main/AndroidManifest.xml`:
```xml
<uses-permission android:name="com.ekkademy.shared_auth.permission.ACCESS_TOKEN" />
```

### 4. Android — App B must point at App A
Somewhere early in App B's (`com.ekkademy.student`) `main()`:
```dart
await SharedAuth.configurePeer('com.ekkademy.edroom'); // App A's applicationId
```
App A (`com.ekkademy.edroom`) never calls `configurePeer` — it always uses its own local provider.

### 5. iOS — Keychain Sharing
In Xcode, for **both** apps' Runner targets: Signing & Capabilities → +
Capability → Keychain Sharing → add group `com.ekkademy.shared`. Both apps
must belong to the same Apple Developer Team. See
`ios/Runner.entitlements.example`.

### 6. Wire up the calls
- App A, after a successful login: `SharedAuth.saveTokens(...)`
- App A, on logout: `SharedAuth.clearTokens()`
- App B, on startup: `SharedAuth.getTokens()` / `SharedAuth.isLoggedIn()`

See `example_appA/lib/login_example.dart` and
`example_appB/lib/read_token_example.dart`.

## Notes / gotchas

- **Debug builds**: Flutter's default debug signing key differs per machine
  unless you share a keystore — the Android path won't work between a
  debug App A and debug App B built on different machines/CI runners unless
  they use the same debug keystore. Fine in release since you control the
  keystore.
- **Token refresh**: whichever app is running foreground and refreshes the
  access token should call `saveTokens` again to overwrite storage, so the
  other app picks up the new token next time it reads.
- **Multi-user / logout sync**: `clearTokens()` from App A is the only
  logout signal App B gets — App B should re-check `getTokens()` (e.g. on
  resume) rather than caching the session in memory indefinitely.
