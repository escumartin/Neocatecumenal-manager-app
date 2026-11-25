import { fireEvent, screen } from '@testing-library/react-native';
import { Home } from '..';
import { homeText } from '@/i18n/es/home';
import { renderWithSession } from '@/screens/auth/support/test-support';

function home() {
  const onCreateEvent = jest.fn();
  const element = (
    <Home
      onOpenUsers={jest.fn()}
      onOpenRooms={jest.fn()}
      onRequestBooking={jest.fn()}
      onOpenMyRequests={jest.fn()}
      onOpenOccupancy={jest.fn()}
      onOpenPendingRequests={jest.fn()}
      onCreateEvent={onCreateEvent}
      onPasswordChange={jest.fn()}
    />
  );
  return { element, onCreateEvent };
}

describe('home events', () => {
  it.each(['MODERATOR', 'ADMINISTRATOR'] as const)('links %s to creating an event', async (role) => {
    const { element, onCreateEvent } = home();
    await renderWithSession(element, { role });

    await fireEvent.press(await screen.findByRole('button', { name: homeText.createEvent }));

    expect(onCreateEvent).toHaveBeenCalledTimes(1);
  });

  it('does not show creating an event to a registered user', async () => {
    const { element } = home();
    await renderWithSession(element, { role: 'REGISTERED' });

    await screen.findByRole('button', { name: homeText.changePassword });
    expect(screen.queryByRole('button', { name: homeText.createEvent })).not.toBeOnTheScreen();
  });
});
