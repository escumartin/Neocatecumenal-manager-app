import { fireEvent, screen, waitFor } from '@testing-library/react-native';
import { UserEdit } from '../edit';
import { ana, communitiesApi, fakeUsersApi, renderAs } from '../support/test-support';
import { communitiesText } from '@/i18n/es/communities';
import { usersText } from '@/i18n/es/users';

jest.mock('@/lib/haptics', () => ({ hapticSuccess: jest.fn(), hapticFailure: jest.fn() }));

afterEach(() => {
  jest.clearAllMocks();
});

function editing(update: jest.Mock) {
  const api = fakeUsersApi({ update });
  return (
    <UserEdit
      userId={ana.id}
      api={api}
      communitiesApi={communitiesApi}
      onCreated={jest.fn()}
      onSaved={jest.fn()}
    />
  );
}

describe('user edit and the member link', () => {
  it('tells an administrator to transfer the member instead of changing the community of a linked user', async () => {
    await renderAs('ADMINISTRATOR', editing(jest.fn()));

    expect(await screen.findByText(usersText.form.communityHint)).toBeOnTheScreen();
  });

  it('does not show the community hint when creating a user', async () => {
    await renderAs(
      'ADMINISTRATOR',
      <UserEdit api={fakeUsersApi()} communitiesApi={communitiesApi} onCreated={jest.fn()} onSaved={jest.fn()} />,
    );

    await screen.findByLabelText(usersText.form.name);
    expect(screen.queryByText(usersText.form.communityHint)).not.toBeOnTheScreen();
  });

  it('explains that a linked user moves with the member when the server refuses the change', async () => {
    const update = jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'USER_LINKED_TO_MEMBER' });
    await renderAs('ADMINISTRATOR', editing(update));

    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(2) }));
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.USER_LINKED_TO_MEMBER}`);
    expect(usersText.errors.USER_LINKED_TO_MEMBER).toMatch(/hermano/);
  });

  it('explains that the chosen community is deactivated', async () => {
    const update = jest.fn().mockResolvedValue({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'communityId', code: 'COMMUNITY_DEACTIVATED' }],
    });
    await renderAs('ADMINISTRATOR', editing(update));

    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(2) }));
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.save }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(`⚠ ${usersText.fieldErrors.COMMUNITY_DEACTIVATED}`),
    );
  });
});
