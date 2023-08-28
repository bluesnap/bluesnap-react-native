import { NativeEventEmitter, NativeModules } from 'react-native';

const asyncFn =
  <T>(response: T) =>
  () =>
    jest.fn(() => {
      return Promise.resolve(response);
    });
const syncFn =
  <T>(response: T) =>
  () =>
    jest.fn(() => response);

const BluesnapSdkReactNative: any = {};

BluesnapSdkReactNative.initBluesnap = asyncFn('unknown-string')();
BluesnapSdkReactNative.setSDKRequest = asyncFn(undefined)();
BluesnapSdkReactNative.finalizeToken = syncFn(() => {})();
BluesnapSdkReactNative.showCheckout = asyncFn({})();

BluesnapSdkReactNative.eventEmitter = new NativeEventEmitter();

NativeModules.BluesnapSdkReactNative = BluesnapSdkReactNative;
jest.mock('react-native/Libraries/EventEmitter/NativeEventEmitter');
jest.mock('./src/internal/nativeInterface', () => ({
  default: BluesnapSdkReactNative,
}));
