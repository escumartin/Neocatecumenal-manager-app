import { screen } from '@testing-library/react-native';
import { Text } from 'react-native';
import { PendingStepGate } from '../pending-step-gate';
import { renderWithSession } from '../support/test-support';

jest.mock('expo-router', () => {
  const { Text: MockText } = jest.requireActual('react-native');
  return {
    Redirect: ({ href }: { href: string }) => <MockText>{`redirect:${href}`}</MockText>,
  };
});

function gate(screenName: 'privacy-notice' | 'change-password') {
  return (
    <PendingStepGate screen={screenName}>
      <Text>step content</Text>
    </PendingStepGate>
  );
}

describe('pending step gate', () => {
  it('sends a visitor without session to the login', async () => {
    await renderWithSession(gate('privacy-notice'), null);

    expect(await screen.findByText('redirect:/login')).toBeOnTheScreen();
  });

  it('shows the privacy notice while it is pending', async () => {
    await renderWithSession(gate('privacy-notice'), { pendingSteps: ['PRIVACY_NOTICE', 'PASSWORD_CHANGE'] });

    expect(await screen.findByText('step content')).toBeOnTheScreen();
  });

  it('moves on from the privacy notice to the password change once it is accepted', async () => {
    await renderWithSession(gate('privacy-notice'), { pendingSteps: ['PASSWORD_CHANGE'] });

    expect(await screen.findByText('redirect:/change-password')).toBeOnTheScreen();
  });

  it('moves on from the privacy notice to the home when nothing else is pending', async () => {
    await renderWithSession(gate('privacy-notice'), { pendingSteps: [] });

    expect(await screen.findByText('redirect:/')).toBeOnTheScreen();
  });

  it('sends the password change back to the privacy notice while it is pending', async () => {
    await renderWithSession(gate('change-password'), { pendingSteps: ['PRIVACY_NOTICE', 'PASSWORD_CHANGE'] });

    expect(await screen.findByText('redirect:/privacy-notice')).toBeOnTheScreen();
  });

  it('shows the password change when it is pending', async () => {
    await renderWithSession(gate('change-password'), { pendingSteps: ['PASSWORD_CHANGE'] });
    expect(await screen.findByText('step content')).toBeOnTheScreen();
  });

  it('shows the password change when nothing is pending', async () => {
    await renderWithSession(gate('change-password'), { pendingSteps: [] });

    expect(await screen.findByText('step content')).toBeOnTheScreen();
  });
});
