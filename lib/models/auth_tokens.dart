class AuthTokens {
  final String accessToken;
  final String refreshToken;
  final int? expiresAtEpochMs;

  const AuthTokens({
    required this.accessToken,
    required this.refreshToken,
    this.expiresAtEpochMs,
  });

  bool get isExpired {
    if (expiresAtEpochMs == null) return false;
    return DateTime.now().millisecondsSinceEpoch >= expiresAtEpochMs!;
  }

  Map<String, dynamic> toMap() => {
        'accessToken': accessToken,
        'refreshToken': refreshToken,
        'expiresAt': expiresAtEpochMs,
      };

  factory AuthTokens.fromMap(Map<dynamic, dynamic> map) => AuthTokens(
        accessToken: map['accessToken'] as String,
        refreshToken: map['refreshToken'] as String,
        expiresAtEpochMs: map['expiresAt'] as int?,
      );

  @override
  String toString() =>
      'AuthTokens(accessToken: ${accessToken.substring(0, accessToken.length > 8 ? 8 : accessToken.length)}..., expired: $isExpired)';
}
