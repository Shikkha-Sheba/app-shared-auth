library shared_auth;

import 'models/auth_tokens.dart';
import 'shared_auth_method_channel.dart';

export 'models/auth_tokens.dart';

class SharedAuth {
  static final _impl = SharedAuthMethodChannel.instance;

  /// App B calls this once at startup, e.g.:
  /// `SharedAuth.configurePeer('com.ekkademy.auth')`
  /// (App A's Android applicationId). Not needed on App A itself.
  static Future<void> configurePeer(String authAppApplicationId) =>
      _impl.configurePeer(authAppApplicationId);

  /// App A calls this right after login succeeds.
  static Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
    DateTime? expiresAt,
  }) {
    return _impl.saveTokens(AuthTokens(
      accessToken: accessToken,
      refreshToken: refreshToken,
      expiresAtEpochMs: expiresAt?.millisecondsSinceEpoch,
    ));
  }

  /// Either app calls this to read the currently stored tokens.
  static Future<AuthTokens?> getTokens() => _impl.getTokens();

  /// App A calls this on logout. App B will then see no tokens.
  static Future<void> clearTokens() => _impl.clearTokens();

  /// Convenience check, treats an expired token as "logged out".
  static Future<bool> isLoggedIn() => _impl.isLoggedIn();
}
