Pod::Spec.new do |s|
  s.name             = 'shared_auth'
  s.version          = '0.0.1'
  s.summary          = 'Shares auth tokens between two apps via Keychain (iOS) / ContentProvider (Android).'
  s.source_files     = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform         = :ios, '12.0'
end
