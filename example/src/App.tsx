import * as React from 'react';
import { StyleSheet, View, Text, Button, Platform } from 'react-native';
import {
  addBluesnapListener,
  getSdkVersion,
  initBluesnap,
  respondToTaxUpdate,
  setBsToken,
  showCheckout,
  type PurchaseResult,
} from 'bluesnap-sdk-react-native';
import SelfTestScreen from './SelfTestScreen';

/**
 * Example integration. Replace MERCHANT_TOKEN with a token from your server:
 * POST /services/2/payment-fields-tokens
 */
const MERCHANT_TOKEN = 'YOUR_PAYMENT_FIELDS_TOKEN';

export default function App() {
  const [sdkVersion, setSdkVersion] = React.useState<string>('');
  const [status, setStatus] = React.useState<string>('Not initialized');
  const [lastResult, setLastResult] = React.useState<PurchaseResult | null>(
    null
  );
  const [selfTest, setSelfTest] = React.useState(false);

  React.useEffect(() => {
    getSdkVersion().then((version) => {
      setSdkVersion(`${Platform.OS} SDK ${version}`);
    });

    const tokenListener = addBluesnapListener('onRequestNewToken', async () => {
      // Fetch a fresh token from your backend, then pass it to the SDK.
      const newToken = await fetchTokenFromServer();
      await setBsToken(newToken);
    });

    const taxListener = addBluesnapListener('onTaxUpdate', (event) => {
      // Calculate tax on your server or locally, then respond.
      const estimatedTax = event.amount * 0.1;
      respondToTaxUpdate(estimatedTax);
    });

    return () => {
      tokenListener.remove();
      taxListener.remove();
    };
  }, []);

  const initialize = async () => {
    try {
      setStatus('Initializing...');
      await initBluesnap({
        token: MERCHANT_TOKEN,
        initKount: true,
        merchantStoreCurrency: 'USD',
      });
      setStatus('SDK ready');
    } catch (error) {
      setStatus(`Init failed: ${String(error)}`);
    }
  };

  const startCheckout = async () => {
    try {
      setStatus('Opening checkout...');
      const result = await showCheckout({
        amount: 10.99,
        currency: 'USD',
        taxAmount: 0,
        emailRequired: true,
        billingRequired: true,
        shippingRequired: false,
        activate3DS: false,
      });
      setLastResult(result);
      setStatus('Checkout complete');
    } catch (error) {
      setStatus(`Checkout failed: ${String(error)}`);
    }
  };

  if (selfTest) {
    return <SelfTestScreen />;
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>BlueSnap React Native</Text>
      <Text style={styles.subtitle}>{sdkVersion}</Text>
      <Text style={styles.status}>{status}</Text>
      <Button title="Initialize SDK" onPress={initialize} />
      <View style={styles.spacer} />
      <Button title="Show Checkout" onPress={startCheckout} />
      <View style={styles.spacer} />
      <Button
        testID="open-selftest"
        title="Bridge self-test"
        onPress={() => setSelfTest(true)}
      />
      {lastResult ? (
        <Text style={styles.result}>{JSON.stringify(lastResult, null, 2)}</Text>
      ) : null}
    </View>
  );
}

async function fetchTokenFromServer(): Promise<string> {
  // Implement server-side token generation in your app.
  throw new Error('Implement fetchTokenFromServer with your backend');
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  title: {
    fontSize: 20,
    fontWeight: '600',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: '#666',
    marginBottom: 16,
  },
  status: {
    marginBottom: 16,
    textAlign: 'center',
  },
  spacer: {
    height: 12,
  },
  result: {
    marginTop: 16,
    fontSize: 12,
  },
});
