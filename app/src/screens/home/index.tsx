import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { homeText } from '@/i18n/es/home';

type Props = {
  onOpenUsers: () => void;
  onPasswordChange: () => void;
};

export function Home({ onOpenUsers, onPasswordChange }: Props) {
  const { canManageUsers } = useCurrentUser();

  return (
    <View className="flex-1 justify-center gap-4 bg-background p-6">
      <Text variant="display">{homeText.title}</Text>
      <Text variant="muted">{homeText.tagline}</Text>
      {canManageUsers ? (
        <Button variant="outline" accessibilityLabel={homeText.manageUsers} onPress={onOpenUsers}>
          <Text>{homeText.manageUsers}</Text>
        </Button>
      ) : null}
      <Button variant="outline" accessibilityLabel={homeText.changePassword} onPress={onPasswordChange}>
        <Text>{homeText.changePassword}</Text>
      </Button>
    </View>
  );
}
