import { getSdkVersion } from '../index';

jest.mock('react-native', () => ({
  NativeModules: {
    BluesnapSdkReactNative: {
      getSdkVersion: jest.fn(() => Promise.resolve('0.0.0-test')),
      addListener: jest.fn(),
      removeListeners: jest.fn(),
    },
  },
  NativeEventEmitter: jest.fn().mockImplementation(() => ({
    addListener: jest.fn(() => ({ remove: jest.fn() })),
  })),
  Platform: { select: jest.fn() },
}));

describe('bluesnap-sdk-react-native', () => {
  it('exposes getSdkVersion', async () => {
    await expect(getSdkVersion()).resolves.toBe('0.0.0-test');
  });
});
