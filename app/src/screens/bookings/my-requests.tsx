import { useState } from 'react';
import { FlatList, RefreshControl, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { usePullToRefresh } from '@/hooks/navigation/use-pull-to-refresh';
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
import { BookingStatusBadge } from './booking-status-badge';
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
  const { refreshing, onRefresh } = usePullToRefresh(() => Promise.all([reload(), reloadRooms()]));
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
      <View className="flex-1 bg-background p-4">
        <StateView kind="loading" message={bookingsText.list.loading} />
      </View>
    );
  }

  if (!isModerator) {
    return (
      <View className="flex-1 bg-background p-4">
        <StateView kind="error" message={bookingsText.forbidden} />
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
        <StateView
          kind="error"
          message={bookingsText.request.roomsError}
          retryLabel={bookingsText.request.retry}
          onRetry={() => void reloadRooms()}
        />
      ) : null}
      {status === 'loading' && !refreshing ? (
        <StateView kind="loading" message={bookingsText.list.loading} />
      ) : null}
      {status === 'error' ? (
        <StateView
          kind="error"
          message={bookingsText.list.loadError}
          retryLabel={bookingsText.list.retry}
          onRetry={() => void reload()}
        />
      ) : null}
    </View>
  );

  const empty =
    status === 'ready' ? (
      <StateView kind="empty" icon="tray" message={bookingsText.list.empty}>
        {onRequest ? (
          <Button
            accessibilityLabel={bookingsText.list.newRequest}
            onPress={onRequest}
          >
            <Text>{bookingsText.list.newRequest}</Text>
          </Button>
        ) : null}
      </StateView>
    ) : undefined;

  return (
    <>
      <FlatList
        testID="requests-list"
        className="flex-1 bg-background"
        contentContainerClassName="gap-4 p-4"
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => void onRefresh()}
          />
        }
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
            <View className="gap-2 rounded-lg border-hairline border-border bg-card p-4">
              <Text variant="heading">{roomName}</Text>
              <Text>{bookingsText.list.when(request)}</Text>
              <BookingStatusBadge status={request.status} />
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
