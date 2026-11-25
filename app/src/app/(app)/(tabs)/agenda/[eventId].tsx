import { useLocalSearchParams } from 'expo-router';
import { EventScreen } from '@/screens/events/event-details';

export default function EventRoute() {
  const { eventId } = useLocalSearchParams<{ eventId: string }>();
  return <EventScreen eventId={eventId} />;
}
