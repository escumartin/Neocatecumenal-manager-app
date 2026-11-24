import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { useSession } from '@/hooks/session/use-session';
import { identity } from '@/i18n/es/identity';
import { privacyNotice } from '@/i18n/es/privacy-notice';

export function PrivacyNotice() {
  const { acceptPrivacyNotice, rejectPrivacyNotice } = useSession();
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function accept() {
    setBusy(true);
    setMessage(null);
    const result = await acceptPrivacyNotice();
    setBusy(false);
    if (!result.ok) {
      setMessage(privacyNotice.errors[result.code ?? ''] ?? identity.errors.UNKNOWN_ACTION);
    }
  }

  return (
    <ScrollView className="flex-1 bg-background" contentContainerClassName="gap-6 p-6">
      <Text variant="display">{privacyNotice.title}</Text>
      {privacyNotice.paragraphs.map((paragraph) => (
        <Text key={paragraph}>{paragraph}</Text>
      ))}
      {message ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${message}`}
        </Text>
      ) : null}
      <Button accessibilityLabel={privacyNotice.accept} disabled={busy} onPress={accept}>
        <Text>{busy ? privacyNotice.accepting : privacyNotice.accept}</Text>
      </Button>
      <View className="gap-2">
        <Text variant="muted">{privacyNotice.rejectConsequence}</Text>
        <Button
          variant="outline"
          accessibilityLabel={privacyNotice.reject}
          disabled={busy}
          onPress={rejectPrivacyNotice}
        >
          <Text>{privacyNotice.reject}</Text>
        </Button>
      </View>
    </ScrollView>
  );
}
