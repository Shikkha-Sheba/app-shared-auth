import 'package:flutter/services.dart';
import 'models/auth_tokens.dart';

class SharedAuthMethodChannel {
  SharedAuthMethodChannel._();
  static final SharedAuthMethodChannel instance = SharedAuthMethodChannel._();

  final MethodChannel _channel = const MethodChannel('com.ekkademy.shared_auth');

  /// App B calls this ONCE (e.g. in main()) with App A's Android applicationId,
  /// so it knows which ContentProvider to query. No-op on iOS (uses Keychain groups instead).
  Future<void> configurePeer(String authAppApplicationId) async {
    await _channel.invokeMethod('configurePeer', {'applicationId': authAppApplicationId});
  }

  /// Called ONLY by the app that owns auth (App A) after a successful login.
  Future<void> saveTokens(AuthTokens tokens) async {
    await _channel.invokeMethod('saveTokens', tokens.toMap());
  }

  /// Called by either app. On App A this reads local secure storage directly.
  /// On App B (Android) this queries App A's ContentProvider;
  /// on iOS both apps read the same shared Keychain access group.
  Future<AuthTokens?> getTokens() async {
    final result = await _channel.invokeMethod('getTokens');
    if (result == null) return null;
    return AuthTokens.fromMap(Map<dynamic, dynamic>.from(result as Map));
  }

  /// Called by App A on logout. Clears storage so App B sees the user as logged out too.
  Future<void> clearTokens() async {
    await _channel.invokeMethod('clearTokens');
  }

  Future<bool> isLoggedIn() async {
    final tokens = await getTokens();
    return tokens != null && !tokens.isExpired;
  }
}
