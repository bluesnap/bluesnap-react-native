import { Platform, NativeModules } from 'react-native';
export const DEVICE_LOCALE = (
  Platform.OS === 'ios'
    ? NativeModules.SettingsManager.settings.AppleLocale ||
      NativeModules.SettingsManager.settings.AppleLanguages[0]
    : NativeModules.I18nManager.localeIdentifier
).replace('_', '-');

export const CHANNEL = Platform.select({
  ios: () => 'iOS',
  android: () => 'Android',
})();

export const DEFAULT_CONFIGURATION = {};

// Sandbox environmet
export const ENVIRONMENT = {};
