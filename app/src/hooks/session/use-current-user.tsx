import { useSession } from './use-session';

export function useCurrentUser() {
  const { status, me } = useSession();
  const role = me?.role;
  const isAdministrator = role === 'ADMINISTRATOR';
  const isModerator = role === 'MODERATOR' || isAdministrator;
  return {
    status,
    user: me,
    isModerator,
    isAdministrator,
    canManageUsers: isModerator,
    canChooseCommunity: isAdministrator,
  };
}
