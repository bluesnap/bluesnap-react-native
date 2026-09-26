/**
 * Bridge contract: the JS layer, the iOS module and the Android module must
 * agree on module name, method names, method shapes, events and native SDK
 * versions. These drift silently: a missing native method only fails at
 * runtime on one platform, and a wrong SDK pin only fails in the app build.
 * The native sources are parsed as text, so this runs without Xcode or Gradle.
 */
import { readFileSync } from 'fs';
import { join } from 'path';

const root = join(__dirname, '..', '..');
const read = (path: string) => readFileSync(join(root, path), 'utf8');

const MODULE = 'BluesnapSdkReactNative';
const jsSource = read('src/index.tsx');
const typesSource = read('src/types.ts');
const iosExterns = read('ios/BluesnapSdkReactNative.m');
const iosSwift = read('ios/BluesnapSdkReactNative.swift');
const androidModule = read(
  'android/src/main/java/com/bluesnapsdkreactnative/BluesnapSdkReactNativeModule.java'
);
const podspec = read('bluesnap-sdk-react-native.podspec');
const gradleProperties = read('android/gradle.properties');
const readme = read('README.md');

const matchAll = (source: string, re: RegExp) =>
  [...source.matchAll(re)].map((m) => m[1] as string);
const one = (source: string, re: RegExp, what: string): string => {
  const m = source.match(re);
  if (!m?.[1]) throw new Error(`could not find ${what}`);
  return m[1];
};

// JS: methods called on the native module, and whether the JS wrapper returns a Promise
const jsCalls = new Set(matchAll(jsSource, /NativeBluesnap\.(\w+)\(/g));
const jsReturnsPromise = new Map(
  [...jsSource.matchAll(/export function (\w+)\([^)]*\)\s*:\s*([^{]+)\{/g)].map(
    (m) => [m[1] as string, /Promise</.test(m[2] as string)]
  )
);
const jsNativeCallOf = (exportName: string) =>
  one(
    jsSource,
    new RegExp(
      `export function ${exportName}\\b[\\s\\S]*?NativeBluesnap\\.(\\w+)\\(`
    ),
    exportName
  );

// iOS: RCT_EXTERN_METHOD(name:... ) blocks, with whether they take a resolve block
const iosMethods = new Map(
  [
    ...iosExterns.matchAll(
      /RCT_EXTERN_METHOD\((\w+)([\s\S]*?)\)\s*(?=RCT_EXTERN_METHOD|@end)/g
    ),
  ].map((m) => [m[1] as string, /RCTPromiseResolveBlock/.test(m[2] as string)])
);

// Android: @ReactMethod public void name(params)
const androidMethods = new Map(
  [
    ...androidModule.matchAll(/@ReactMethod\s+public void (\w+)\(([^)]*)\)/g),
  ].map((m) => [m[1] as string, /\bPromise\b/.test(m[2] as string)])
);

// RN event-emitter plumbing that JS never calls directly
const EMITTER_PLUMBING = new Set(['addListener', 'removeListeners']);

describe('module name', () => {
  it('is the same in JS, iOS and Android', () => {
    expect(jsSource).toMatch(new RegExp(`NativeModules\\.${MODULE}\\b`));
    expect(one(iosExterns, /RCT_EXTERN_MODULE\((\w+),/, 'iOS module')).toBe(
      MODULE
    );
    expect(one(androidModule, /String NAME = "(\w+)"/, 'Android NAME')).toBe(
      MODULE
    );
  });
});

describe('methods', () => {
  it('finds the native calls in the JS layer', () => {
    expect(jsCalls.size).toBeGreaterThanOrEqual(14);
  });

  it.each([...jsCalls])('%s is exported by the iOS module', (name) => {
    expect(iosMethods.has(name)).toBe(true);
  });

  it.each([...jsCalls])('%s is exported by the Android module', (name) => {
    expect(androidMethods.has(name)).toBe(true);
  });

  it('neither platform exports methods the JS layer never calls', () => {
    const unused = (methods: Map<string, boolean>) =>
      [...methods.keys()].filter(
        (m) => !jsCalls.has(m) && !EMITTER_PLUMBING.has(m)
      );
    expect(unused(iosMethods)).toEqual([]);
    expect(unused(androidMethods)).toEqual([]);
  });

  it.each(
    [...jsReturnsPromise.keys()].filter((e) => e !== 'addBluesnapListener')
  )(
    '%s: iOS, Android and the JS signature agree on returning a Promise',
    (exportName) => {
      const native = jsNativeCallOf(exportName);
      const jsPromise = jsReturnsPromise.get(exportName);
      expect({
        native,
        ios: iosMethods.get(native),
        android: androidMethods.get(native),
      }).toEqual({
        native,
        ios: jsPromise,
        android: jsPromise,
      });
    }
  );

  it('Android exports the listener plumbing NativeEventEmitter needs', () => {
    expect(androidMethods.has('addListener')).toBe(true);
    expect(androidMethods.has('removeListeners')).toBe(true);
  });
});

describe('events', () => {
  const jsEvents = matchAll(
    one(typesSource, /BluesnapEventMap = \{([\s\S]*?)\};/, 'BluesnapEventMap'),
    /(\w+)\s*:/g
  ).sort();

  it('iOS supportedEvents() matches BluesnapEventMap', () => {
    const supported = one(
      iosSwift,
      /supportedEvents\(\)[\s\S]*?return \[([^\]]*)\]/,
      'supportedEvents'
    );
    expect(matchAll(supported, /"(\w+)"/g).sort()).toEqual(jsEvents);
  });

  it('iOS only sends declared events', () => {
    expect(
      new Set(matchAll(iosSwift, /sendEvent\(\s*withName:\s*"(\w+)"/g))
    ).toEqual(new Set(jsEvents));
  });

  it('Android only sends declared events', () => {
    expect(new Set(matchAll(androidModule, /sendEvent\("(\w+)"/g))).toEqual(
      new Set(jsEvents)
    );
  });
});

describe('native SDK versions', () => {
  const iosPin = one(
    podspec,
    /s\.dependency\s+"BluesnapSDK",\s*"([^"]+)"/,
    'podspec BluesnapSDK pin'
  );
  const androidPin = one(
    gradleProperties,
    /^BluesnapSdkReactNative_bluesnapAndroidVersion=(\S+)$/m,
    'Android pin'
  );
  const iosReported = one(
    iosSwift,
    /func getSdkVersion\([\s\S]*?resolve\("([^"]+)"\)/,
    'iOS getSdkVersion'
  );
  const androidReported = one(
    androidModule,
    /void getSdkVersion\([\s\S]*?promise\.resolve\("([^"]+)"\)/,
    'Android getSdkVersion'
  );

  it('iOS pins an exact BluesnapSDK version', () => {
    expect(iosPin).toMatch(/^\d+\.\d+\.\d+$/);
  });

  it('Android pins an exact public bluesnap-android release tag (vX.Y.Z, as tagged)', () => {
    expect(androidPin).toMatch(/^v\d+\.\d+\.\d+$/);
  });

  it('getSdkVersion reports the pinned version on each platform', () => {
    expect(iosReported).toBe(iosPin);
    expect(androidReported).toBe(androidPin.replace(/^v/, ''));
  });

  it('the README table lists the pinned versions', () => {
    expect(readme).toMatch(
      new RegExp(`\\| iOS \\|[^\\n]*\\| ${iosPin.replace(/\./g, '\\.')} \\|`)
    );
    expect(readme).toMatch(
      new RegExp(
        `\\| Android \\|[^\\n]*\\| ${androidReported.replace(/\./g, '\\.')} \\|`
      )
    );
  });
});
