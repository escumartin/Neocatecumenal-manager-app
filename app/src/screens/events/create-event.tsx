import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import { useCreateEvent } from '@/hooks/events/use-create-event';
import { useRooms } from '@/hooks/rooms/use-rooms';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { eventsText, messageForEventFailure } from '@/i18n/es/events';
import { eventsApi } from '@/lib/api';
import { firstInvalidSlotField } from '@/lib/bookings/validation';
import type { AgendaEvent, EventType, EventsApi } from '@/lib/api/events-api';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { RoomsApi } from '@/lib/api/rooms-api';
import { FieldProblem } from '@/screens/bookings/field-problem';
import { fieldHint } from '@/screens/bookings/field-hint';
import { CommunityPicker } from '@/screens/users/community-picker';

type Field = 'title' | 'date' | 'startTime' | 'endTime';
type Problem = { field: Field | null; message: string };

const TITLE_MAX_LENGTH = 120;
const EVENT_TYPES: EventType[] = ['WORD', 'CONVIVENCE'];

type Props = {
  api?: EventsApi;
  roomsApi?: RoomsApi;
  communitiesApi?: CommunitiesApi;
  onCreated?: (event: AgendaEvent) => void;
};

export function CreateEventScreen({ api = eventsApi, roomsApi, communitiesApi, onCreated }: Props) {
  const { user, isModerator, canChooseCommunity } = useCurrentUser();
  const { status, rooms, reload } = useRooms(roomsApi, isModerator);
  const { submitting, create } = useCreateEvent(api);
  const [type, setType] = useState<EventType | null>(null);
  const [title, setTitle] = useState('');
  const [roomId, setRoomId] = useState<string | null>(null);
  const [communityId, setCommunityId] = useState<string | null>(null);
  const [date, setDate] = useState('');
  const [startTime, setStartTime] = useState('');
  const [endTime, setEndTime] = useState('');
  const [problem, setProblem] = useState<Problem | null>(null);
  const [created, setCreated] = useState<AgendaEvent | null>(null);

  if (user === null) {
    return (
      <View className="flex-1 bg-background p-6">
        <Text role="status">{eventsText.create.loadingRooms}</Text>
      </View>
    );
  }

  if (!isModerator) {
    return (
      <View className="flex-1 bg-background p-6">
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${eventsText.forbidden}`}
        </Text>
      </View>
    );
  }

  const problemOf = (field: Field) => (problem?.field === field ? problem.message : null);

  function validationProblem(): Problem | null {
    if (type === null) {
      return { field: null, message: eventsText.fieldErrors.TYPE_REQUIRED };
    }
    if (title.trim().length === 0) {
      return { field: 'title', message: eventsText.fieldErrors.TITLE_REQUIRED };
    }
    if (roomId === null) {
      return { field: null, message: eventsText.fieldErrors.ROOM_REQUIRED };
    }
    if (canChooseCommunity && communityId === null) {
      return { field: null, message: eventsText.fieldErrors.COMMUNITY_REQUIRED };
    }
    const slotField = firstInvalidSlotField({ date, startTime, endTime });
    if (slotField !== null) {
      return { field: slotField, message: eventsText.fieldErrors.INVALID_FORMAT };
    }
    return null;
  }

  async function submit() {
    const invalid = validationProblem();
    setCreated(null);
    if (invalid !== null || type === null || roomId === null) {
      setProblem(invalid);
      return;
    }
    setProblem(null);
    try {
      const result = await create({
        type,
        title: title.trim(),
        roomId,
        date: date.trim(),
        startTime: startTime.trim(),
        endTime: endTime.trim(),
        ...(canChooseCommunity && communityId ? { communityId } : {}),
      });
      if (result.ok) {
        setCreated(result.event);
        onCreated?.(result.event);
      } else {
        setProblem({ field: null, message: messageForEventFailure(result) });
      }
    } catch {
      setProblem({ field: null, message: eventsText.errors.UNKNOWN });
    }
  }

  return (
    <ScrollView
      className="flex-1 bg-background"
      contentContainerClassName="gap-6 p-6"
      keyboardShouldPersistTaps="handled"
    >
      <Text variant="title">{eventsText.create.title}</Text>
      <View role="radiogroup" accessibilityLabel={eventsText.create.type} className="gap-3">
        <Text variant="heading">{eventsText.create.type}</Text>
        {EVENT_TYPES.map((option) => {
          const selected = option === type;
          const label = eventsText.types[option];
          return (
            <Button
              key={option}
              role="radio"
              variant={selected ? 'default' : 'outline'}
              accessibilityLabel={label}
              aria-checked={selected}
              accessibilityState={{ checked: selected }}
              onPress={() => setType(option)}
            >
              <Text>{selected ? `✓ ${label}` : label}</Text>
            </Button>
          );
        })}
      </View>
      <View className="gap-2">
        <Text variant="strong">{eventsText.create.eventTitle}</Text>
        <Text>{eventsText.create.titleWarning}</Text>
        <Input
          accessibilityLabel={eventsText.create.eventTitle}
          accessibilityHint={fieldHint(eventsText.create.titleWarning, problemOf('title'))}
          invalid={problemOf('title') !== null}
          maxLength={TITLE_MAX_LENGTH}
          value={title}
          onChangeText={setTitle}
        />
        <FieldProblem message={problemOf('title')} />
      </View>
      <View role="radiogroup" accessibilityLabel={eventsText.create.room} className="gap-3">
        <Text variant="heading">{eventsText.create.room}</Text>
        {status === 'loading' ? <Text role="status">{eventsText.create.loadingRooms}</Text> : null}
        {status === 'error' ? (
          <View className="gap-3">
            <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
              {`⚠ ${eventsText.create.roomsError}`}
            </Text>
            <Button variant="outline" accessibilityLabel={eventsText.create.retry} onPress={() => void reload()}>
              <Text>{eventsText.create.retry}</Text>
            </Button>
          </View>
        ) : null}
        {status === 'ready' && rooms.length === 0 ? <Text>{eventsText.create.noRooms}</Text> : null}
        {rooms.map((room) => {
          const selected = room.id === roomId;
          return (
            <Button
              key={room.id}
              role="radio"
              variant={selected ? 'default' : 'outline'}
              accessibilityLabel={room.name}
              aria-checked={selected}
              accessibilityState={{ checked: selected }}
              onPress={() => setRoomId(room.id)}
            >
              <Text>{selected ? `✓ ${room.name}` : room.name}</Text>
            </Button>
          );
        })}
      </View>
      <CommunityPicker selectedId={communityId} onSelect={setCommunityId} api={communitiesApi} />
      <View className="gap-2">
        <Text variant="strong">{eventsText.create.date}</Text>
        <Input
          accessibilityLabel={eventsText.create.date}
          accessibilityHint={problemOf('date') ?? undefined}
          invalid={problemOf('date') !== null}
          keyboardType="numbers-and-punctuation"
          maxLength={10}
          value={date}
          onChangeText={setDate}
        />
        <FieldProblem message={problemOf('date')} />
      </View>
      <View className="gap-2">
        <Text variant="strong">{eventsText.create.startTime}</Text>
        <Input
          accessibilityLabel={eventsText.create.startTime}
          accessibilityHint={problemOf('startTime') ?? undefined}
          invalid={problemOf('startTime') !== null}
          keyboardType="numbers-and-punctuation"
          maxLength={5}
          value={startTime}
          onChangeText={setStartTime}
        />
        <FieldProblem message={problemOf('startTime')} />
      </View>
      <View className="gap-2">
        <Text variant="strong">{eventsText.create.endTime}</Text>
        <Input
          accessibilityLabel={eventsText.create.endTime}
          accessibilityHint={problemOf('endTime') ?? undefined}
          invalid={problemOf('endTime') !== null}
          keyboardType="numbers-and-punctuation"
          maxLength={5}
          value={endTime}
          onChangeText={setEndTime}
        />
        <FieldProblem message={problemOf('endTime')} />
      </View>
      <FieldProblem message={problem?.field === null ? problem.message : null} />
      {created ? <Text role="status">{eventsText.create.sent(created.bookingStatus)}</Text> : null}
      <Button accessibilityLabel={eventsText.create.submit} disabled={submitting} onPress={() => void submit()}>
        <Text>{submitting ? eventsText.create.submitting : eventsText.create.submit}</Text>
      </Button>
    </ScrollView>
  );
}
