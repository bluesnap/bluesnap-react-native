import {
  NativeModules,
  NativeEventEmitter,
  Platform,
  type EmitterSubscription,
} from 'react-native';
import type {
  ApplePaySupport,
  BluesnapEventMap,
  CheckoutRequest,
  CreditCardInfo,
  InitOptions,
  PurchaseResult,
  ShopperConfiguration,
  ShopperRequirementsRequest,
  SubscriptionCheckoutRequest,
  ThreeDSRequest,
  TokenizeCardRequest,
} from './types';

export * from './types';

const LINKING_ERROR =
  `The package 'bluesnap-sdk-react-native' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const NativeBluesnap = NativeModules.BluesnapSdkReactNative
  ? NativeModules.BluesnapSdkReactNative
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

const eventEmitter = new NativeEventEmitter(NativeBluesnap);

/**
 * Initialize the BlueSnap SDK with a payment-fields token from your server.
 * Must be called before any checkout or tokenization flow.
 */
export function initBluesnap(options: InitOptions): Promise<void> {
  return NativeBluesnap.initBluesnap(options);
}

/**
 * Set a refreshed payment-fields token (also called automatically on token refresh).
 */
export function setBsToken(token: string): Promise<void> {
  return NativeBluesnap.setBsToken(token);
}

/**
 * Start the standard checkout flow (credit card, Apple Pay, PayPal / Google Pay).
 */
export function showCheckout(
  request: CheckoutRequest
): Promise<PurchaseResult> {
  return NativeBluesnap.showCheckout(request);
}

/**
 * Start subscription checkout flow.
 */
export function showSubscriptionCheckout(
  request: SubscriptionCheckoutRequest
): Promise<PurchaseResult> {
  return NativeBluesnap.showSubscriptionCheckout(request);
}

/**
 * Configure shopper payment preferences (returning shopper onboarding).
 */
export function showChoosePayment(
  request: ShopperRequirementsRequest
): Promise<PurchaseResult> {
  return NativeBluesnap.showChoosePayment(request);
}

/**
 * Charge a returning shopper with a saved payment method.
 */
export function showCreatePayment(
  request: CheckoutRequest
): Promise<PurchaseResult> {
  return NativeBluesnap.showCreatePayment(request);
}

/**
 * Submit card details using a custom UI (tokenization only).
 */
export function submitTokenizedDetails(
  request: TokenizeCardRequest
): Promise<Record<string, string>> {
  return NativeBluesnap.submitTokenizedDetails(request);
}

/**
 * Run 3D Secure authentication for custom UI flows.
 */
export function authenticate3DS(request: ThreeDSRequest): Promise<string> {
  return NativeBluesnap.authenticate3DS(request);
}

/**
 * Respond to a tax update event from the SDK (call from onTaxUpdate listener).
 */
export function respondToTaxUpdate(taxAmount: number): void {
  NativeBluesnap.respondToTaxUpdate(taxAmount);
}

/**
 * Get supported currency rates after initialization.
 */
export function getSupportedCurrencies(): Promise<string[]> {
  return NativeBluesnap.getSupportedCurrencies();
}

/**
 * Check Apple Pay availability (iOS only).
 */
export function applePaySupported(): Promise<ApplePaySupport> {
  return NativeBluesnap.applePaySupported();
}

/**
 * Get stored cards for a returning shopper (iOS only, after init with shopper token).
 */
export function getCards(): Promise<CreditCardInfo[]> {
  return NativeBluesnap.getCards();
}

/**
 * Get returning shopper configuration (Android only, after init with shopper token).
 */
export function getShopperConfiguration(): Promise<ShopperConfiguration | null> {
  return NativeBluesnap.getShopperConfiguration();
}

/**
 * Get native SDK version string for the current platform.
 */
export function getSdkVersion(): Promise<string> {
  return NativeBluesnap.getSdkVersion();
}

/** Subscribe to SDK events (token refresh, tax updates). */
export function addBluesnapListener<K extends keyof BluesnapEventMap>(
  event: K,
  listener: (payload: BluesnapEventMap[K]) => void
): EmitterSubscription {
  return eventEmitter.addListener(event, listener);
}
