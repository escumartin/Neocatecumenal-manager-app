import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { useSession } from '@/hooks/session/use-session';
import { identity } from '@/i18n/es/identity';

export function SessionUnavailable() {
  const { retry } = useSession();
  return (
    <View className="flex-1 items-center justify-center gap-4 bg-background p-6">
      <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
        {`⚠ ${identity.sessionUnavailable.message}`}
      </Text>
      <Button accessibilityLabel={identity.sessionUnavailable.retry} onPress={retry}>
        <Text>{identity.sessionUnavailable.retry}</Text>
      </Button>
    </View>
  );
}
