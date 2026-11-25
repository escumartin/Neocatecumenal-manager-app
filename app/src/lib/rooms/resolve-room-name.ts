import type { RoomsStatus } from '@/hooks/rooms/use-rooms';

type Labels = { loading: string; unavailable: string; deactivated: string };

export function resolveRoomName(
  status: RoomsStatus,
  names: ReadonlyMap<string, string>,
  roomId: string,
  labels: Labels,
): string {
  const name = names.get(roomId);
  if (name !== undefined) {
    return name;
  }
  if (status === 'error') {
    return labels.unavailable;
  }
  if (status === 'ready') {
    return labels.deactivated;
  }
  return labels.loading;
}
