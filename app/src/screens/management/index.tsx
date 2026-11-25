import { ScrollView } from 'react-native';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
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
      contentContainerClassName="p-4"
    >
      <ListSection>
        {canManageUsers ? (
          <ListRow title={navigationText.management.users} icon="person.2" onPress={onOpenUsers} />
        ) : null}
        {canManageRooms ? (
          <ListRow title={navigationText.management.rooms} icon="door.left.hand.open" onPress={onOpenRooms} />
        ) : null}
      </ListSection>
    </ScrollView>
  );
}
