import { ScrollView } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { homeText } from '@/i18n/es/home';

type Props = {
  onOpenUsers: () => void;
  onOpenRooms: () => void;
  onRequestBooking: () => void;
  onOpenMyRequests: () => void;
  onOpenOccupancy: () => void;
  onOpenPendingRequests: () => void;
  onCreateEvent: () => void;
  onPasswordChange: () => void;
};

export function Home({
  onOpenUsers,
  onOpenRooms,
  onRequestBooking,
  onOpenMyRequests,
  onOpenOccupancy,
  onOpenPendingRequests,
  onCreateEvent,
  onPasswordChange,
}: Props) {
  const { canManageUsers, isAdministrator, isModerator } = useCurrentUser();

  return (
    <ScrollView className="flex-1 bg-background" contentContainerClassName="flex-grow justify-center gap-4 p-6">
      <Text variant="display">{homeText.title}</Text>
      <Text variant="muted">{homeText.tagline}</Text>
      {canManageUsers ? (
        <Button variant="outline" accessibilityLabel={homeText.manageUsers} onPress={onOpenUsers}>
          <Text>{homeText.manageUsers}</Text>
        </Button>
      ) : null}
      {isModerator ? (
        <>
          <Button accessibilityLabel={homeText.requestBooking} onPress={onRequestBooking}>
            <Text>{homeText.requestBooking}</Text>
          </Button>
          <Button variant="outline" accessibilityLabel={homeText.myRequests} onPress={onOpenMyRequests}>
            <Text>{homeText.myRequests}</Text>
          </Button>
          <Button variant="outline" accessibilityLabel={homeText.viewOccupancy} onPress={onOpenOccupancy}>
            <Text>{homeText.viewOccupancy}</Text>
          </Button>
          <Button variant="outline" accessibilityLabel={homeText.createEvent} onPress={onCreateEvent}>
            <Text>{homeText.createEvent}</Text>
          </Button>
        </>
      ) : null}
      {isAdministrator ? (
        <>
          <Button variant="outline" accessibilityLabel={homeText.pendingRequests} onPress={onOpenPendingRequests}>
            <Text>{homeText.pendingRequests}</Text>
          </Button>
          <Button variant="outline" accessibilityLabel={homeText.manageRooms} onPress={onOpenRooms}>
            <Text>{homeText.manageRooms}</Text>
          </Button>
        </>
      ) : null}
      <Button variant="outline" accessibilityLabel={homeText.changePassword} onPress={onPasswordChange}>
        <Text>{homeText.changePassword}</Text>
      </Button>
    </ScrollView>
  );
}
