import { screen } from '@testing-library/react-native';
import { Home } from '..';
import { homeText } from '@/i18n/es/home';
import { renderWithSession } from '@/screens/auth/support/test-support';

describe('home layout', () => {
  it('scrolls so that every action stays reachable with large text', async () => {
    await renderWithSession(
      <Home
        onOpenUsers={jest.fn()}
        onOpenRooms={jest.fn()}
        onRequestBooking={jest.fn()}
        onOpenMyRequests={jest.fn()}
        onOpenOccupancy={jest.fn()}
        onOpenPendingRequests={jest.fn()}
        onCreateEvent={jest.fn()}
        onPasswordChange={jest.fn()}
      />,
      { role: 'ADMINISTRATOR' },
    );

    await screen.findByRole('button', { name: homeText.changePassword });

    expect(String(screen.root?.props.contentContainerClassName)).toContain('flex-grow');
  });
});
