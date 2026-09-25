# bluesnap-sdk-react-native

React Native bridge for the BlueSnap iOS and Android native SDKs.

| Platform | Native SDK | Version |
|----------|-----------|---------|
| iOS | [bluesnap-ios](https://github.com/bluesnap/bluesnap-ios) | 2.2.0 |
| Android | [bluesnap-android](https://github.com/bluesnap/bluesnap-android) | 2.5.1 |

## Requirements

- React Native >= 0.71
- iOS 15+
- Android API 29+ (Android 10+)

## Installation

```sh
npm install bluesnap-sdk-react-native
cd ios && pod install
```

### iOS setup

The bridge depends on `BluesnapSDK` 2.2.0 via CocoaPods, which requires iOS 15.0 or later (set `platform :ios, '15.0'` in your `Podfile`). Your app must include a `credentials.plist` with `BsAPIUser` and `BsAPIPassword` as required by the native SDK.

### Android setup

Add the JitPack and Cardinal repositories to your root `android/build.gradle`:

```gradle
allprojects {
  repositories {
    maven { url 'https://jitpack.io' }
    maven {
      url "https://cardinalcommerceprod.jfrog.io/artifactory/android"
      credentials {
        username 'bluesnap_sdk_users'
        password 'AKCp8jQnUytDaavAPeaX5SfvRP8e6PUsrmh8cyp1Be5wBpBdDKhLRmaaKiKj3pnFwu9mwzv2n'
      }
    }
  }
}
```

Set `minSdkVersion` to at least **29** in your app.

## Usage

### 1. Generate a token on your server

For each transaction, create a payment-fields token:

```
POST https://sandbox.bluesnap.com/services/2/payment-fields-tokens
```

Extract the token from the `Location` response header.

### 2. Initialize the SDK

```typescript
import {
  initBluesnap,
  addBluesnapListener,
  setBsToken,
  respondToTaxUpdate,
  showCheckout,
} from 'bluesnap-sdk-react-native';

// Handle token refresh when the SDK detects expiration
addBluesnapListener('onRequestNewToken', async () => {
  const newToken = await fetchTokenFromYourServer();
  await setBsToken(newToken);
});

// Handle dynamic tax updates when shipping changes
addBluesnapListener('onTaxUpdate', (event) => {
  const tax = calculateTax(event.country, event.state, event.amount);
  respondToTaxUpdate(tax);
});

await initBluesnap({
  token: merchantToken,
  initKount: true,
  merchantStoreCurrency: 'USD',
  applePayMerchantIdentifier: 'merchant.com.example', // iOS only
});
```

### 3. Start a checkout flow

```typescript
const result = await showCheckout({
  amount: 29.99,
  currency: 'USD',
  taxAmount: 0,
  emailRequired: true,
  billingRequired: true,
  shippingRequired: true,
  activate3DS: true,
});

// Send result to your server to complete the charge via BlueSnap Payment API
```

## API Reference

| Method | Description |
|--------|-------------|
| `initBluesnap(options)` | Initialize SDK with payment-fields token |
| `setBsToken(token)` | Set refreshed token |
| `showCheckout(request)` | Standard checkout UI |
| `showSubscriptionCheckout(request)` | Subscription checkout |
| `showChoosePayment(request)` | Configure shopper payment method |
| `showCreatePayment(request)` | Pay with saved payment method |
| `submitTokenizedDetails(request)` | Tokenize card with custom UI |
| `authenticate3DS(request)` | Run 3DS for custom UI |
| `respondToTaxUpdate(taxAmount)` | Respond to `onTaxUpdate` event |
| `getSupportedCurrencies()` | List supported currencies |
| `applePaySupported()` | Check Apple Pay availability (iOS) |
| `getCards()` | Get stored cards (iOS) |
| `getShopperConfiguration()` | Get shopper info (Android) |
| `getSdkVersion()` | Native SDK version |
| `addBluesnapListener(event, handler)` | Subscribe to SDK events |

### Events

| Event | When |
|-------|------|
| `onRequestNewToken` | SDK needs a fresh payment-fields token |
| `onTaxUpdate` | Shipping changed; app should call `respondToTaxUpdate` |

## License

MIT
