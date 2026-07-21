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

  /// Launches the peer app. androidPackage = peer's applicationId,
  /// iosUrlScheme = peer's custom URL scheme (no "://"). reason is an
  /// optional tag (e.g. "auth_handoff") the peer app can read via
  /// consumeLaunchReason() to know WHY it was opened. Returns false
  /// if the peer app isn't installed.
  Future<bool> launchApp({
    required String androidPackage,
    required String iosUrlScheme,
    String? reason,
  }) async {
    final result = await _channel.invokeMethod<bool>('launchApp', {
      'androidPackage': androidPackage,
      'iosUrlScheme': iosUrlScheme,
      'reason': reason,
    });
    return result ?? false;
  }

  /// Reads (and clears) the reason this app instance was deep-linked open,
  /// if any. Returns null on a normal launch (user tapped the app icon).
  Future<String?> consumeLaunchReason() async {
    return _channel.invokeMethod<String>('consumeLaunchReason');
  }
}

