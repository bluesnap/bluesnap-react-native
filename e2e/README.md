# Testing the bridge

| Layer | Catches | Where | Runs |
|---|---|---|---|
| JS unit tests | the JS API forwards arguments, results and errors to the native module unchanged; a clear error when the module isn't linked | `src/__tests__/index.test.tsx`, `unlinked.test.tsx` | CI `test`, every PR |
| Bridge contract | JS, iOS and Android disagree on a method, its Promise shape or an event; the SDK pins, `getSdkVersion()` and the README drift apart | `src/__tests__/bridgeContract.test.ts` | CI `test`, every PR |
| Android compile | an unresolvable bluesnap-android pin; a native SDK API change | CI `android` job | every PR |
| Self-test runner | the self-test's own logic | `example/src/__tests__/selfTest.test.ts` | CI `test`, every PR |
| **End-to-end self-test** | a real round trip: JS → native module → BlueSnap native SDK → BlueSnap sandbox → back, on a device | this folder | before a release |

## End-to-end self-test

The example app has a **Bridge self-test** screen (`example/src/SelfTestScreen.tsx`). With a sandbox payment-fields token it runs, and shows PASS or FAIL for each step:

1. `getSdkVersion`: the native SDK version is a real `X.Y.Z`
2. `initBluesnap`: initializes the native SDK with the token
3. `getSupportedCurrencies`: the SDK loaded currency rates from the sandbox
4. `submitTokenizedDetails`: tokenizes the sandbox test card 4111 1111 1111 1111 into the token
5. `applePaySupported` (iOS) or `getShopperConfiguration` (Android)

These steps open no native payment screen, so the run is unattended and behaves the same on both platforms. Each step times out after 30 s, so a native promise that never settles fails the run instead of hanging it. The overall result is shown under `testID="selftest-result"`.

### Run it with Maestro

Prerequisites:

- Sandbox API credentials in `BS_API_USER` and `BS_API_PASSWORD`, the same secrets the native SDK CI uses. `scripts/e2e/get-token.sh` exchanges them for a payment-fields token (`POST /services/2/payment-fields-tokens`).
- The example app installed on a booted iOS simulator or Android emulator (`yarn example ios` / `yarn example android`).
- [Maestro](https://maestro.mobile.dev): `curl -Ls https://get.maestro.mobile.dev | bash`

```sh
scripts/e2e/run-self-test.sh ios       # or: android
```

This fetches a fresh token and runs `e2e/bridge-self-test.yaml`. It writes a JUnit report to `build/e2e/bridge-self-test-<platform>.xml` and a screenshot of the result.

### Run it by hand

Open the example app, tap **Bridge self-test**, paste the output of `scripts/e2e/get-token.sh`, and tap **Run bridge self-test**. Every step should say PASS.

## Known blockers (checked 2026-09-25)

- **Android example app.** `compileSdkVersion = 35` with the example's AGP 7.4 fails at resource linking (`aapt2` can't load the Android 35 platform). The bridge library itself compiles; building the app needs compileSdk 34 or an AGP 8 / React Native upgrade. Not yet verified.
- **iOS example app.** Building React Native 0.72 with Xcode 27 is not yet verified.
- **Not in CI yet.** Running this in CI needs a simulator or emulator runner plus the sandbox secrets. Once the example app builds, add a manual (`workflow_dispatch`) job that builds the example, boots a device and runs `scripts/e2e/run-self-test.sh`.
