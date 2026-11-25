import { Alert, Platform } from 'react-native';
import { confirmDestructive } from '../confirm';

const options = {
  title: 'Delete Ana?',
  message: 'This cannot be undone.',
  confirmLabel: 'Delete',
  cancelLabel: 'Cancel',
};

describe('confirmDestructive', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('shows a native alert with a cancel button and a destructive button on iOS', () => {
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);

    const handled = confirmDestructive({ ...options, onConfirm: jest.fn() });

    expect(handled).toBe(true);
    expect(alert).toHaveBeenCalledWith('Delete Ana?', 'This cannot be undone.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: expect.any(Function) },
    ]);
  });

  it('runs the action only when the destructive button is pressed', () => {
    const onConfirm = jest.fn();
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    confirmDestructive({ ...options, onConfirm });
    const buttons = alert.mock.calls[0][2] ?? [];

    expect(onConfirm).not.toHaveBeenCalled();
    buttons[1].onPress?.();

    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('shows the same native alert on Android', () => {
    jest.replaceProperty(Platform, 'OS', 'android');
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);

    expect(confirmDestructive({ ...options, onConfirm: jest.fn() })).toBe(true);
    expect(alert).toHaveBeenCalledTimes(1);
  });

  it('leaves the confirmation to the dialog on web', () => {
    jest.replaceProperty(Platform, 'OS', 'web');
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    const onConfirm = jest.fn();

    expect(confirmDestructive({ ...options, onConfirm })).toBe(false);
    expect(alert).not.toHaveBeenCalled();
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
