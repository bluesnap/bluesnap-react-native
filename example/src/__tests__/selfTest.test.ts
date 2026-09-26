import {
  runBridgeSelfTest,
  TEST_CARD_NUMBER,
  type BridgeApi,
} from '../selfTest';

const makeApi = (overrides: Partial<Record<keyof BridgeApi, jest.Mock>> = {}) =>
  ({
    getSdkVersion: jest.fn().mockResolvedValue('2.2.0'),
    initBluesnap: jest.fn().mockResolvedValue(undefined),
    getSupportedCurrencies: jest.fn().mockResolvedValue(['USD', 'EUR']),
    submitTokenizedDetails: jest
      .fn()
      .mockResolvedValue({ last4Digits: '1111' }),
    applePaySupported: jest
      .fn()
      .mockResolvedValue({ canMakePayments: true, canSetupCards: false }),
    getShopperConfiguration: jest.fn().mockResolvedValue(null),
    ...overrides,
  } as unknown as BridgeApi & Record<keyof BridgeApi, jest.Mock>);

const now = new Date('2026-09-25T00:00:00Z');
const names = (r: { steps: { name: string }[] }) => r.steps.map((s) => s.name);

describe('runBridgeSelfTest', () => {
  it('passes on iOS when every bridge call succeeds', async () => {
    const api = makeApi();
    const result = await runBridgeSelfTest(api, {
      token: ' tok_1 ',
      platform: 'ios',
      now,
    });
    expect(result.status).toBe('PASS');
    expect(names(result)).toEqual([
      'getSdkVersion',
      'initBluesnap',
      'getSupportedCurrencies',
      'submitTokenizedDetails',
      'applePaySupported',
    ]);
    expect(api.initBluesnap).toHaveBeenCalledWith({
      token: 'tok_1',
      initKount: false,
    });
    expect(api.getShopperConfiguration).not.toHaveBeenCalled();
  });

  it('checks getShopperConfiguration instead of Apple Pay on Android', async () => {
    const api = makeApi();
    const result = await runBridgeSelfTest(api, {
      token: 'tok',
      platform: 'android',
      now,
    });
    expect(result.status).toBe('PASS');
    expect(names(result)).toContain('getShopperConfiguration');
    expect(api.applePaySupported).not.toHaveBeenCalled();
  });

  it('tokenizes the sandbox test card with a future expiry', async () => {
    const api = makeApi();
    await runBridgeSelfTest(api, { token: 'tok', platform: 'ios', now });
    expect(api.submitTokenizedDetails).toHaveBeenCalledWith(
      expect.objectContaining({
        card: {
          cardNumber: TEST_CARD_NUMBER,
          cvv: '123',
          expirationMonth: '12',
          expirationYear: '2028',
        },
        storeCard: false,
      })
    );
  });

  it('fails the run and reports the native error code when a call rejects', async () => {
    const error = Object.assign(new Error('card declined'), {
      code: 'TOKENIZE_FAILED',
    });
    const api = makeApi({
      submitTokenizedDetails: jest.fn().mockRejectedValue(error),
    });
    const result = await runBridgeSelfTest(api, {
      token: 'tok',
      platform: 'ios',
      now,
    });
    expect(result.status).toBe('FAIL');
    expect(
      result.steps.find((s) => s.name === 'submitTokenizedDetails')
    ).toEqual({
      name: 'submitTokenizedDetails',
      status: 'FAIL',
      detail: 'TOKENIZE_FAILED: card declined',
    });
    // later steps still run, so one run shows every broken call
    expect(
      result.steps.find((s) => s.name === 'applePaySupported')?.status
    ).toBe('PASS');
  });

  it('marks the dependent steps as not run when initBluesnap fails', async () => {
    const api = makeApi({
      initBluesnap: jest.fn().mockRejectedValue(new Error('bad token')),
    });
    const result = await runBridgeSelfTest(api, {
      token: 'tok',
      platform: 'android',
      now,
    });
    expect(result.status).toBe('FAIL');
    expect(
      result.steps
        .slice(2)
        .every((s) => s.detail === 'not run: initBluesnap failed')
    ).toBe(true);
    expect(api.getSupportedCurrencies).not.toHaveBeenCalled();
  });

  it('fails without calling init when no token was entered', async () => {
    const api = makeApi();
    const result = await runBridgeSelfTest(api, {
      token: '  ',
      platform: 'ios',
      now,
    });
    expect(result.steps[1]).toMatchObject({
      name: 'initBluesnap',
      status: 'FAIL',
    });
    expect(api.initBluesnap).not.toHaveBeenCalled();
  });

  it('rejects malformed SDK versions', async () => {
    const api = makeApi({ getSdkVersion: jest.fn().mockResolvedValue('') });
    const result = await runBridgeSelfTest(api, {
      token: 'tok',
      platform: 'ios',
      now,
    });
    expect(result.steps[0]).toMatchObject({
      status: 'FAIL',
      detail: 'unexpected version ""',
    });
  });

  it('fails a step whose native promise never settles', async () => {
    const api = makeApi({
      getSupportedCurrencies: jest.fn(() => new Promise(() => {})),
    });
    const result = await runBridgeSelfTest(api, {
      token: 'tok',
      platform: 'ios',
      now,
      timeoutMs: 20,
    });
    expect(
      result.steps.find((s) => s.name === 'getSupportedCurrencies')
    ).toMatchObject({
      status: 'FAIL',
      detail: 'getSupportedCurrencies did not settle within 20 ms',
    });
  });
});
