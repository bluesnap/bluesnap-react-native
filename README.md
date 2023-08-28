# bluesnap-sdk-react-native

A BlueSnap SDK React Native Bridge

## Installation


### npm 
```sh
npm install bluesnap-sdk-react-native 
```

### checkout the bridge prject
Add dependency to the checkout directory in your package.json
```sh
    "bluesnap-sdk-react-native": "file:../bluesnap-sdk-react-native/",
```

## Native SDKs Documentation
This Bridge project is using both Android and iOS Bluesnap Native SDK's which are open source and available on github on https://github.com/bluesnap/bluesnap-ios and https://github.com/bluesnap/bluesnap-android-int
It is highly recommended to read both SDK's README.md in order to understand how this bridge project is working and get familiar with the features of each SDK.


## Token
The Bluesnap Native SDKs require a short lived token in order to call API. You should obtain the token during runtime from your backend and pass it to the intialization method.


## Example applications 
This project contains example React Native application that demonstrates the SDK 

### Example application - iOS 

1. Check out the repostory and run 'yarn ios'
2. Open the example app's `src` directory, add a new file called `credentials.json`. In the file, create an object that contains 2 strings, one called `username` and the other called `password`, each of them must be set to their respective values:

```
{
    "username": "REPLACE_ME",
    "password": "REPLACE_ME"
}
```

3. Open the example app project and in the `BluesnapSdkReactNativeExample` workspace (do not open the project directly) by opening BluesnapSdkReactNativeExample.xcworkspace in xcode  
4. You can then run the example application using xcode without having to implement an example backend. This approach is not for production usage.
5. Clicking the checkout link in the application will start a checkout flow using the native SDK, currently the checkout details such as price are not configurable in this demo mode.
6. if clicking the checkout button does not open the SDK - check the error logs using xcode.

### Using a custom checkout UI - explanation

The steps for processing a checkout manually without using the existing UI are as follows:

1. Initialize the BlueSnap SDK
2. set the SDK request
3. Run the checkout function
4. Collect the SDK result

We initialize the BlueSnap SDK by calling `initBluesnap` on view load via the `useEffect` function and setting a hook for generating new tokens:

```
const generateToken = (event: any) => {
  var urlStr = 'https://sandbox.bluesnap.com/services/2/payment-fields-tokens';

  if (event.shopperID !== undefined && event.shopperID != null) {
    urlStr += `?shopperId=${event.shopperID}`;
  }

  fetch(urlStr, {
    method: 'POST',
    headers: {
      'Content-Type': 'text/xml',
      'Authorization': `Basic ${Buffer.from(
        `${credentials.username}:${credentials.password}`
      ).toString('base64')}`,
    },
  })
    .then((response) => {
      var locationTokenArray = response.headers.get('location')!.split('/');
      let token: string = locationTokenArray[locationTokenArray.length - 1]!;

      BluesnapSdkReactNative.finalizeToken(token);
    })
    .catch((_) => {
      Alert.alert(
        'Token fetch',
        'Cannot fetch token, did you set the credentials? '
      ); //TODO: better parsing of error here
    });
};

useEffect(() => {
    BluesnapSdkReactNative?.eventEmitter?.addListener(
        'generateToken',
        generateToken
    );

    setConnectionStatus('');
    BluesnapSdkReactNative.initBluesnap(
        'bsToken',
        true, // initKount
        '',
        'merchant.com.example.bluesnap',
        'USD'
    )
    .then((result) => {
        console.log(result);
        setConnectionStatus(result);
    })
    .catch((error) => {
        console.log(error);
        setConnectionStatus(error);
    });
}, []);
```

After that, we can set up a minimal checkout UI in react native as an alternative to the Bluesnap Native SDK's UI.


```
const renderCheckoutCard = () => {
    return (
        <View style={styles.checkoutCard}>
            <TextInput
                style={[styles.textInput, { width: 320 }]}
                keyboardType={'number-pad'}
                maxLength={16}
                placeholder={'cardNumber'}
                value={cardNumber}
                onChangeText={setCardNumber}
            />
            <View style={styles.verticalSpacer} />
            <View style={styles.row}>
            <TextInput
                style={styles.textInput}
                keyboardType={'number-pad'}
                maxLength={2}
                placeholder={'month'}
                value={month}
                onChangeText={setMonth}
            />
            <View style={styles.horizontalSpacer} />
            <Text style={[styles.statusText, { fontSize: 40 }]}>/</Text>
            <View style={styles.horizontalSpacer} />
            <TextInput
                style={styles.textInput}
                keyboardType={'number-pad'}
                maxLength={2}
                placeholder={'year'}
                value={year}
                onChangeText={setYear}
            />
            <View style={{ width: 50 }} />
            <TextInput
                style={styles.textInput}
                keyboardType={'number-pad'}
                maxLength={4}
                placeholder={'cvv'}
                value={cvv}
                onChangeText={setCvv}
            />
            </View>
            <View style={styles.verticalSpacer} />
            <TextInput
                style={[styles.textInput, { width: 320 }]}
                placeholder={'name'}
                value={name}
                onChangeText={setName}
            />
            <View style={styles.verticalSpacer} />
            <TextInput
                style={[styles.textInput, { width: 320 }]}
                placeholder={'billingZip'}
                value={billingZip}
                onChangeText={setBillingZip}
            />
            <View style={styles.verticalSpacer} />
            <Button
                title="checkout card"
                onPress={async () => {
                    setPaymentStatus('');
                    Keyboard.dismiss();
                    BluesnapSdkReactNative.setSDKRequest(
                        false,
                        false,
                        false,
                        2.0,
                        1.1,
                        'USD',
                        true
                    );

                    try {
                        const data = await BluesnapSdkReactNative.checkoutCard(
                            cardNumber,
                            `${month}/${year}`,
                            cvv,
                            name,
                            billingZip,
                            undefined
                        );
                        console.log('data:', data);
                        setPaymentStatus(JSON.stringify(data));
                    } catch (e) {
                        console.log('Checkout error:  ' + e);
                        Alert.alert('showCheckout error', 'Checkout error: ' + e);
                    }
                }}
            />
        </View>
    );
};
```

In here, we are defining a simple set of fields that will let the user enter their payment information. In the button's `onPress` handler, we can see this:

```
async () => {
    BluesnapSdkReactNative.setSDKRequest(
        false,
        false,
        false,
        2.0,
        1.1,
        'USD',
        true
    );
    
    try {
        const data = await BluesnapSdkReactNative.checkoutCard(
            cardNumber,
            `${month}/${year}`,
            cvv,
            name,
            billingZip,
            undefined
        );
        console.log('data:', data)
    } catch (e) {
        ...
    }
}
```

Here, we perform the 2 final tasks required to register a payment - `setSDKRequest` and `checkoutCard`.

In `setSDKRequest`, we are telling the BlueSnap SDK that we are checking out minimally - the 3 `false`es are telling the SDK that we are not providing an email, no shipping details, no full billing details. After that, we are providing the amount of money to charge and the amount of tax to add to that charge (`2.0` and `1.1` respectively). After that we are configuring the SDK that we are billing the user in USD, and lastly - we are telling the SDK that we require the use of Cardinal 3DS authentication (this can be turned off by setting it to `false`). 
For more information on the sdkRequst features check out the native SDK's Android/iOS Respectively.

**Note: It is not possible to change Cardinal's popup default UI and it is not testable on simulators due to cardinal limitations at the moment.**

In `checkoutCard`, we are telling the SDK to finish the checkout procedure by charging the user. In this step, we are providing the CCN, expiration date, CVV, cardholder name, billing Zip code and the email (in our case, we are not asking the user for an email so we are providing the SDK with `undefined` for an email).

At the end of the process, we either get a JSON object containing the result of the operation or an error that needs to be handled.  This JSON will contain a TOKENIZAION_SUCCESS message or error message respectively.
This JSON object is the response of the Bluesnap API tokenization method which is documented exstensively in the bluesnap website.


## Contributing

We would love to get your feedback and Pull Request!
See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
