import { fireEvent, screen, waitFor } from '@testing-library/react-native';
import { ManagementMenu } from '@/screens/management';
import { navigationText } from '@/i18n/es/navigation';
import { renderWithSession } from '@/screens/auth/support/test-support';

const noop = {
  onOpenUsers: jest.fn(),
  onOpenRooms: jest.fn(),
  onOpenCommunities: jest.fn(),
  onOpenMembers: jest.fn(),
};

describe('management menu', () => {
  it('links an administrator to users, rooms and communities', async () => {
    const onOpenUsers = jest.fn();
    const onOpenRooms = jest.fn();
    const onOpenCommunities = jest.fn();
    const onOpenMembers = jest.fn();
    await renderWithSession(<ManagementMenu
        onOpenUsers={onOpenUsers}
        onOpenRooms={onOpenRooms}
        onOpenCommunities={onOpenCommunities}
        onOpenMembers={onOpenMembers}
      />, {
      role: 'ADMINISTRATOR',
    });

    await fireEvent.press(await screen.findByRole('button', { name: navigationText.management.users }));
    await fireEvent.press(screen.getByRole('button', { name: navigationText.management.rooms }));

    expect(onOpenUsers).toHaveBeenCalledTimes(1);
    await fireEvent.press(screen.getByRole('button', { name: navigationText.management.communities }));

    expect(onOpenRooms).toHaveBeenCalledTimes(1);
    expect(onOpenCommunities).toHaveBeenCalledTimes(1);

    await fireEvent.press(screen.getByRole('button', { name: navigationText.management.members }));

    expect(onOpenMembers).toHaveBeenCalledTimes(1);
  });

  it('shows the members row to a moderator', async () => {
    await renderWithSession(<ManagementMenu {...noop} />, { role: 'MODERATOR' });

    expect(await screen.findByRole('button', { name: navigationText.management.members })).toBeOnTheScreen();
  });

  it('does not show the members row to a registered user', async () => {
    const { api } = await renderWithSession(<ManagementMenu {...noop} />, { role: 'REGISTERED' });

    await waitFor(() => expect(api.getMe).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: navigationText.management.members })).not.toBeOnTheScreen();
  });

  it('does not show the rooms to a moderator', async () => {
    await renderWithSession(<ManagementMenu {...noop} />, { role: 'MODERATOR' });

    await screen.findByRole('button', { name: navigationText.management.users });
    expect(screen.queryByRole('button', { name: navigationText.management.rooms })).not.toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: navigationText.management.communities })).not.toBeOnTheScreen();
  });

  it('presents the destinations as one grouped list of rows with a chevron each', async () => {
    await renderWithSession(<ManagementMenu {...noop} />, {
      role: 'ADMINISTRATOR',
    });

    await screen.findByRole('button', { name: navigationText.management.users });

    expect(screen.getAllByTestId('list-section-card')).toHaveLength(1);
    expect(screen.getAllByTestId('list-row-chevron')).toHaveLength(4);
    expect(screen.getAllByTestId('list-row-icon')).toHaveLength(4);
  });
});
