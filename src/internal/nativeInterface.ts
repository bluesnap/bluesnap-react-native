import { Platform, NativeModules } from 'react-native';

let BluesnapSdkReactNative = NativeModules.BluesnapSdkReactNative;

// @ts-ignore
if (Platform.OS === 'web' || Platform.OS === 'dom') {
  BluesnapSdkReactNative = null;
}

if (!BluesnapSdkReactNative) {
  // Produce an error if we don't have the native module
  if (
    Platform.OS === 'android' ||
    Platform.OS === 'ios' ||
    Platform.OS === 'web' ||
    // @ts-ignore
    Platform.OS === 'dom'
  ) {
    throw new Error(`NativeModule.BluesnapSdkReactNative is null.`);
  }
}

export default BluesnapSdkReactNative;
