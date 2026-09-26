/**
 * End-to-end bridge self-test: real calls through JS -> native module ->
 * BlueSnap native SDK -> BlueSnap sandbox and back, without opening any native
 * payment screen, so it can run unattended (see e2e/README.md).
 *
 * The bridge API is passed in, so the runner itself is unit-tested with mocks
 * (src/__tests__/selfTest.test.ts) and the screen stays a thin wrapper.
 */
import type * as Bluesnap from 'bluesnap-sdk-react-native';

export type BridgeApi = Pick<
  typeof Bluesnap,
  | 'getSdkVersion'
  | 'initBluesnap'
  | 'getSupportedCurrencies'
  | 'submitTokenizedDetails'
  | 'applePaySupported'
  | 'getShopperConfiguration'
>;

export type StepStatus = 'PASS' | 'FAIL';

export interface StepResult {
  name: string;
  status: StepStatus;
  detail: string;
}

export interface SelfTestResult {
  status: StepStatus;
  steps: StepResult[];
}

export interface SelfTestOptions {
  /** Sandbox payment-fields token (scripts/e2e/get-token.sh). */
  token: string;
  platform: string;
  /** Per-step limit, so a native promise that never settles fails the run. */
  timeoutMs?: number;
  now?: Date;
}

/** BlueSnap sandbox Visa test card. */
export const TEST_CARD_NUMBER = '4111111111111111';

const withTimeout = <T>(promise: Promise<T>, ms: number, name: string) =>
  new Promise<T>((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new Error(`${name} did not settle within ${ms} ms`)),
      ms
    );
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      }
    );
  });

function check(condition: boolean, message: string): asserts condition {
  if (!condition) {
    throw new Error(message);
  }
}

const describeError = (error: unknown) => {
  if (error instanceof Error) {
    const code = (error as Error & { code?: string }).code;
    return code ? `${code}: ${error.message}` : error.message;
  }
  return String(error);
};

export async function runBridgeSelfTest(
  api: BridgeApi,
  options: SelfTestOptions
): Promise<SelfTestResult> {
  const timeoutMs = options.timeoutMs ?? 30000;
  const now = options.now ?? new Date();
  const steps: StepResult[] = [];

  const step = async (name: string, run: () => Promise<string>) => {
    try {
      const detail = await withTimeout(run(), timeoutMs, name);
      steps.push({ name, status: 'PASS', detail });
      return true;
    } catch (error) {
      steps.push({ name, status: 'FAIL', detail: describeError(error) });
      return false;
    }
  };

  await step('getSdkVersion', async () => {
    const version = await api.getSdkVersion();
    check(/^\d+\.\d+\.\d+$/.test(version), `unexpected version "${version}"`);
    return version;
  });

  const token = options.token.trim();
  const initialized = await step('initBluesnap', async () => {
    check(token.length > 0, 'no token: paste a sandbox payment-fields token');
    await api.initBluesnap({ token, initKount: false });
    return 'initialized';
  });

  const afterInit: Array<[string, () => Promise<string>]> = [
    [
      'getSupportedCurrencies',
      async () => {
        const currencies = await api.getSupportedCurrencies();
        check(
          Array.isArray(currencies) && currencies.length > 0,
          'no currencies returned'
        );
        return `${currencies.length} currencies`;
      },
    ],
    [
      'submitTokenizedDetails',
      async () => {
        const result = await api.submitTokenizedDetails({
          card: {
            cardNumber: TEST_CARD_NUMBER,
            cvv: '123',
            expirationMonth: '12',
            expirationYear: String(now.getFullYear() + 2),
          },
          billing: { name: 'E2E Test', zip: '02453', country: 'US' },
          storeCard: false,
        });
        check(
          result !== null && typeof result === 'object',
          'no tokenization result'
        );
        return 'card tokenized';
      },
    ],
    options.platform === 'ios'
      ? [
          'applePaySupported',
          async () => {
            const support = await api.applePaySupported();
            check(
              typeof support?.canMakePayments === 'boolean',
              'missing canMakePayments'
            );
            return `canMakePayments=${support.canMakePayments}`;
          },
        ]
      : [
          'getShopperConfiguration',
          async () => {
            // null is expected for a token that isn't tied to a vaulted shopper
            const configuration = await api.getShopperConfiguration();
            return configuration === null ? 'no shopper' : 'shopper found';
          },
        ],
  ];

  for (const [name, run] of afterInit) {
    if (initialized) {
      await step(name, run);
    } else {
      steps.push({
        name,
        status: 'FAIL',
        detail: 'not run: initBluesnap failed',
      });
    }
  }

  return {
    status: steps.every((s) => s.status === 'PASS') ? 'PASS' : 'FAIL',
    steps,
  };
}
