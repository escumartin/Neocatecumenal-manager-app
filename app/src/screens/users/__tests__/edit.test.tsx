import { act, fireEvent, screen, waitFor } from '@testing-library/react-native';
import { Alert, Platform } from 'react-native';
import { UserEdit } from '../edit';
import { ana, communities, communitiesApi, fakeUsersApi, renderAs } from '../support/test-support';
import { communitiesText } from '@/i18n/es/communities';
import { usersText } from '@/i18n/es/users';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';

jest.mock('@/lib/haptics', () => ({ hapticSuccess: jest.fn(), hapticFailure: jest.fn() }));

afterEach(() => {
  jest.clearAllMocks();
  jest.restoreAllMocks();
});

const created = { ...ana, initialPassword: 'abcd234567' };

function form(props: { userId?: string; api?: ReturnType<typeof fakeUsersApi> } = {}) {
  const api = props.api ?? fakeUsersApi();
  const onCreated = jest.fn();
  const onSaved = jest.fn();
  const onPasswordReset = jest.fn();
  const onDeleted = jest.fn();
  const element = (
    <UserEdit
      userId={props.userId}
      api={api}
      communitiesApi={communitiesApi}
      onCreated={onCreated}
      onSaved={onSaved}
      onPasswordReset={onPasswordReset}
      onDeleted={onDeleted}
    />
  );
  return { element, api, onCreated, onSaved, onPasswordReset, onDeleted };
}

describe('user edit', () => {
  it('creates a registered user in the community of the moderator', async () => {
    const api = fakeUsersApi({ create: jest.fn().mockResolvedValue({ ok: true, user: created }) });
    const { element, onCreated } = form({ api });
    await renderAs('MODERATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(usersText.form.name), 'Ana');
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.create }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created));
    expect(api.create).toHaveBeenCalledWith({ name: 'Ana', role: 'REGISTERED', communityId: communities[0].id });
  });

  it('does not offer role or community choices to a moderator', async () => {
    const { element } = form();
    await renderAs('MODERATOR', element);

    await screen.findByLabelText(usersText.form.name);
    expect(screen.queryByRole('radio', { name: usersText.roles.MODERATOR })).not.toBeOnTheScreen();
    expect(screen.queryByText(communitiesText.pickerTitle)).not.toBeOnTheScreen();
  });

  it('lets an administrator choose role and community', async () => {
    const api = fakeUsersApi({ create: jest.fn().mockResolvedValue({ ok: true, user: created }) });
    const { element } = form({ api });
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(usersText.form.name), 'Ana');
    await fireEvent.press(screen.getByRole('radio', { name: usersText.roles.MODERATOR }));
    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(2) }));
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.create }));

    await waitFor(() =>
      expect(api.create).toHaveBeenCalledWith({ name: 'Ana', role: 'MODERATOR', communityId: communities[1].id }),
    );
  });

  it('marks the chosen role without relying on colour', async () => {
    const { element } = form();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: usersText.roles.MODERATOR }));

    expect(screen.getByRole('radio', { name: usersText.roles.MODERATOR })).toBeChecked();
    expect(screen.getByText(`✓ ${usersText.roles.MODERATOR}`)).toBeOnTheScreen();
  });

  it('asks for a name without calling the server when it is blank', async () => {
    const { element, api } = form();
    await renderAs('MODERATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(usersText.form.name), '   ');
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.fieldErrors.REQUIRED}`);
    expect(api.create).not.toHaveBeenCalled();
  });

  it('shows the field error reported by the server', async () => {
    const api = fakeUsersApi({
      create: jest.fn().mockResolvedValue({
        ok: false,
        status: 422,
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'name', code: 'TOO_LONG' }],
      }),
    });
    const { element, onCreated } = form({ api });
    await renderAs('MODERATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(usersText.form.name), 'Ana');
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.fieldErrors.TOO_LONG}`);
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('ties the blank name error to the name field', async () => {
    const { element } = form();
    await renderAs('MODERATOR', element);

    const name = await screen.findByLabelText(usersText.form.name);
    await fireEvent.changeText(name, '   ');
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.create }));

    await screen.findByRole('alert');
    expect(screen.getByLabelText(usersText.form.name).props.accessibilityHint).toBe(usersText.fieldErrors.REQUIRED);
    expect(screen.getByLabelText(usersText.form.name).props['aria-invalid']).toBe(true);
    expect(screen.getAllByRole('alert')).toHaveLength(1);
  });

  it('ties the name error reported by the server to the name field', async () => {
    const api = fakeUsersApi({
      create: jest.fn().mockResolvedValue({
        ok: false,
        status: 422,
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'name', code: 'TOO_LONG' }],
      }),
    });
    const { element } = form({ api });
    await renderAs('MODERATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(usersText.form.name), 'Ana');
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.create }));

    await screen.findByRole('alert');
    expect(screen.getByLabelText(usersText.form.name).props.accessibilityHint).toBe(usersText.fieldErrors.TOO_LONG);
  });

  it('does not tie a general failure to the name field', async () => {
    const api = fakeUsersApi({ create: jest.fn().mockResolvedValue({ ok: false, status: 403, code: 'FORBIDDEN' }) });
    const { element } = form({ api });
    await renderAs('MODERATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(usersText.form.name), 'Ana');
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.create }));

    await screen.findByRole('alert');
    expect(screen.getByLabelText(usersText.form.name).props.accessibilityHint).toBeUndefined();
    expect(screen.getByLabelText(usersText.form.name).props['aria-invalid']).toBeFalsy();
  });

  it('re-enables the submit button and explains the failure when the save throws', async () => {
    const api = fakeUsersApi({ create: jest.fn().mockRejectedValue(new Error('network down')) });
    const { element } = form({ api });
    await renderAs('MODERATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(usersText.form.name), 'Ana');
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.create }));

    expect(await screen.findByText(`⚠ ${usersText.errors.UNKNOWN}`)).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: usersText.form.create })).not.toBeDisabled();
  });

  it('keeps taps on its buttons working while the keyboard is open', async () => {
    const { element } = form();
    await renderAs('MODERATOR', element);
    await screen.findByLabelText(usersText.form.name);

    expect(screen.root?.props.keyboardShouldPersistTaps).toBe('handled');
  });

  it('shows a forbidden message when the server denies the creation', async () => {
    const api = fakeUsersApi({ create: jest.fn().mockResolvedValue({ ok: false, status: 403, code: 'FORBIDDEN' }) });
    const { element } = form({ api });
    await renderAs('MODERATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(usersText.form.name), 'Ana');
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.FORBIDDEN}`);
  });

  it('loads the user and saves the new name when editing', async () => {
    const api = fakeUsersApi({ update: jest.fn().mockResolvedValue({ ok: true, user: { ...ana, name: 'Ana María' } }) });
    const { element, onSaved } = form({ userId: ana.id, api });
    await renderAs('MODERATOR', element);

    const name = await screen.findByDisplayValue('Ana');
    await fireEvent.changeText(name, 'Ana María');
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.save }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(api.get).toHaveBeenCalledWith(ana.id);
    expect(api.update).toHaveBeenCalledWith(ana.id, { name: 'Ana María', role: 'REGISTERED', communityId: ana.communityId });
  });

  it('shows an error when the user to edit cannot be loaded', async () => {
    const api = fakeUsersApi({ get: jest.fn().mockResolvedValue({ ok: false, status: 404 }) });
    const { element } = form({ userId: ana.id, api });
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.loadOneError}`);
  });

  it('explains a role change that would remove the last administrator', async () => {
    const api = fakeUsersApi({ update: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'LAST_ADMINISTRATOR' }) });
    const { element } = form({ userId: ana.id, api });
    await renderAs('ADMINISTRATOR', element);

    await screen.findByDisplayValue('Ana');
    await fireEvent.press(screen.getByRole('button', { name: usersText.form.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.LAST_ADMINISTRATOR}`);
  });

  describe('password reset', () => {
    const resetOk = { ok: true as const, initialPassword: 'wxyz345678' };
    const ownId = '6f1c1f1e-0000-4000-8000-000000000001';

    async function open(role: 'MODERATOR' | 'ADMINISTRATOR', user: typeof ana | (Omit<typeof ana, 'role'> & { role: 'MODERATOR' }), resetPassword = jest.fn().mockResolvedValue(resetOk)) {
      const api = fakeUsersApi({ get: jest.fn().mockResolvedValue({ ok: true, user }), resetPassword });
      const rendered = form({ userId: user.id, api });
      await renderAs(role, rendered.element);
      await screen.findByDisplayValue('Ana');
      return rendered;
    }

    it('does not offer a reset when creating a user', async () => {
      const { element } = form();
      await renderAs('MODERATOR', element);

      await screen.findByLabelText(usersText.form.name);
      expect(screen.queryByRole('button', { name: usersText.reset.action })).not.toBeOnTheScreen();
    });

    it('asks for confirmation before resetting', async () => {
      const { api } = await open('MODERATOR', ana);

      await fireEvent.press(screen.getByRole('button', { name: usersText.reset.action }));

      expect(screen.getByText(usersText.reset.confirmTitle('Ana'))).toBeOnTheScreen();
      expect(screen.getByText(usersText.reset.warning)).toBeOnTheScreen();
      expect(api.resetPassword).not.toHaveBeenCalled();
    });

    it('hands the new initial password over once the reset is confirmed', async () => {
      const { api, onPasswordReset } = await open('MODERATOR', ana);

      await fireEvent.press(screen.getByRole('button', { name: usersText.reset.action }));
      await fireEvent.press(screen.getByRole('button', { name: usersText.reset.confirm }));

      await waitFor(() => expect(onPasswordReset).toHaveBeenCalledWith({ ...ana, initialPassword: 'wxyz345678' }));
      expect(api.resetPassword).toHaveBeenCalledWith(ana.id);
    });

    it('cancels the reset without calling the server', async () => {
      const { api } = await open('MODERATOR', ana);

      await fireEvent.press(screen.getByRole('button', { name: usersText.reset.action }));
      await fireEvent.press(screen.getByRole('button', { name: usersText.reset.cancel }));

      expect(screen.queryByText(usersText.reset.confirmTitle('Ana'))).not.toBeOnTheScreen();
      expect(api.resetPassword).not.toHaveBeenCalled();
    });

    it('explains a reset the server forbids', async () => {
      const resetPassword = jest.fn().mockResolvedValue({ ok: false, status: 403, code: 'FORBIDDEN' });
      const { onPasswordReset } = await open('MODERATOR', ana, resetPassword);

      await fireEvent.press(screen.getByRole('button', { name: usersText.reset.action }));
      await fireEvent.press(screen.getByRole('button', { name: usersText.reset.confirm }));

      expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.FORBIDDEN}`);
      expect(onPasswordReset).not.toHaveBeenCalled();
    });

    it('explains a reset of the own account refused by the server', async () => {
      const resetPassword = jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'SELF_ACTION_FORBIDDEN' });
      const { onPasswordReset } = await open('MODERATOR', ana, resetPassword);

      await fireEvent.press(screen.getByRole('button', { name: usersText.reset.action }));
      await fireEvent.press(screen.getByRole('button', { name: usersText.reset.confirm }));

      expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.SELF_ACTION_FORBIDDEN}`);
      expect(onPasswordReset).not.toHaveBeenCalled();
    });

    it('confirms the reset in an alert dialog with the destructive variant', async () => {
      await open('MODERATOR', ana);

      await fireEvent.press(screen.getByRole('button', { name: usersText.reset.action }));

      expect(screen.getByLabelText(usersText.reset.confirmTitle('Ana')).props.role).toBe('alertdialog');
      expect(screen.getByText(usersText.reset.confirm).props.className).toContain('text-destructive-foreground');
    });

    it('re-enables the confirmation and explains the failure when the reset throws', async () => {
      const resetPassword = jest.fn().mockRejectedValue(new Error('network down'));
      const { onPasswordReset } = await open('MODERATOR', ana, resetPassword);

      await fireEvent.press(screen.getByRole('button', { name: usersText.reset.action }));
      await fireEvent.press(screen.getByRole('button', { name: usersText.reset.confirm }));

      expect(await screen.findByText(`⚠ ${usersText.errors.UNKNOWN}`)).toBeOnTheScreen();
      expect(screen.getByRole('button', { name: usersText.reset.action })).not.toBeDisabled();
      expect(onPasswordReset).not.toHaveBeenCalled();
    });

    it('does not offer a reset on the own account', async () => {
      await open('MODERATOR', { ...ana, id: ownId });

      expect(screen.queryByRole('button', { name: usersText.reset.action })).not.toBeOnTheScreen();
    });

    it('does not offer a moderator a reset for another moderator', async () => {
      await open('MODERATOR', { ...ana, role: 'MODERATOR' });

      expect(screen.queryByRole('button', { name: usersText.reset.action })).not.toBeOnTheScreen();
    });

    it('offers an administrator a reset for a moderator', async () => {
      await open('ADMINISTRATOR', { ...ana, role: 'MODERATOR' });

      expect(screen.getByRole('button', { name: usersText.reset.action })).toBeOnTheScreen();
    });
  });

  describe('deletion', () => {
    const ownId = '6f1c1f1e-0000-4000-8000-000000000001';

    async function open(role: 'MODERATOR' | 'ADMINISTRATOR', user: typeof ana | (Omit<typeof ana, 'role'> & { role: 'MODERATOR' }), remove = jest.fn().mockResolvedValue({ ok: true })) {
      const api = fakeUsersApi({ get: jest.fn().mockResolvedValue({ ok: true, user }), remove });
      const rendered = form({ userId: user.id, api });
      await renderAs(role, rendered.element);
      await screen.findByDisplayValue('Ana');
      return rendered;
    }

    it('does not offer deletion when creating a user', async () => {
      const { element } = form();
      await renderAs('ADMINISTRATOR', element);

      await screen.findByLabelText(usersText.form.name);
      expect(screen.queryByRole('button', { name: usersText.delete.row })).not.toBeOnTheScreen();
    });

    it('offers deletion as a destructive row in its own section when editing', async () => {
      await open('MODERATOR', ana);

      const row = screen.getByRole('button', { name: usersText.delete.row });
      expect(row).toBeOnTheScreen();
      expect(screen.getByText(usersText.delete.row).props.className).toContain('text-destructive');
    });

    it('does not offer deletion on the own account', async () => {
      await open('ADMINISTRATOR', { ...ana, id: ownId });

      expect(screen.queryByRole('button', { name: usersText.delete.row })).not.toBeOnTheScreen();
    });

    it('does not offer a moderator deletion of another moderator', async () => {
      await open('MODERATOR', { ...ana, role: 'MODERATOR' });

      expect(screen.queryByRole('button', { name: usersText.delete.row })).not.toBeOnTheScreen();
    });

    it('offers an administrator deletion of a moderator', async () => {
      await open('ADMINISTRATOR', { ...ana, role: 'MODERATOR' });

      expect(screen.getByRole('button', { name: usersText.delete.row })).toBeOnTheScreen();
    });

    it('asks with a native alert before deleting', async () => {
      const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
      const { api } = await open('MODERATOR', ana);

      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.row }));

      expect(alert).toHaveBeenCalledWith(
        usersText.delete.title('Ana'),
        usersText.delete.warning,
        expect.arrayContaining([expect.objectContaining({ text: usersText.delete.confirm, style: 'destructive' })]),
      );
      expect(api.remove).not.toHaveBeenCalled();
    });

    it('deletes after the native alert is confirmed, gives haptic feedback and goes back', async () => {
      const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
      const { api, onDeleted } = await open('MODERATOR', ana);

      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.row }));
      const buttons = alert.mock.calls[0][2] ?? [];
      await act(async () => buttons.find((button) => button.style === 'destructive')?.onPress?.());

      await waitFor(() => expect(onDeleted).toHaveBeenCalledTimes(1));
      expect(api.remove).toHaveBeenCalledWith(ana.id);
      expect(hapticSuccess).toHaveBeenCalledTimes(1);
    });

    it('asks with a dialog on web and deletes once confirmed', async () => {
      jest.replaceProperty(Platform, 'OS', 'web');
      const { api, onDeleted } = await open('MODERATOR', ana);

      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.row }));
      expect(screen.getByLabelText(usersText.delete.title('Ana')).props.role).toBe('alertdialog');
      expect(api.remove).not.toHaveBeenCalled();
      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.confirm }));

      await waitFor(() => expect(onDeleted).toHaveBeenCalledTimes(1));
      expect(api.remove).toHaveBeenCalledWith(ana.id);
    });

    it('keeps the user when the web dialog is cancelled', async () => {
      jest.replaceProperty(Platform, 'OS', 'web');
      const { api, onDeleted } = await open('MODERATOR', ana);

      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.row }));
      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.cancel }));

      expect(screen.queryByLabelText(usersText.delete.title('Ana'))).not.toBeOnTheScreen();
      expect(api.remove).not.toHaveBeenCalled();
      expect(onDeleted).not.toHaveBeenCalled();
    });

    it('explains a refused deletion and stays on the screen', async () => {
      jest.replaceProperty(Platform, 'OS', 'web');
      const remove = jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'LAST_MODERATOR' });
      const { onDeleted } = await open('ADMINISTRATOR', { ...ana, role: 'MODERATOR' }, remove);

      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.row }));
      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.confirm }));

      expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.LAST_MODERATOR}`);
      expect(hapticFailure).toHaveBeenCalledTimes(1);
      expect(onDeleted).not.toHaveBeenCalled();
    });

    it('has no chevron on the destructive row', async () => {
      await open('MODERATOR', ana);

      expect(screen.queryByTestId('list-row-chevron')).not.toBeOnTheScreen();
    });

    it('disables the row while a native deletion is in progress', async () => {
      const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
      const pendingRemove = jest.fn().mockReturnValue(new Promise(() => undefined));
      await open('MODERATOR', ana, pendingRemove);

      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.row }));
      await act(async () => {
        (alert.mock.calls[0][2] ?? []).find((button) => button.style === 'destructive')?.onPress?.();
      });
      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.row }));

      expect(screen.getByRole('button', { name: usersText.delete.row }).props.accessibilityState).toEqual(
        expect.objectContaining({ disabled: true }),
      );
      expect(pendingRemove).toHaveBeenCalledTimes(1);
    });

    it('enables the row again once a native deletion fails', async () => {
      const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
      await open('MODERATOR', ana, jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'LAST_MODERATOR' }));

      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.row }));
      await act(async () => {
        (alert.mock.calls[0][2] ?? []).find((button) => button.style === 'destructive')?.onPress?.();
      });

      await screen.findByRole('alert');
      expect(screen.getByRole('button', { name: usersText.delete.row }).props.accessibilityState).toEqual(
        expect.objectContaining({ disabled: false }),
      );
    });

    it('explains a failed deletion when the request throws', async () => {
      jest.replaceProperty(Platform, 'OS', 'web');
      const { onDeleted } = await open('MODERATOR', ana, jest.fn().mockRejectedValue(new Error('network down')));

      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.row }));
      await fireEvent.press(screen.getByRole('button', { name: usersText.delete.confirm }));

      expect(await screen.findByText(`⚠ ${usersText.errors.UNKNOWN}`)).toBeOnTheScreen();
      expect(onDeleted).not.toHaveBeenCalled();
    });
  });
});
