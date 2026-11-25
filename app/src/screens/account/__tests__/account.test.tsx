import { fireEvent, screen, waitFor, within } from '@testing-library/react-native';
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

  it('shows a chevron on the password row but not on the log out row', async () => {
    await renderWithSession(<Account onChangePassword={jest.fn()} />, { role: 'REGISTERED' });

    const password = await screen.findByRole('button', { name: accountText.changePassword });
    const logout = screen.getByRole('button', { name: identity.logout });

    expect(within(password).getByTestId('list-row-chevron')).toBeOnTheScreen();
    expect(within(logout).queryByTestId('list-row-chevron')).not.toBeOnTheScreen();
  });

  it('groups the profile and the actions in grouped lists', async () => {
    await renderWithSession(<Account onChangePassword={jest.fn()} />, { role: 'REGISTERED' });

    await screen.findByRole('button', { name: accountText.changePassword });

    expect(screen.getAllByTestId('list-section-card')).toHaveLength(2);
  });

  it('shows the profile rows without making them pressable', async () => {
    await renderWithSession(<Account onChangePassword={jest.fn()} />, { role: 'REGISTERED' });

    await screen.findByText(baseMe.name);

    expect(screen.getAllByRole('button')).toHaveLength(2);
  });
});
