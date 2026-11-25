import { useState } from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { DateField } from '@/components/ui/date-field';
import { FormField } from '@/components/ui/form-field';
import { FormScroll } from '@/components/ui/form-scroll';
import { ListSection } from '@/components/ui/list-section';
import { Text } from '@/components/ui/text';
import { useRoomOccupancy } from '@/hooks/rooms/use-room-occupancy';
import { useRooms } from '@/hooks/rooms/use-rooms';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { bookingsText, messageForBookingFailure } from '@/i18n/es/bookings';
import { useCreateBookingRequest } from '@/hooks/bookings/use-create-booking-request';
import { bookingRequestsApi } from '@/lib/api';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import type { BookingRequestsApi } from '@/lib/api/booking-requests-api';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { RoomsApi } from '@/lib/api/rooms-api';
import { startOfDay } from '@/lib/bookings/date-value';
import { firstInvalidSlotField, isDate } from '@/lib/bookings/validation';
import { OccupiedSlots } from '@/screens/rooms/occupied-slots';
import { RoomChoice } from '@/screens/rooms/room-choice';
import { CommunityPicker } from '@/screens/users/community-picker';
import { FieldProblem } from './field-problem';
import { ReasonField } from './reason-field';
import { RecurrenceFields } from './recurrence-fields';

type Field = 'date' | 'startTime' | 'endTime' | 'reason' | 'until';
type Problem = { field: Field | null; message: string };

type Props = {
  api?: BookingRequestsApi;
  roomsApi?: RoomsApi;
  communitiesApi?: CommunitiesApi;
  onCreated?: () => void;
};

export function RequestBookingScreen({ api = bookingRequestsApi, roomsApi, communitiesApi, onCreated }: Props) {
  const { user, isModerator, canChooseCommunity } = useCurrentUser();
  const { status, rooms, reload } = useRooms(roomsApi, isModerator);
  const [roomId, setRoomId] = useState<string | null>(null);
  const [communityId, setCommunityId] = useState<string | null>(null);
  const [date, setDate] = useState('');
  const [startTime, setStartTime] = useState('');
  const [endTime, setEndTime] = useState('');
  const [reason, setReason] = useState('');
  const [repeats, setRepeats] = useState(false);
  const [until, setUntil] = useState('');
  const [problem, setProblem] = useState<Problem | null>(null);
  const [sent, setSent] = useState(false);
  const { submitting, create } = useCreateBookingRequest(api);

  const day = date.trim();
  const occupancyQuery =
    isModerator && roomId !== null && isDate(day) && !(canChooseCommunity && communityId === null)
      ? {
          roomId,
          date: day,
          ...(canChooseCommunity && communityId ? { communityId } : {}),
        }
      : null;
  const occupancy = useRoomOccupancy(roomsApi, occupancyQuery);

  if (user === null) {
    return (
      <View className="flex-1 bg-background p-6">
        <Text role="status">{bookingsText.request.loadingRooms}</Text>
      </View>
    );
  }

  if (!isModerator) {
    return (
      <View className="flex-1 bg-background p-6">
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${bookingsText.forbidden}`}
        </Text>
      </View>
    );
  }

  function validationProblem(): Problem | null {
    if (roomId === null) {
      return { field: null, message: bookingsText.fieldErrors.ROOM_REQUIRED };
    }
    if (canChooseCommunity && communityId === null) {
      return { field: null, message: bookingsText.fieldErrors.COMMUNITY_REQUIRED };
    }
    const slotField = firstInvalidSlotField({ date, startTime, endTime });
    if (slotField !== null) {
      return { field: slotField, message: bookingsText.fieldErrors.INVALID_FORMAT };
    }
    if (reason.trim().length === 0) {
      return { field: 'reason', message: bookingsText.fieldErrors.REQUIRED };
    }
    if (repeats && until.trim().length === 0) {
      return { field: 'until', message: bookingsText.recurrence.fieldErrors.REQUIRED };
    }
    if (repeats && !isDate(until)) {
      return { field: 'until', message: bookingsText.recurrence.fieldErrors.INVALID_FORMAT };
    }
    return null;
  }

  const problemOf = (field: Field) => (problem?.field === field ? problem.message : null);

  async function submit() {
    const invalid = validationProblem();
    setSent(false);
    if (invalid !== null || roomId === null) {
      setProblem(invalid);
      hapticFailure();
      return;
    }
    setProblem(null);
    try {
      const result = await create({
        roomId,
        date: date.trim(),
        startTime: startTime.trim(),
        endTime: endTime.trim(),
        reason: reason.trim(),
        ...(repeats ? { recurrence: { frequency: 'WEEKLY' as const, until: until.trim() } } : {}),
        ...(canChooseCommunity && communityId ? { communityId } : {}),
      });
      if (result.ok) {
        setSent(true);
        hapticSuccess();
        onCreated?.();
      } else {
        setProblem({ field: null, message: messageForBookingFailure(result) });
        hapticFailure();
        if (result.code === 'ROOM_OCCUPIED') {
          void occupancy.reload();
        }
      }
    } catch {
      setProblem({ field: null, message: bookingsText.errors.UNKNOWN });
      hapticFailure();
    }
  }

  return (
    <FormScroll>
      <Text variant="title">{bookingsText.request.title}</Text>
      <RoomChoice
        title={bookingsText.request.room}
        text={bookingsText.request}
        status={status}
        rooms={rooms}
        selectedId={roomId}
        onSelect={setRoomId}
        onRetry={() => void reload()}
      />
      <CommunityPicker selectedId={communityId} onSelect={setCommunityId} api={communitiesApi} />
      <ListSection title={bookingsText.request.when}>
        <FormField label={bookingsText.request.date}>
          <DateField
            label={bookingsText.request.date}
            mode="date"
            minimumDate={startOfDay(new Date())}
            value={date}
            hint={problemOf('date') ?? undefined}
            invalid={problemOf('date') !== null}
            onChange={setDate}
          />
          <FieldProblem message={problemOf('date')} />
        </FormField>
        <FormField label={bookingsText.request.startTime}>
          <DateField
            label={bookingsText.request.startTime}
            mode="time"
            value={startTime}
            hint={problemOf('startTime') ?? undefined}
            invalid={problemOf('startTime') !== null}
            onChange={setStartTime}
          />
          <FieldProblem message={problemOf('startTime')} />
        </FormField>
        <FormField label={bookingsText.request.endTime}>
          <DateField
            label={bookingsText.request.endTime}
            mode="time"
            value={endTime}
            hint={problemOf('endTime') ?? undefined}
            invalid={problemOf('endTime') !== null}
            onChange={setEndTime}
          />
          <FieldProblem message={problemOf('endTime')} />
        </FormField>
      </ListSection>
      <RecurrenceFields
        enabled={repeats}
        until={until}
        firstDate={day}
        invalid={problemOf('until') !== null}
        problem={problemOf('until')}
        onToggle={setRepeats}
        onChangeUntil={setUntil}
      />
      {occupancyQuery ? (
        <View className="gap-3">
          <Text variant="heading">{bookingsText.occupancy.listLabel}</Text>
          <OccupiedSlots
            status={occupancy.status}
            occupied={occupancy.occupied}
            onReload={() => void occupancy.reload()}
          />
        </View>
      ) : null}
      <ListSection title={bookingsText.request.reason}>
        <FormField>
          <ReasonField
            showLabel={false}
            label={bookingsText.request.reason}
            value={reason}
            invalid={problemOf('reason') !== null}
            problem={problemOf('reason')}
            onChangeText={setReason}
          />
        </FormField>
      </ListSection>
      <FieldProblem message={problem?.field === null ? problem.message : null} />
      {sent ? <Text role="status">{bookingsText.request.sent}</Text> : null}
      <Button accessibilityLabel={bookingsText.request.submit} disabled={submitting} onPress={() => void submit()}>
        <Text>{submitting ? bookingsText.request.submitting : bookingsText.request.submit}</Text>
      </Button>
    </FormScroll>
  );
}
