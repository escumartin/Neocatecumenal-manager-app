import { fireEvent, screen, waitFor } from '@testing-library/react-native';
import { TextInput } from 'react-native';
import { ChangePassword } from '../change-password';
import { renderWithSession } from '../support/test-support';
import { identity } from '@/i18n/es/identity';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';

jest.mock('@/lib/haptics', () => ({ hapticSuccess: jest.fn(), hapticFailure: jest.fn() }));

afterEach(() => {
  jest.clearAllMocks();
});

const text = identity.changePassword;
const pending = { pendingSteps: ['PASSWORD_CHANGE' as const] };

async function fill(current: string, next: string) {
  await fireEvent.changeText(await screen.findByLabelText(text.currentLabel), current);
  await fireEvent.changeText(screen.getByLabelText(text.newLabel), next);
}

describe('change password', () => {
  it('accepts up to 64 characters in the new password', async () => {
    await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending);

    expect((await screen.findByLabelText(text.newLabel)).props.maxLength).toBe(64);
  });

  it('hides both passwords until the person chooses to show them', async () => {
    await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending);

    expect((await screen.findByLabelText(text.currentLabel)).props.secureTextEntry).toBe(true);
    expect(screen.getByLabelText(text.newLabel).props.secureTextEntry).toBe(true);

    await fireEvent.press(screen.getByRole('button', { name: text.show }));

    expect(screen.getByLabelText(text.currentLabel).props.secureTextEntry).toBe(false);
    expect(screen.getByLabelText(text.newLabel).props.secureTextEntry).toBe(false);
    expect(screen.getByRole('button', { name: text.hide })).toBeOnTheScreen();
  });

  it('changes the password with the current and the new one and finishes', async () => {
    const onDone = jest.fn();
    const { api } = await renderWithSession(<ChangePassword onDone={onDone} />, pending);

    await fill('abcd234567', 'a much longer passphrase');
    await fireEvent.press(screen.getByRole('button', { name: text.submit }));

    await waitFor(() => expect(onDone).toHaveBeenCalledTimes(1));
    expect(api.changePassword).toHaveBeenCalledWith('abcd234567', 'a much longer passphrase');
  });

  it('asks for the missing field without calling the server', async () => {
    const { api } = await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending);

    await fill('abcd234567', '');
    await fireEvent.press(screen.getByRole('button', { name: text.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${text.fieldErrors.REQUIRED}`);
    expect(screen.getByLabelText(text.newLabel).props.accessibilityHint).toBe(text.fieldErrors.REQUIRED);
    expect(screen.getByLabelText(text.currentLabel).props.accessibilityHint).toBeUndefined();
    expect(api.changePassword).not.toHaveBeenCalled();
  });

  it.each(['PASSWORD_TOO_SHORT', 'PASSWORD_TOO_LONG', 'PASSWORD_TOO_COMMON', 'PASSWORD_CONTAINS_ACCESS_ID'])(
    'shows %s next to the new password field',
    async (code) => {
      const onDone = jest.fn();
      await renderWithSession(<ChangePassword onDone={onDone} />, pending, {
        changePassword: jest.fn().mockResolvedValue({
          ok: false,
          status: 422,
          code: 'VALIDATION_FAILED',
          fieldErrors: [{ field: 'newPassword', code }],
        }),
      });

      await fill('abcd234567', 'short');
      await fireEvent.press(screen.getByRole('button', { name: text.submit }));

      expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${text.fieldErrors[code]}`);
      expect(screen.getByLabelText(text.newLabel).props.accessibilityHint).toBe(text.fieldErrors[code]);
      expect(screen.getByLabelText(text.currentLabel).props.accessibilityHint).toBeUndefined();
      expect(onDone).not.toHaveBeenCalled();
    },
  );

  it('shows an incorrect current password next to the current password field', async () => {
    await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending, {
      changePassword: jest.fn().mockResolvedValue({
        ok: false,
        status: 422,
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'currentPassword', code: 'CURRENT_PASSWORD_INCORRECT' }],
      }),
    });

    await fill('wrong-one', 'a much longer passphrase');
    await fireEvent.press(screen.getByRole('button', { name: text.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${text.fieldErrors.CURRENT_PASSWORD_INCORRECT}`);
    expect(screen.getByLabelText(text.currentLabel).props.accessibilityHint).toBe(
      text.fieldErrors.CURRENT_PASSWORD_INCORRECT,
    );
    expect(screen.getByLabelText(text.newLabel).props.accessibilityHint).toBeUndefined();
  });

  it('shows a generic alert when the change fails for an unknown reason', async () => {
    await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending, {
      changePassword: jest.fn().mockResolvedValue({ ok: false, status: 500 }),
    });

    await fill('abcd234567', 'a much longer passphrase');
    await fireEvent.press(screen.getByRole('button', { name: text.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${identity.errors.UNKNOWN_ACTION}`);
  });

  it('re-enables the submit button and explains the failure when the call throws', async () => {
    await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending, {
      changePassword: jest.fn().mockRejectedValue(new Error('network down')),
    });

    await fill('abcd234567', 'a much longer passphrase');
    await fireEvent.press(screen.getByRole('button', { name: text.submit }));

    expect(await screen.findByText(`⚠ ${identity.errors.UNKNOWN_ACTION}`)).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: text.submit })).not.toBeDisabled();
  });

  it('keeps taps on its buttons working while the keyboard is open', async () => {
    await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending);
    await screen.findByLabelText(text.newLabel);

    expect(screen.root?.props.keyboardShouldPersistTaps).toBe('handled');
  });

  it('lets a person who chose to change the password go back without changing it', async () => {
    const onCancel = jest.fn();
    await renderWithSession(<ChangePassword onDone={jest.fn()} onCancel={onCancel} />, {});

    await fireEvent.press(await screen.findByRole('button', { name: text.cancel }));

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('does not offer to go back while the password change is required', async () => {
    await renderWithSession(<ChangePassword onDone={jest.fn()} onCancel={jest.fn()} />, pending);

    await screen.findByLabelText(text.newLabel);
    expect(screen.queryByRole('button', { name: text.cancel })).not.toBeOnTheScreen();
  });

  it('does not offer to go back when no way back was given', async () => {
    await renderWithSession(<ChangePassword onDone={jest.fn()} />, {});

    await screen.findByLabelText(text.newLabel);
    expect(screen.queryByRole('button', { name: text.cancel })).not.toBeOnTheScreen();
  });

  it('moves from the current password to the new one with the next key', async () => {
    await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending);

    const current = await screen.findByLabelText(text.currentLabel);
    const focus = jest.spyOn(TextInput.prototype, 'focus');
    expect(current.props.returnKeyType).toBe('next');
    expect(current.props.submitBehavior).toBe('submit');

    await fireEvent(current, 'submitEditing');

    expect(focus).toHaveBeenCalledTimes(1);
  });

  it('finishes with the done key of the new password', async () => {
    const { api } = await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending);

    await fill('abcd234567', 'a much longer passphrase');
    const next = screen.getByLabelText(text.newLabel);
    expect(next.props.returnKeyType).toBe('done');
    await fireEvent(next, 'submitEditing');

    await waitFor(() => expect(api.changePassword).toHaveBeenCalledTimes(1));
  });

  it('lifts the form above the keyboard on iOS', async () => {
    await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending);
    await screen.findByLabelText(text.currentLabel);

    expect(screen.root?.props.automaticallyAdjustKeyboardInsets).toBe(true);
  });

  it('gives success feedback when the password is changed', async () => {
    await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending);

    await fill('abcd234567', 'a much longer passphrase');
    await fireEvent.press(screen.getByRole('button', { name: text.submit }));

    await waitFor(() => expect(hapticSuccess).toHaveBeenCalledTimes(1));
    expect(hapticFailure).not.toHaveBeenCalled();
  });

  it('gives error feedback when a field is missing', async () => {
    await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending);

    await fill('abcd234567', '');
    await fireEvent.press(screen.getByRole('button', { name: text.submit }));

    await screen.findByRole('alert');
    expect(hapticFailure).toHaveBeenCalledTimes(1);
    expect(hapticSuccess).not.toHaveBeenCalled();
  });

  it('gives error feedback when the server refuses the change', async () => {
    const { api } = await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending);
    (api.changePassword as jest.Mock).mockResolvedValue({ ok: false, status: 500 });

    await fill('abcd234567', 'a much longer passphrase');
    await fireEvent.press(screen.getByRole('button', { name: text.submit }));

    await screen.findByRole('alert');
    expect(hapticFailure).toHaveBeenCalledTimes(1);
    expect(hapticSuccess).not.toHaveBeenCalled();
  });

  it('gives error feedback when the call throws', async () => {
    const { api } = await renderWithSession(<ChangePassword onDone={jest.fn()} />, pending);
    (api.changePassword as jest.Mock).mockRejectedValue(new Error('network down'));

    await fill('abcd234567', 'a much longer passphrase');
    await fireEvent.press(screen.getByRole('button', { name: text.submit }));

    await screen.findByRole('alert');
    expect(hapticFailure).toHaveBeenCalledTimes(1);
  });
});
