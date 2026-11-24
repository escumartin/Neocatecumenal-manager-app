import * as Clipboard from 'expo-clipboard';
import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { usersText } from '@/i18n/es/users';
import type { CreatedUser } from '@/lib/api/users-api';

type Props = {
  created: CreatedUser;
  variant?: 'created' | 'reset';
  onDone: () => void;
};

type CopyState = 'idle' | 'copied' | 'failed';

export function UserCreated({ created, variant = 'created', onDone }: Props) {
  const [copyState, setCopyState] = useState<CopyState>('idle');
  const title = variant === 'reset' ? usersText.reset.title(created.name) : usersText.created.title(created.name);
  const warning = variant === 'reset' ? usersText.reset.onlyOnce : usersText.created.onlyOnce;

  async function copy() {
    try {
      await Clipboard.setStringAsync(
        `${usersText.created.accessId}: ${created.accessId}\n${usersText.created.password}: ${created.initialPassword}`,
      );
      setCopyState('copied');
    } catch {
      setCopyState('failed');
    }
  }

  return (
    <ScrollView
      className="flex-1 bg-background"
      contentContainerClassName="gap-6 p-6"
      keyboardShouldPersistTaps="handled"
    >
      <Text variant="title">{title}</Text>
      <View className="gap-2">
        <Text variant="strong">{usersText.created.accessId}</Text>
        <Text variant="display" selectable accessibilityLabel={`${usersText.created.accessId}: ${created.accessId}`}>
          {String(created.accessId)}
        </Text>
      </View>
      <View className="gap-2">
        <Text variant="strong">{usersText.created.password}</Text>
        <Text variant="display" selectable accessibilityLabel={`${usersText.created.password}: ${created.initialPassword}`}>
          {created.initialPassword}
        </Text>
      </View>
      <Button variant="outline" accessibilityLabel={usersText.created.copy} onPress={copy}>
        <Text>{usersText.created.copy}</Text>
      </Button>
      {copyState === 'copied' ? (
        <Text role="status" accessibilityLiveRegion="polite">{`✓ ${usersText.created.copied}`}</Text>
      ) : null}
      {copyState === 'failed' ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${usersText.created.copyFailed}`}
        </Text>
      ) : null}
      <Text role="status" accessibilityLiveRegion="polite" className="text-warning">
        {`⚠ ${warning}`}
      </Text>
      <Button accessibilityLabel={usersText.created.done} onPress={onDone}>
        <Text>{usersText.created.done}</Text>
      </Button>
    </ScrollView>
  );
}
