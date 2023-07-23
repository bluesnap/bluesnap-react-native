import { NativeModules, Platform, NativeEventEmitter } from 'react-native';
import type { BSBaseSdkResult } from 'types/index.types';

const LINKING_ERROR =
  `The package 'bluesnap-sdk-react-native' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const BluesnapSdkReactNative = NativeModules.BluesnapSdkReactNative
  ? NativeModules.BluesnapSdkReactNative
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export const eventEmitter = new NativeEventEmitter(BluesnapSdkReactNative);

export async function initBluesnap(
  bsTokenString: string,
  initKount: boolean,
  fraudSessionId: string,
  applePayMerchantIdentifier: string,
  merchantStoreCurrency: string
): Promise<any> {
  //return BluesnapSdkReactNative.multiply(a, b);
  return await BluesnapSdkReactNative.initBluesnap(
    bsTokenString,
    initKount,
    fraudSessionId,
    applePayMerchantIdentifier,
    merchantStoreCurrency
  );
}

export function setSDKRequest(
  withEmail: boolean,
  withShipping: boolean,
  fullBilling: boolean,
  amount: number,
  taxAmount: number,
  currency: string
) {
  BluesnapSdkReactNative.setSDKRequest(
    withEmail,
    withShipping,
    fullBilling,
    amount,
    taxAmount,
    currency
  );
}

export function finalizeToken(token: string | null) {
  BluesnapSdkReactNative.finalizeToken(token);
}

export async function showCheckout(
  activate3DS: boolean
): Promise<BSBaseSdkResult> {
  return await BluesnapSdkReactNative.showCheckout(activate3DS);
}
