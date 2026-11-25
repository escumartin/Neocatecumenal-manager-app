import { useState } from 'react';
import { FlatList, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { useBookingRequests } from '@/hooks/bookings/use-booking-requests';
import { useRooms } from '@/hooks/rooms/use-rooms';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { bookingsText, messageForBookingFailure } from '@/i18n/es/bookings';
import { hasStarted } from '@/lib/bookings/has-started';
import { resolveRoomName } from '@/lib/rooms/resolve-room-name';
import type {
  BookingRequest,
  BookingRequestsApi,
} from '@/lib/api/booking-requests-api';
import type { RoomsApi } from '@/lib/api/rooms-api';
import {
  CancelConfirmation,
  type CancellableOccurrence,
} from './cancel-confirmation';
import { ReasonEditor } from './reason-editor';

type Cancelling = {
  request: BookingRequest;
  roomName: string;
  occurrences?: CancellableOccurrence[];
  busy: boolean;
  problem: string | null;
};

function canCancel(request: BookingRequest): boolean {
  const active = request.status === 'PENDING' || request.status === 'APPROVED';
  return (
    active &&
    (request.occurrenceCount > 1 ||
      !hasStarted(request.date, request.startTime))
  );
}

type Props = {
  api?: BookingRequestsApi;
  roomsApi?: RoomsApi;
  onRequest?: () => void;
};

export function MyRequestsScreen({ api, roomsApi, onRequest }: Props) {
  const { user, isModerator } = useCurrentUser();
  const {
    status,
    requests,
    reload,
    rectifyReason,
    cancel,
    cancelBooking,
    loadDetail,
  } = useBookingRequests(api, isModerator);
  const {
    status: roomsStatus,
    rooms,
    reload: reloadRooms,
  } = useRooms(roomsApi, isModerator);
  useReloadOnFocus(reload);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState<Cancelling | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function startCancelling(request: BookingRequest, roomName: string) {
    setNotice(null);
    if (request.occurrenceCount <= 1) {
      setCancelling({ request, roomName, busy: false, problem: null });
      return;
    }
    try {
      const detail = await loadDetail(request.id);
      if (!detail.ok) {
        setNotice(messageForBookingFailure(detail));
        return;
      }
      const occurrences = detail.detail.bookings.filter(
        (booking) =>
          (booking.status === 'PENDING' || booking.status === 'APPROVED') &&
          !hasStarted(booking.date, booking.startTime),
      );
      setCancelling({
        request,
        roomName,
        occurrences,
        busy: false,
        problem: null,
      });
    } catch {
      setNotice(bookingsText.errors.UNKNOWN);
    }
  }

  async function confirmCancelling(bookingId?: string) {
    if (!cancelling) {
      return;
    }
    setCancelling({ ...cancelling, busy: true, problem: null });
    try {
      const result = bookingId
        ? await cancelBooking(cancelling.request.id, bookingId)
        : await cancel(cancelling.request.id);
      setCancelling(
        result.ok
          ? null
          : {
              ...cancelling,
              busy: false,
              problem: messageForBookingFailure(result),
            },
      );
    } catch {
      setCancelling({
        ...cancelling,
        busy: false,
        problem: bookingsText.errors.UNKNOWN,
      });
    }
  }

  if (user === null) {
    return (
      <View className="flex-1 bg-background p-6">
        <Text role="status">{bookingsText.list.loading}</Text>
      </View>
    );
  }

  if (!isModerator) {
    return (
      <View className="flex-1 bg-background p-6">
        <Text
          role="alert"
          accessibilityLiveRegion="assertive"
          className="text-destructive"
        >
          {`⚠ ${bookingsText.forbidden}`}
        </Text>
      </View>
    );
  }

  const roomNames = new Map(rooms.map((room) => [room.id, room.name]));
  const roomLabels = {
    loading: bookingsText.list.roomLoading,
    unavailable: bookingsText.list.roomUnavailable,
    deactivated: bookingsText.list.unknownRoom,
  };

  const header = (
    <View className="gap-4">
      <Text variant="title">{bookingsText.list.title}</Text>
      {notice ? (
        <Text
          role="alert"
          accessibilityLiveRegion="assertive"
          className="text-destructive"
        >
          {`⚠ ${notice}`}
        </Text>
      ) : null}
      {roomsStatus === 'error' ? (
        <View className="gap-3">
          <Text
            role="alert"
            accessibilityLiveRegion="assertive"
            className="text-destructive"
          >
            {`⚠ ${bookingsText.request.roomsError}`}
          </Text>
          <Button
            variant="outline"
            accessibilityLabel={bookingsText.request.retry}
            onPress={() => void reloadRooms()}
          >
            <Text>{bookingsText.request.retry}</Text>
          </Button>
        </View>
      ) : null}
      {status === 'loading' ? (
        <Text role="status">{bookingsText.list.loading}</Text>
      ) : null}
      {status === 'error' ? (
        <View className="gap-3">
          <Text
            role="alert"
            accessibilityLiveRegion="assertive"
            className="text-destructive"
          >
            {`⚠ ${bookingsText.list.loadError}`}
          </Text>
          <Button
            variant="outline"
            accessibilityLabel={bookingsText.list.retry}
            onPress={() => void reload()}
          >
            <Text>{bookingsText.list.retry}</Text>
          </Button>
        </View>
      ) : null}
    </View>
  );

  const empty =
    status === 'ready' ? (
      <View className="gap-3">
        <Text>{bookingsText.list.empty}</Text>
        {onRequest ? (
          <Button
            accessibilityLabel={bookingsText.list.newRequest}
            onPress={onRequest}
          >
            <Text>{bookingsText.list.newRequest}</Text>
          </Button>
        ) : null}
      </View>
    ) : undefined;

  return (
    <>
      <FlatList
        className="flex-1 bg-background"
        contentContainerClassName="gap-4 p-6"
        contentInsetAdjustmentBehavior="automatic"
        keyboardShouldPersistTaps="handled"
        data={requests}
        keyExtractor={(request) => request.id}
        ListHeaderComponent={header}
        ListEmptyComponent={empty}
        renderItem={({ item: request }) => {
          const roomName = resolveRoomName(
            roomsStatus,
            roomNames,
            request.roomId,
            roomLabels,
          );
          return (
            <View className="gap-2 rounded-md border-2 border-input p-4">
              <Text variant="heading">{roomName}</Text>
              <Text>{bookingsText.list.when(request)}</Text>
              <Text variant="strong">
                {bookingsText.status[request.status]}
              </Text>
              {request.status === 'REJECTED' && request.rejectionReason ? (
                <Text>
                  {bookingsText.rejectionReasonLine(request.rejectionReason)}
                </Text>
              ) : null}
              {editingId === request.id ? (
                <ReasonEditor
                  initialReason={request.reason}
                  onSave={(reason) => rectifyReason(request.id, reason)}
                  onDone={() => setEditingId(null)}
                />
              ) : (
                <>
                  <Text>{request.reason}</Text>
                  {request.status === 'PENDING' ? (
                    <Button
                      variant="outline"
                      accessibilityLabel={bookingsText.rectify.actionFor(
                        roomName,
                      )}
                      onPress={() => setEditingId(request.id)}
                    >
                      <Text>{bookingsText.rectify.action}</Text>
                    </Button>
                  ) : null}
                </>
              )}
              {canCancel(request) ? (
                <Button
                  variant="outline"
                  accessibilityLabel={bookingsText.cancel.actionFor(roomName)}
                  onPress={() => void startCancelling(request, roomName)}
                >
                  <Text>{bookingsText.cancel.action}</Text>
                </Button>
              ) : null}
            </View>
          );
        }}
      />
      {cancelling ? (
        <CancelConfirmation
          roomName={cancelling.roomName}
          occurrences={cancelling.occurrences}
          busy={cancelling.busy}
          problem={cancelling.problem}
          onConfirm={(bookingId) => void confirmCancelling(bookingId)}
          onDismiss={() => setCancelling(null)}
        />
      ) : null}
    </>
  );
}
