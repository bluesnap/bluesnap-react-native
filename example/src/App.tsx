import React, { useEffect, useState } from 'react';

import {
  StyleSheet,
  View,
  Button,
  Alert,
  Text,
  SafeAreaView,
  TextInput,
  ScrollView,
  Keyboard,
} from 'react-native';
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
  //States
  const [connectionStatus, setConnectionStatus] = useState('');
  const [paymentStatus, setPaymentStatus] = useState('');
  const [cardNumber, setCardNumber] = useState('');
  const [month, setMonth] = useState('');
  const [year, setYear] = useState('');
  const [cvv, setCvv] = useState('');
  const [name, setName] = useState('');
  const [billingZip, setBillingZip] = useState('');
  //Effects
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

  const renderCheckoutNative = () => {
    return (
      <View style={styles.checkoutNative}>
        <Button
          title="checkout"
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
            ); //TOOD: catch errors here

            try {
              const data = await BluesnapSdkReactNative.showCheckout();
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
            ); //TOOD: catch errors here

            try {
              const data = await BluesnapSdkReactNative.checkoutCard(
                cardNumber,
                `${month}/${year}`,
                cvv,
                name,
                billingZip,
                undefined //FIXME
              );
              console.log('data:', data);
              setPaymentStatus(JSON.stringify(data));
            } catch (e) {
              console.log('Checkout error:  ' + e);
              Alert.alert('showCheckout error', 'Checkout error: ' + e);
            }
          }}
        />
        {/* <View style={styles.verticalSpacer} />
        <Text style={styles.statusText}>{connectionStatus}</Text>
        <View style={styles.verticalSpacer} />
        <Text style={styles.statusText}>{paymentStatus}</Text> */}
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        style={styles.flexOne}
        contentContainerStyle={styles.container}
        keyboardShouldPersistTaps={'handled'}
        automaticallyAdjustKeyboardInsets
      >
        <Text style={styles.statusText}>{connectionStatus}</Text>
        <View style={styles.verticalSpacer} />
        <Text style={styles.titleText}>Using Native UI:</Text>
        {renderCheckoutNative()}
        <Text style={styles.titleText}>Using Custom UI:</Text>
        {renderCheckoutCard()}
        <View style={styles.verticalSpacer} />
        <Text style={styles.statusText}>{paymentStatus}</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flexOne: {
    flex: 1,
  },
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkoutNative: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkoutCard: {
    flex: 1,
    alignItems: 'flex-start',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
  titleText: {
    color: 'blue',
    fontSize: 20,
    fontWeight: 'bold',
    alignSelf: 'flex-start',
  },
  statusText: {
    color: '#30A14E',
    fontSize: 14,
  },
  verticalSpacer: {
    height: 10,
  },
  horizontalSpacer: {
    width: 10,
  },
  row: {
    flexDirection: 'row',
  },
  textInput: {
    minWidth: 60,
    height: 42,
    color: 'blue',
    borderWidth: 1,
    borderColor: 'blue',
    paddingHorizontal: 4,
  },
});
