import { Redirect } from 'expo-router';
import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import { useSession } from '@/hooks/session/use-session';
import { identity, messageForLoginFailure } from '@/i18n/es/identity';

const MAX_PASSWORD_LENGTH = 64;

export function Login() {
  const { status, logIn } = useSession();
  const [accessId, setAccessId] = useState('');
  const [password, setPassword] = useState('');
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    const parsedAccessId = Number.parseInt(accessId, 10);
    if (Number.isNaN(parsedAccessId) || password.length === 0) {
      setMessage(identity.login.required);
      return;
    }
    setSubmitting(true);
    setMessage(null);
    try {
      const outcome = await logIn(parsedAccessId, password);
      if (!outcome.ok) {
        setMessage(messageForLoginFailure(outcome.code, outcome.retryAfterSeconds));
      }
    } catch {
      setMessage(messageForLoginFailure('UNKNOWN'));
    } finally {
      setSubmitting(false);
    }
  }

  if (status === 'authenticated') {
    return <Redirect href="/" />;
  }

  return (
    <ScrollView
      className="flex-1 bg-background"
      contentContainerClassName="flex-grow justify-center gap-6 p-6"
      keyboardShouldPersistTaps="handled"
    >
      <Text variant="display">{identity.login.title}</Text>
      <View className="gap-2">
        <Text variant="strong">{identity.login.accessIdLabel}</Text>
        <Input
          accessibilityLabel={identity.login.accessIdLabel}
          keyboardType="number-pad"
          inputMode="numeric"
          autoComplete="username"
          maxLength={6}
          value={accessId}
          onChangeText={setAccessId}
        />
      </View>
      <View className="gap-2">
        <Text variant="strong">{identity.login.passwordLabel}</Text>
        <Input
          accessibilityLabel={identity.login.passwordLabel}
          secureTextEntry={!passwordVisible}
          autoComplete="current-password"
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={MAX_PASSWORD_LENGTH}
          value={password}
          onChangeText={setPassword}
          onSubmitEditing={submit}
        />
        <Button
          variant="ghost"
          accessibilityLabel={passwordVisible ? identity.login.hidePassword : identity.login.showPassword}
          onPress={() => setPasswordVisible((visible) => !visible)}
        >
          <Text>{passwordVisible ? identity.login.hidePassword : identity.login.showPassword}</Text>
        </Button>
      </View>
      {message ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${message}`}
        </Text>
      ) : null}
      <Button
        accessibilityLabel={identity.login.submit}
        disabled={submitting}
        onPress={submit}
      >
        <Text>{submitting ? identity.login.submitting : identity.login.submit}</Text>
      </Button>
    </ScrollView>
  );
}
