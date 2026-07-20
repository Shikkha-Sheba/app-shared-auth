#include "include/shared_auth/shared_auth_plugin_c_api.h"

#include <flutter/plugin_registrar_windows.h>

#include "shared_auth_plugin.h"

void SharedAuthPluginCApiRegisterWithRegistrar(
    FlutterDesktopPluginRegistrarRef registrar) {
  shared_auth::SharedAuthPlugin::RegisterWithRegistrar(
      flutter::PluginRegistrarManager::GetInstance()
          ->GetRegistrar<flutter::PluginRegistrarWindows>(registrar));
}
