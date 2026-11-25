import { fireEvent, screen } from '@testing-library/react-native';
import { Home } from '..';
import { homeText } from '@/i18n/es/home';
import { renderWithSession } from '@/screens/auth/support/test-support';

function home() {
  const onOpenRooms = jest.fn();
  const element = (
    <Home
      onOpenUsers={jest.fn()}
      onOpenRooms={onOpenRooms}
      onRequestBooking={jest.fn()}
      onOpenMyRequests={jest.fn()}
      onOpenOccupancy={jest.fn()}
      onOpenPendingRequests={jest.fn()}
      onCreateEvent={jest.fn()}
      onPasswordChange={jest.fn()}
    />
  );
  return { element, onOpenRooms };
}

describe('home room management', () => {
  it('links an administrator to the room management', async () => {
    const { element, onOpenRooms } = home();
    await renderWithSession(element, { role: 'ADMINISTRATOR' });

    await fireEvent.press(await screen.findByRole('button', { name: homeText.manageRooms }));

    expect(onOpenRooms).toHaveBeenCalledTimes(1);
  });

  it.each(['MODERATOR', 'REGISTERED'] as const)('does not show the room management to %s', async (role) => {
    const { element } = home();
    await renderWithSession(element, { role });

    await screen.findByRole('button', { name: homeText.changePassword });
    expect(screen.queryByRole('button', { name: homeText.manageRooms })).not.toBeOnTheScreen();
  });
});
