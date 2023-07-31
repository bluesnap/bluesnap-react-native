import * as React from 'react';

import { StyleSheet, View, Button, Alert } from 'react-native';
import * as BluesnapSdkReactNative from 'bluesnap-sdk-react-native';

// We import the username and password from a JSON file.
// ***THIS IS NOT TO BE REPLICATED IN PRODUCTION!!! FOR DEVELOPMENT PURPOSES ONLY!!!***
import * as credentials from './credentials.json';

import { Buffer } from 'buffer';

// This is the function that's being called when there's a need to generate a token for the BlueSnap SDK.
// After obtaining the token, you MUST call BluesnapSdkReactNative.finalizeToken with the token.
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

export default function App() {
  React.useEffect(() => {
    BluesnapSdkReactNative?.eventEmitter?.addListener(
      'generateToken',
      generateToken
    );

    BluesnapSdkReactNative.initBluesnap(
      'bsToken',
      true, // initKount
      '',
      'merchant.com.example.bluesnap',
      'USD'
    ).then((result) => {
      console.log(result);
    });
  }, []);

  return (
    <View style={styles.container}>
      <Button
        title="checkout"
        onPress={async () => {
          BluesnapSdkReactNative.setSDKRequest(
            false,
            false,
            false,
            2.0,
            1.1,
            'USD'
          ); //TOOD: catch errors here

          try {
            const data = await BluesnapSdkReactNative.showCheckout(true);
            console.log('data:', data);
          } catch (e) {
            console.log('Checkout error:  ' + e);
            Alert.alert('showCheckout error', 'Checkout error: ' + e);
          }
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
});
