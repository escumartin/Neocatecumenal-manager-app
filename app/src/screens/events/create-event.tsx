import { useState } from 'react';
import { Keyboard, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { DateField } from '@/components/ui/date-field';
import { FormField } from '@/components/ui/form-field';
import { FormScroll } from '@/components/ui/form-scroll';
import { Input } from '@/components/ui/input';
import { ListSection } from '@/components/ui/list-section';
import { RadioSection } from '@/components/ui/radio-section';
import { Text } from '@/components/ui/text';
import { useCreateEvent } from '@/hooks/events/use-create-event';
import { useRooms } from '@/hooks/rooms/use-rooms';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { eventsText, messageForEventFailure } from '@/i18n/es/events';
import { eventsApi } from '@/lib/api';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { startOfDay } from '@/lib/bookings/date-value';
import { firstInvalidSlotField } from '@/lib/bookings/validation';
import type { AgendaEvent, EventType, EventsApi } from '@/lib/api/events-api';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { RoomsApi } from '@/lib/api/rooms-api';
import { FieldProblem } from '@/screens/bookings/field-problem';
import { RoomChoice } from '@/screens/rooms/room-choice';
import { fieldHint } from '@/screens/bookings/field-hint';
import { CommunityPicker } from '@/screens/users/community-picker';

type Field = 'title' | 'date' | 'startTime' | 'endTime';
type Problem = { field: Field | null; message: string };

const TITLE_MAX_LENGTH = 120;
const EVENT_TYPES: EventType[] = ['WORD', 'CONVIVENCE'];
const EVENT_TYPE_OPTIONS = EVENT_TYPES.map((value) => ({ value, label: eventsText.types[value] }));

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
      hapticFailure();
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
        hapticSuccess();
        onCreated?.(result.event);
      } else {
        setProblem({ field: null, message: messageForEventFailure(result) });
        hapticFailure();
      }
    } catch {
      setProblem({ field: null, message: eventsText.errors.UNKNOWN });
      hapticFailure();
    }
  }

  return (
    <FormScroll>
      <Text variant="title">{eventsText.create.title}</Text>
      <RadioSection title={eventsText.create.type} options={EVENT_TYPE_OPTIONS} value={type} onChange={setType} />
      <ListSection title={eventsText.create.eventTitle}>
        <FormField description={eventsText.create.titleWarning}>
          <Input
            accessibilityLabel={eventsText.create.eventTitle}
            accessibilityHint={fieldHint(eventsText.create.titleWarning, problemOf('title'))}
            invalid={problemOf('title') !== null}
            maxLength={TITLE_MAX_LENGTH}
            returnKeyType="done"
            onSubmitEditing={Keyboard.dismiss}
            value={title}
            onChangeText={setTitle}
          />
          <FieldProblem message={problemOf('title')} />
        </FormField>
      </ListSection>
      <RoomChoice
        title={eventsText.create.room}
        text={eventsText.create}
        status={status}
        rooms={rooms}
        selectedId={roomId}
        onSelect={setRoomId}
        onRetry={() => void reload()}
      />
      <CommunityPicker selectedId={communityId} onSelect={setCommunityId} api={communitiesApi} />
      <ListSection title={eventsText.create.when}>
        <FormField label={eventsText.create.date}>
          <DateField
            label={eventsText.create.date}
            mode="date"
            minimumDate={startOfDay(new Date())}
            value={date}
            hint={problemOf('date') ?? undefined}
            invalid={problemOf('date') !== null}
            onChange={setDate}
          />
          <FieldProblem message={problemOf('date')} />
        </FormField>
        <FormField label={eventsText.create.startTime}>
          <DateField
            label={eventsText.create.startTime}
            mode="time"
            value={startTime}
            hint={problemOf('startTime') ?? undefined}
            invalid={problemOf('startTime') !== null}
            onChange={setStartTime}
          />
          <FieldProblem message={problemOf('startTime')} />
        </FormField>
        <FormField label={eventsText.create.endTime}>
          <DateField
            label={eventsText.create.endTime}
            mode="time"
            value={endTime}
            hint={problemOf('endTime') ?? undefined}
            invalid={problemOf('endTime') !== null}
            onChange={setEndTime}
          />
          <FieldProblem message={problemOf('endTime')} />
        </FormField>
      </ListSection>
      <FieldProblem message={problem?.field === null ? problem.message : null} />
      {created ? <Text role="status">{eventsText.create.sent(created.bookingStatus)}</Text> : null}
      <Button accessibilityLabel={eventsText.create.submit} disabled={submitting} onPress={() => void submit()}>
        <Text>{submitting ? eventsText.create.submitting : eventsText.create.submit}</Text>
      </Button>
    </FormScroll>
  );
}
