import * as React from 'react';
import {
  Button,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import * as Bluesnap from 'bluesnap-sdk-react-native';
import { runBridgeSelfTest, type SelfTestResult } from './selfTest';

/**
 * Screen driven by e2e/bridge-self-test.yaml. Every element the flow touches
 * has a testID (Maestro matches `id:` against testID on both platforms).
 */
export default function SelfTestScreen() {
  const [token, setToken] = React.useState('');
  const [running, setRunning] = React.useState(false);
  const [result, setResult] = React.useState<SelfTestResult | null>(null);

  const run = async () => {
    setRunning(true);
    setResult(null);
    setResult(
      await runBridgeSelfTest(Bluesnap, { token, platform: Platform.OS })
    );
    setRunning(false);
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>Bridge self-test</Text>
      <TextInput
        testID="selftest-token"
        style={styles.input}
        placeholder="Sandbox payment-fields token"
        value={token}
        onChangeText={setToken}
        autoCapitalize="none"
        autoCorrect={false}
      />
      <Button
        testID="selftest-run"
        title={running ? 'Running...' : 'Run bridge self-test'}
        onPress={run}
        disabled={running}
      />
      {result ? (
        <View style={styles.results}>
          <Text testID="selftest-result" style={styles.overall}>
            {result.status}
          </Text>
          {result.steps.map((s) => (
            <Text key={s.name} testID={`selftest-step-${s.name}`}>
              {`${s.status} ${s.name}: ${s.detail}`}
            </Text>
          ))}
        </View>
      ) : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 24,
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 12,
  },
  input: {
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 4,
    padding: 8,
    marginBottom: 12,
  },
  results: {
    marginTop: 16,
  },
  overall: {
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 8,
  },
});
