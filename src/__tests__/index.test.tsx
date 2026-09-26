/**
 * JS-layer tests: every exported function must forward its arguments to the
 * matching native method unchanged and hand back the native promise.
 * Native-side parity (method names, events, SDK versions) is covered by
 * bridgeContract.test.ts.
 */
import type {
  ApplePaySupport,
  CheckoutRequest,
  CreditCardInfo,
  InitOptions,
  PurchaseResult,
  ShopperRequirementsRequest,
  SubscriptionCheckoutRequest,
  ThreeDSRequest,
  TokenizeCardRequest,
} from '../types';

const mockEmitter = { addListener: jest.fn() };

const mockNative = {
  initBluesnap: jest.fn(),
  setBsToken: jest.fn(),
  showCheckout: jest.fn(),
  showSubscriptionCheckout: jest.fn(),
  showChoosePayment: jest.fn(),
  showCreatePayment: jest.fn(),
  submitTokenizedDetails: jest.fn(),
  authenticate3DS: jest.fn(),
  respondToTaxUpdate: jest.fn(),
  getSupportedCurrencies: jest.fn(),
  applePaySupported: jest.fn(),
  getCards: jest.fn(),
  getShopperConfiguration: jest.fn(),
  getSdkVersion: jest.fn(),
  addListener: jest.fn(),
  removeListeners: jest.fn(),
};

jest.mock('react-native', () => ({
  NativeModules: { BluesnapSdkReactNative: mockNative },
  NativeEventEmitter: jest.fn().mockImplementation(() => mockEmitter),
  Platform: {
    select: (spec: { ios?: string; default?: string }) =>
      spec.ios ?? spec.default,
  },
}));

// Loaded after the mocks above exist: an `import` would be hoisted above them.

const Bluesnap: typeof import('../index') = require('../index');
// recorded before beforeEach clears mock state
const emitterCreatedWith =
  jest.requireMock('react-native').NativeEventEmitter.mock.calls[0]?.[0];

const checkout = {
  amount: 10.5,
  currency: 'USD',
  taxAmount: 1.5,
  billingRequired: true,
  activate3DS: true,
  billingDetails: { name: 'Jane Doe', country: 'US', zip: '02453' },
} satisfies CheckoutRequest;

// Promise-returning API: [export name, native method, args, native result]
const promiseCases: Array<
  [keyof typeof Bluesnap, keyof typeof mockNative, unknown[], unknown]
> = [
  [
    'initBluesnap',
    'initBluesnap',
    [
      {
        token: 'tok_123',
        initKount: true,
        merchantStoreCurrency: 'USD',
      } satisfies InitOptions,
    ],
    undefined,
  ],
  ['setBsToken', 'setBsToken', ['tok_456'], undefined],
  [
    'showCheckout',
    'showCheckout',
    [checkout],
    {
      paymentType: 'CC',
      token: 'tok_123',
      last4Digits: '1111',
    } satisfies PurchaseResult,
  ],
  [
    'showSubscriptionCheckout',
    'showSubscriptionCheckout',
    [
      {
        ...checkout,
        showSubscriptionCancellationMessage: true,
      } satisfies SubscriptionCheckoutRequest,
    ],
    { paymentType: 'CC', isSubscriptionCharge: true } satisfies PurchaseResult,
  ],
  [
    'showChoosePayment',
    'showChoosePayment',
    [
      {
        billingRequired: true,
        emailRequired: true,
      } satisfies ShopperRequirementsRequest,
    ],
    { isShopperRequirements: true } satisfies PurchaseResult,
  ],
  [
    'showCreatePayment',
    'showCreatePayment',
    [checkout],
    { paymentType: 'CC' } satisfies PurchaseResult,
  ],
  [
    'submitTokenizedDetails',
    'submitTokenizedDetails',
    [
      {
        card: {
          cardNumber: '4111111111111111',
          cvv: '123',
          expirationMonth: '12',
          expirationYear: '2030',
        },
        storeCard: false,
      } satisfies TokenizeCardRequest,
    ],
    { last4Digits: '1111', ccType: 'VISA' },
  ],
  [
    'authenticate3DS',
    'authenticate3DS',
    [{ amount: '10.00', currency: 'USD' } satisfies ThreeDSRequest],
    'AUTHENTICATION_SUCCEEDED',
  ],
  ['getSupportedCurrencies', 'getSupportedCurrencies', [], ['USD', 'EUR']],
  [
    'applePaySupported',
    'applePaySupported',
    [],
    { canMakePayments: true, canSetupCards: false } satisfies ApplePaySupport,
  ],
  [
    'getCards',
    'getCards',
    [],
    [{ last4Digits: '1111', cardType: 'VISA' }] satisfies CreditCardInfo[],
  ],
  ['getShopperConfiguration', 'getShopperConfiguration', [], null],
  ['getSdkVersion', 'getSdkVersion', [], '9.9.9'],
];

beforeEach(() => {
  jest.clearAllMocks();
});

describe.each(promiseCases)(
  '%s',
  (exportName, nativeName, args, nativeResult) => {
    const call = () =>
      (
        Bluesnap[exportName] as unknown as (...a: unknown[]) => Promise<unknown>
      )(...args);

    it(`forwards its arguments to NativeModules.BluesnapSdkReactNative.${nativeName}`, async () => {
      mockNative[nativeName].mockResolvedValueOnce(nativeResult);
      await call();
      expect(mockNative[nativeName]).toHaveBeenCalledTimes(1);
      expect(mockNative[nativeName]).toHaveBeenCalledWith(...args);
      // the request object is passed through as-is, not copied or reshaped
      args.forEach((arg, i) =>
        expect(mockNative[nativeName].mock.calls[0]?.[i]).toBe(arg)
      );
    });

    it('resolves with the native result', async () => {
      mockNative[nativeName].mockResolvedValueOnce(nativeResult);
      await expect(call()).resolves.toEqual(nativeResult);
    });

    it('rejects with the native error (e.g. shopper cancelled, SDK not initialized)', async () => {
      const error = Object.assign(new Error('native failure'), {
        code: 'NOT_INITIALIZED',
      });
      mockNative[nativeName].mockRejectedValueOnce(error);
      await expect(call()).rejects.toBe(error);
    });
  }
);

describe('respondToTaxUpdate', () => {
  it('forwards the tax amount and returns nothing (fire-and-forget on both platforms)', () => {
    expect(Bluesnap.respondToTaxUpdate(2.25)).toBeUndefined();
    expect(mockNative.respondToTaxUpdate).toHaveBeenCalledWith(2.25);
  });
});

describe('addBluesnapListener', () => {
  it('builds the event emitter on the native module', () => {
    expect(emitterCreatedWith).toBe(mockNative);
  });

  it.each(['onRequestNewToken', 'onTaxUpdate'] as const)(
    'subscribes to %s and returns the subscription',
    (event) => {
      const subscription = { remove: jest.fn() };
      mockEmitter.addListener.mockReturnValueOnce(subscription);
      const listener = jest.fn();
      expect(Bluesnap.addBluesnapListener(event, listener)).toBe(subscription);
      expect(mockEmitter.addListener).toHaveBeenCalledWith(event, listener);
    }
  );
});
