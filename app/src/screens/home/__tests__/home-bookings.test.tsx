import { fireEvent, screen } from '@testing-library/react-native';
import { Home } from '..';
import { homeText } from '@/i18n/es/home';
import { renderWithSession } from '@/screens/auth/support/test-support';

function home() {
  const onRequestBooking = jest.fn();
  const onOpenMyRequests = jest.fn();
  const onOpenOccupancy = jest.fn();
  const onOpenPendingRequests = jest.fn();
  const element = (
    <Home
      onOpenUsers={jest.fn()}
      onOpenRooms={jest.fn()}
      onRequestBooking={onRequestBooking}
      onOpenMyRequests={onOpenMyRequests}
      onOpenOccupancy={onOpenOccupancy}
      onOpenPendingRequests={onOpenPendingRequests}
      onCreateEvent={jest.fn()}
      onPasswordChange={jest.fn()}
    />
  );
  return { element, onRequestBooking, onOpenMyRequests, onOpenOccupancy, onOpenPendingRequests };
}

describe('home booking requests', () => {
  it.each(['MODERATOR', 'ADMINISTRATOR'] as const)('links %s to the room occupancy', async (role) => {
    const { element, onOpenOccupancy } = home();
    await renderWithSession(element, { role });

    await fireEvent.press(await screen.findByRole('button', { name: homeText.viewOccupancy }));

    expect(onOpenOccupancy).toHaveBeenCalledTimes(1);
  });

  it.each(['MODERATOR', 'ADMINISTRATOR'] as const)('links %s to requesting a room and to the requests', async (role) => {
    const { element, onRequestBooking, onOpenMyRequests } = home();
    await renderWithSession(element, { role });

    await fireEvent.press(await screen.findByRole('button', { name: homeText.requestBooking }));
    await fireEvent.press(screen.getByRole('button', { name: homeText.myRequests }));

    expect(onRequestBooking).toHaveBeenCalledTimes(1);
    expect(onOpenMyRequests).toHaveBeenCalledTimes(1);
  });

  it('links an administrator to the pending requests', async () => {
    const { element, onOpenPendingRequests } = home();
    await renderWithSession(element, { role: 'ADMINISTRATOR' });

    await fireEvent.press(await screen.findByRole('button', { name: homeText.pendingRequests }));

    expect(onOpenPendingRequests).toHaveBeenCalledTimes(1);
  });

  it('does not show the pending requests to a moderator', async () => {
    const { element } = home();
    await renderWithSession(element, { role: 'MODERATOR' });

    await screen.findByRole('button', { name: homeText.myRequests });
    expect(screen.queryByRole('button', { name: homeText.pendingRequests })).not.toBeOnTheScreen();
  });

  it('does not show the booking requests to a registered user', async () => {
    const { element } = home();
    await renderWithSession(element, { role: 'REGISTERED' });

    await screen.findByRole('button', { name: homeText.changePassword });
    expect(screen.queryByRole('button', { name: homeText.requestBooking })).not.toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: homeText.myRequests })).not.toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: homeText.viewOccupancy })).not.toBeOnTheScreen();
  });
});
