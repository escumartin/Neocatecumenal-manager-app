import type { Me } from '@/lib/api/session-api';

export type TabName = 'agenda' | 'reservas' | 'gestion' | 'cuenta';

export function navigationFor(role: Me['role'] | undefined) {
  const isAdministrator = role === 'ADMINISTRATOR';
  const isModerator = role === 'MODERATOR' || isAdministrator;
  const tabs: TabName[] = isModerator ? ['agenda', 'reservas', 'gestion', 'cuenta'] : ['cuenta'];
  return {
    tabs,
    firstTab: tabs[0],
    canCreateEvent: isModerator,
    canRequestBooking: isModerator,
    canManageUsers: isModerator,
    canManageMembers: isModerator,
    canReviewPending: isAdministrator,
    canManageRooms: isAdministrator,
    canManageCommunities: isAdministrator,
  };
}
