import { ScrollView } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { useSession } from '@/hooks/session/use-session';
import { navigationText } from '@/i18n/es/navigation';
import { navigationFor } from '@/lib/auth/navigation-permissions';

type Props = {
  onOpenUsers: () => void;
  onOpenRooms: () => void;
};

export function ManagementMenu({ onOpenUsers, onOpenRooms }: Props) {
  const { me } = useSession();
  const { canManageUsers, canManageRooms } = navigationFor(me?.role);

  return (
    <ScrollView
      className="flex-1 bg-background"
      contentInsetAdjustmentBehavior="automatic"
      contentContainerClassName="gap-4 p-4"
    >
      {canManageUsers ? (
        <Button variant="outline" accessibilityLabel={navigationText.management.users} onPress={onOpenUsers}>
          <Text>{navigationText.management.users}</Text>
        </Button>
      ) : null}
      {canManageRooms ? (
        <Button variant="outline" accessibilityLabel={navigationText.management.rooms} onPress={onOpenRooms}>
          <Text>{navigationText.management.rooms}</Text>
        </Button>
      ) : null}
    </ScrollView>
  );
}
