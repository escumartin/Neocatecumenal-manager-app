import { fireEvent, screen } from '@testing-library/react-native';
import { ManagementMenu } from '@/screens/management';
import { navigationText } from '@/i18n/es/navigation';
import { renderWithSession } from '@/screens/auth/support/test-support';

describe('management menu', () => {
  it('links an administrator to users and rooms', async () => {
    const onOpenUsers = jest.fn();
    const onOpenRooms = jest.fn();
    await renderWithSession(<ManagementMenu onOpenUsers={onOpenUsers} onOpenRooms={onOpenRooms} />, {
      role: 'ADMINISTRATOR',
    });

    await fireEvent.press(await screen.findByRole('button', { name: navigationText.management.users }));
    await fireEvent.press(screen.getByRole('button', { name: navigationText.management.rooms }));

    expect(onOpenUsers).toHaveBeenCalledTimes(1);
    expect(onOpenRooms).toHaveBeenCalledTimes(1);
  });

  it('does not show the rooms to a moderator', async () => {
    await renderWithSession(<ManagementMenu onOpenUsers={jest.fn()} onOpenRooms={jest.fn()} />, { role: 'MODERATOR' });

    await screen.findByRole('button', { name: navigationText.management.users });
    expect(screen.queryByRole('button', { name: navigationText.management.rooms })).not.toBeOnTheScreen();
  });

  it('presents the destinations as one grouped list of rows with a chevron each', async () => {
    await renderWithSession(<ManagementMenu onOpenUsers={jest.fn()} onOpenRooms={jest.fn()} />, {
      role: 'ADMINISTRATOR',
    });

    await screen.findByRole('button', { name: navigationText.management.users });

    expect(screen.getAllByTestId('list-section-card')).toHaveLength(1);
    expect(screen.getAllByTestId('list-row-chevron')).toHaveLength(2);
    expect(screen.getAllByTestId('list-row-icon')).toHaveLength(2);
  });
});
