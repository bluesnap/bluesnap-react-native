import * as BluesnapSdkReactNative from '../';

// // The BlueSnap API credentials.
// // NOTE: FOR DEVELOPMENT PURPOSES ONLY - **NOT FOR PRODUCTION!!!**.
// const USERNAME = 'REPLACE_ME';
// const PASSWORD = 'REPLACE_ME';

// // This is the function that's being called when there's a need to generate a token for the BlueSnap SDK.
// // After obtaining the token, you MUST call BluesnapSdkReactNative.finalizeToken with the token.
// const generateToken = (
//   event: { shopperID: string },
//   resolve?: (token: string) => any,
//   reject?: (error: any) => any
// ) => {
//   var urlStr = 'https://sandbox.bluesnap.com/services/2/payment-fields-tokens';

//   if (event.shopperID !== undefined && event.shopperID != null) {
//     urlStr += `?shopperId=${event.shopperID}`;
//   }

//   fetch(urlStr, {
//     method: 'POST',
//     headers: {
//       'Content-Type': 'text/xml',
//       'Authorization': `Basic ${Buffer.from(`${USERNAME}:${PASSWORD}`).toString(
//         'base64'
//       )}`,
//     },
//   })
//     .then((response) => {
//       var locationTokenArray = response.headers.get('location')!.split('/');
//       let token: string = locationTokenArray[locationTokenArray.length - 1]!;

//       resolve?.(token);
//     })
//     .catch((e) => {
//       reject?.(e);
//     });
// };

/**
 * Check sdk flow with @initBluesnap
 */
describe('Check Bluesnap inital', () => {
  /**
   * Init succcess
   */
  test('Init Bluesnap success', async () => {
    // BluesnapSdkReactNative?.eventEmitter?.addListener('generateToken', (_) => {
    //   generateToken(
    //     {
    //       shopperID: '',
    //     },
    //     (token) => BluesnapSdkReactNative.finalizeToken(token),
    //     (error) => console.log(error)
    //   );
    // });
    //Fake generate token
    setTimeout(() => {
      BluesnapSdkReactNative.finalizeToken(''); // No token
    }, 1000);

    const initRestult = await BluesnapSdkReactNative.initBluesnap(
      'bsToken',
      true, // initKount
      '',
      'merchant.com.example.bluesnap',
      'USD'
    );
    expect(initRestult).toBe('unknown-string');
  });
});
