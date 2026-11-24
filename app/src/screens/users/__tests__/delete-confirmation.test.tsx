import { PortalHost } from '@rn-primitives/portal';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { DeleteConfirmation } from '../delete-confirmation';
import { usersText } from '@/i18n/es/users';

describe('delete confirmation', () => {
  it('names the user and warns that the deletion is permanent', async () => {
    await render(
      <>
        <DeleteConfirmation name="Ana" busy={false} onConfirm={jest.fn()} onCancel={jest.fn()} />
        <PortalHost />
      </>,
    );

    expect(screen.getByLabelText(usersText.delete.title('Ana'))).toBeOnTheScreen();
    expect(screen.getByText(usersText.delete.title('Ana'))).toBeOnTheScreen();
    expect(screen.getByText(usersText.delete.warning)).toBeOnTheScreen();
  });

  it('confirms only when the destructive action is pressed', async () => {
    const onConfirm = jest.fn();
    await render(
      <>
        <DeleteConfirmation name="Ana" busy={false} onConfirm={onConfirm} onCancel={jest.fn()} />
        <PortalHost />
      </>,
    );
    expect(onConfirm).not.toHaveBeenCalled();

    await fireEvent.press(screen.getByRole('button', { name: usersText.delete.confirm }));

    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('cancels without deleting', async () => {
    const onConfirm = jest.fn();
    const onCancel = jest.fn();
    await render(
      <>
        <DeleteConfirmation name="Ana" busy={false} onConfirm={onConfirm} onCancel={onCancel} />
        <PortalHost />
      </>,
    );

    await fireEvent.press(screen.getByRole('button', { name: usersText.delete.cancel }));

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('disables both actions while the deletion is running', async () => {
    await render(
      <>
        <DeleteConfirmation name="Ana" busy onConfirm={jest.fn()} onCancel={jest.fn()} />
        <PortalHost />
      </>,
    );

    expect(screen.getByRole('button', { name: usersText.delete.confirm })).toBeDisabled();
    expect(screen.getByRole('button', { name: usersText.delete.cancel })).toBeDisabled();
  });

  it('is an alert dialog and asks for the destructive action with the destructive variant', async () => {
    await render(
      <>
        <DeleteConfirmation name="Ana" busy={false} onConfirm={jest.fn()} onCancel={jest.fn()} />
        <PortalHost />
      </>,
    );

    expect(screen.getByLabelText(usersText.delete.title('Ana')).props.role).toBe('alertdialog');
    expect(screen.getByText(usersText.delete.confirm).props.className).toContain('text-destructive-foreground');
  });
});
