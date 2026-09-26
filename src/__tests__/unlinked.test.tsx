/**
 * Without the native module (not linked, no rebuild, Expo Go), importing the
 * package must still work and every call must fail with an actionable message.
 */
const loadUnlinked = (os: 'ios' | 'android'): typeof import('../index') => {
  jest.resetModules();
  jest.doMock('react-native', () => ({
    NativeModules: {},
    NativeEventEmitter: jest
      .fn()
      .mockImplementation(() => ({ addListener: jest.fn() })),
    Platform: {
      select: (spec: Record<string, string>) => spec[os] ?? spec.default,
    },
  }));

  return require('../index');
};

const messageOf = (fn: () => unknown): string => {
  try {
    fn();
  } catch (e) {
    return (e as Error).message;
  }
  return '';
};

describe('when the native module is not linked', () => {
  it('imports without throwing', () => {
    expect(() => loadUnlinked('android')).not.toThrow();
  });

  it('throws a linking error that mentions pod install on iOS', () => {
    const api = loadUnlinked('ios');
    expect(messageOf(() => api.initBluesnap({ token: 'tok' }))).toMatch(
      /doesn't seem to be linked[\s\S]*pod install/
    );
  });

  it('throws a linking error without the pod install hint on Android', () => {
    const message = messageOf(() => loadUnlinked('android').getSdkVersion());
    expect(message).toMatch(/doesn't seem to be linked/);
    expect(message).not.toMatch(/pod install/);
  });
});
