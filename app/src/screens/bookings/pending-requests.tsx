import { useState } from 'react';
import { FlatList, RefreshControl, View } from 'react-native';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { usePullToRefresh } from '@/hooks/navigation/use-pull-to-refresh';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { usePendingRequests } from '@/hooks/bookings/use-pending-requests';
import { useCommunities } from '@/hooks/communities/use-communities';
import { useRooms } from '@/hooks/rooms/use-rooms';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { bookingsText } from '@/i18n/es/bookings';
import { resolveRoomName } from '@/lib/rooms/resolve-room-name';
import type { BookingRequestResult, BookingRequestsApi } from '@/lib/api/booking-requests-api';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { RoomsApi } from '@/lib/api/rooms-api';
import { PendingRequestCard } from './pending-request-card';

type Props = {
  api?: BookingRequestsApi;
  roomsApi?: RoomsApi;
  communitiesApi?: CommunitiesApi;
};

export function PendingRequestsScreen({ api, roomsApi, communitiesApi }: Props) {
  const { user, isAdministrator } = useCurrentUser();
  const { status, requests, reload, refresh, approve, reject } = usePendingRequests(api, isAdministrator);
  const { status: roomsStatus, rooms, reload: reloadRooms } = useRooms(roomsApi, isAdministrator);
  const { communities } = useCommunities(communitiesApi, isAdministrator, {
    includeDeactivated: isAdministrator,
  });
  useReloadOnFocus(refresh);
  const { refreshing, onRefresh } = usePullToRefresh(() => Promise.all([reload(), reloadRooms()]));

  const [notice, setNotice] = useState<string | null>(null);

  async function track(action: Promise<BookingRequestResult>) {
    const result = await action;
    const resolvedMeanwhile = !result.ok && result.code === 'BOOKING_REQUEST_ALREADY_RESOLVED';
    setNotice(resolvedMeanwhile ? bookingsText.resolve.alreadyResolved : null);
    return result;
  }

  if (user === null) {
    return (
      <View className="flex-1 bg-background p-4">
        <StateView kind="loading" message={bookingsText.resolve.loading} />
      </View>
    );
  }

  if (!isAdministrator) {
    return (
      <View className="flex-1 bg-background p-4">
        <StateView kind="error" message={bookingsText.resolve.forbidden} />
      </View>
    );
  }

  const roomNames = new Map(rooms.map((room) => [room.id, room.name]));
  const roomLabels = {
    loading: bookingsText.list.roomLoading,
    unavailable: bookingsText.list.roomUnavailable,
    deactivated: bookingsText.list.unknownRoom,
  };
  const communityNumbers = new Map(communities.map((community) => [community.id, community.number]));

  const header = (
    <View className="gap-4">
      {notice ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
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
      {status === 'loading' && !refreshing ? <StateView kind="loading" message={bookingsText.resolve.loading} /> : null}
      {status === 'error' ? (
        <StateView
          kind="error"
          message={bookingsText.resolve.loadError}
          retryLabel={bookingsText.resolve.retry}
          onRetry={() => void reload()}
        />
      ) : null}
    </View>
  );

  return (
    <FlatList
      testID="pending-requests-list"
      className="flex-1 bg-background"
      contentContainerClassName="gap-4 p-4"
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void onRefresh()} />}
      contentInsetAdjustmentBehavior="automatic"
      keyboardShouldPersistTaps="handled"
      data={requests}
      keyExtractor={(request) => request.id}
      ListHeaderComponent={header}
      ListEmptyComponent={
        status === 'ready' ? <StateView kind="empty" icon="tray" message={bookingsText.resolve.empty} /> : undefined
      }
      renderItem={({ item: request }) => (
        <PendingRequestCard
          request={request}
          roomName={resolveRoomName(roomsStatus, roomNames, request.roomId, roomLabels)}
          communityNumber={communityNumbers.get(request.communityId)}
          onApprove={(id) => track(approve(id))}
          onReject={(id, reason) => track(reject(id, reason))}
        />
      )}
    />
  );
}
