import { fireEvent, screen } from '@testing-library/react-native';
import { Home } from '..';
import { homeText } from '@/i18n/es/home';
import { renderWithSession } from '@/screens/auth/support/test-support';

function home() {
  const onOpenUsers = jest.fn();
  const onPasswordChange = jest.fn();
  const element = (
    <Home
      onOpenUsers={onOpenUsers}
      onOpenRooms={jest.fn()}
      onRequestBooking={jest.fn()}
      onOpenMyRequests={jest.fn()}
      onOpenOccupancy={jest.fn()}
      onOpenPendingRequests={jest.fn()}
      onCreateEvent={jest.fn()}
      onPasswordChange={onPasswordChange}
    />
  );
  return { element, onOpenUsers, onPasswordChange };
}

describe('home', () => {
  it.each(['MODERATOR', 'ADMINISTRATOR'] as const)('links %s to the user management', async (role) => {
    const { element, onOpenUsers } = home();
    await renderWithSession(element, { role });

    await fireEvent.press(await screen.findByRole('button', { name: homeText.manageUsers }));

    expect(onOpenUsers).toHaveBeenCalledTimes(1);
  });

  it('does not show the user management to a registered user', async () => {
    const { element } = home();
    await renderWithSession(element, { role: 'REGISTERED' });

    await screen.findByRole('button', { name: homeText.changePassword });
    expect(screen.queryByRole('button', { name: homeText.manageUsers })).not.toBeOnTheScreen();
  });

  it('offers every user a way to change their own password', async () => {
    const { element, onPasswordChange } = home();
    await renderWithSession(element, { role: 'REGISTERED' });

    await fireEvent.press(await screen.findByRole('button', { name: homeText.changePassword }));

    expect(onPasswordChange).toHaveBeenCalledTimes(1);
  });

  it('shows the product name and tagline from the home texts', async () => {
    const { element } = home();
    await renderWithSession(element, { role: 'REGISTERED' });

    expect(await screen.findByText(homeText.title)).toBeOnTheScreen();
    expect(screen.getByText(homeText.tagline)).toBeOnTheScreen();
  });

  it('has no action without a destination', async () => {
    const { element } = home();
    await renderWithSession(element, { role: 'REGISTERED' });

    await screen.findByRole('button', { name: homeText.changePassword });
    expect(screen.queryByRole('button', { name: 'Empezar' })).not.toBeOnTheScreen();
  });
});
