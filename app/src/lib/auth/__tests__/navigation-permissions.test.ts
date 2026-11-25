import { navigationFor } from '../navigation-permissions';

describe('navigation permissions', () => {
  it('shows only the account tab to a registered user', () => {
    expect(navigationFor('REGISTERED').tabs).toEqual(['cuenta']);
  });

  it('shows only the account tab while the role is unknown', () => {
    expect(navigationFor(undefined).tabs).toEqual(['cuenta']);
  });

  it.each(['MODERATOR', 'ADMINISTRATOR'] as const)('shows every tab to %s', (role) => {
    expect(navigationFor(role).tabs).toEqual(['agenda', 'reservas', 'gestion', 'cuenta']);
  });

  it('starts on the first visible tab', () => {
    expect(navigationFor('REGISTERED').firstTab).toBe('cuenta');
    expect(navigationFor('MODERATOR').firstTab).toBe('agenda');
  });

  it.each(['MODERATOR', 'ADMINISTRATOR'] as const)(
    'lets %s create events, request bookings and manage users',
    (role) => {
      const permissions = navigationFor(role);

      expect(permissions.canCreateEvent).toBe(true);
      expect(permissions.canRequestBooking).toBe(true);
      expect(permissions.canManageUsers).toBe(true);
    },
  );

  it('lets only an administrator review pending requests and manage rooms', () => {
    expect(navigationFor('ADMINISTRATOR')).toMatchObject({ canReviewPending: true, canManageRooms: true });
    expect(navigationFor('MODERATOR')).toMatchObject({ canReviewPending: false, canManageRooms: false });
  });

  it('lets only an administrator manage communities', () => {
    expect(navigationFor('ADMINISTRATOR').canManageCommunities).toBe(true);
    expect(navigationFor('MODERATOR').canManageCommunities).toBe(false);
    expect(navigationFor('REGISTERED').canManageCommunities).toBe(false);
    expect(navigationFor(undefined).canManageCommunities).toBe(false);
  });

  it('gives a registered user no capability beyond the account', () => {
    expect(navigationFor('REGISTERED')).toMatchObject({
      canCreateEvent: false,
      canRequestBooking: false,
      canManageUsers: false,
      canReviewPending: false,
      canManageRooms: false,
    });
  });
});

describe('member management permission', () => {
  it.each(['MODERATOR', 'ADMINISTRATOR'] as const)('lets %s manage members', (role) => {
    expect(navigationFor(role).canManageMembers).toBe(true);
  });

  it('does not let a registered or unknown user manage members', () => {
    expect(navigationFor('REGISTERED').canManageMembers).toBe(false);
    expect(navigationFor(undefined).canManageMembers).toBe(false);
  });
});
