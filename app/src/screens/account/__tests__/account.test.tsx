import { fireEvent, screen, waitFor } from '@testing-library/react-native';
import { Account } from '@/screens/account';
import { accountText } from '@/i18n/es/account';
import { communitiesText } from '@/i18n/es/communities';
import { identity } from '@/i18n/es/identity';
import { usersText } from '@/i18n/es/users';
import { baseMe, renderWithSession } from '@/screens/auth/support/test-support';

describe('account', () => {
  it('shows the name, the role and the community of the user', async () => {
    await renderWithSession(<Account onChangePassword={jest.fn()} />, { role: 'MODERATOR' });

    expect(await screen.findByText(baseMe.name)).toBeOnTheScreen();
    expect(screen.getByText(usersText.roles.MODERATOR)).toBeOnTheScreen();
    expect(screen.getByText(communitiesText.option(baseMe.communityNumber))).toBeOnTheScreen();
  });

  it('offers every user a way to change their own password', async () => {
    const onChangePassword = jest.fn();
    await renderWithSession(<Account onChangePassword={onChangePassword} />, { role: 'REGISTERED' });

    await fireEvent.press(await screen.findByRole('button', { name: accountText.changePassword }));

    expect(onChangePassword).toHaveBeenCalledTimes(1);
  });

  it('logs the user out', async () => {
    const { api } = await renderWithSession(<Account onChangePassword={jest.fn()} />);

    await fireEvent.press(await screen.findByRole('button', { name: identity.logout }));

    await waitFor(() => expect(api.logOut).toHaveBeenCalledTimes(1));
  });
});
