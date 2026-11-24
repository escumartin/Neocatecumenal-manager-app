import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import * as Clipboard from 'expo-clipboard';
import { UserCreated } from '../created';
import { usersText } from '@/i18n/es/users';

const created = {
  id: '6f1c1f1e-0000-4000-8000-000000000021',
  accessId: 482913,
  name: 'Ana',
  role: 'REGISTERED' as const,
  communityId: '6f1c1f1e-0000-4000-8000-000000000011',
  passwordChangePending: true,
  initialPassword: 'abcd234567',
};

jest.mock('expo-clipboard', () => ({ setStringAsync: jest.fn().mockResolvedValue(true) }));

describe('user created', () => {
  beforeEach(() => jest.clearAllMocks());

  it('shows the access id and the initial password of the new user', async () => {
    await render(<UserCreated created={created} onDone={jest.fn()} />);

    expect(screen.getByText(usersText.created.title('Ana'))).toBeOnTheScreen();
    expect(screen.getByLabelText(`${usersText.created.accessId}: 482913`)).toHaveTextContent('482913');
    expect(screen.getByLabelText(`${usersText.created.password}: abcd234567`)).toHaveTextContent('abcd234567');
  });

  it('keeps the values readable by screen readers instead of hiding them behind the label', async () => {
    await render(<UserCreated created={created} onDone={jest.fn()} />);

    expect(screen.queryByLabelText(usersText.created.accessId, { exact: true })).not.toBeOnTheScreen();
    expect(screen.queryByLabelText(usersText.created.password, { exact: true })).not.toBeOnTheScreen();
  });

  it('scrolls without dismissing taps on its buttons', async () => {
    await render(<UserCreated created={created} onDone={jest.fn()} />);

    expect(screen.root?.props.keyboardShouldPersistTaps).toBe('handled');
  });

  it('warns that the password is shown only once and must be changed at first access', async () => {
    await render(<UserCreated created={created} onDone={jest.fn()} />);

    expect(screen.getByText(new RegExp(usersText.created.onlyOnce))).toBeOnTheScreen();
  });

  it('announces the one-time notice as informative status with the warning colour', async () => {
    await render(<UserCreated created={created} onDone={jest.fn()} />);

    const notice = screen.getByText(new RegExp(usersText.created.onlyOnce));

    expect(notice.props.role).toBe('status');
    expect(notice.props.className).toContain('text-warning');
    expect(notice.props.className).not.toContain('text-destructive');
    expect(screen.queryByRole('alert')).not.toBeOnTheScreen();
  });

  it('finishes with a single action', async () => {
    const onDone = jest.fn();
    await render(<UserCreated created={created} onDone={onDone} />);

    await fireEvent.press(screen.getByRole('button', { name: usersText.created.done }));

    expect(onDone).toHaveBeenCalledTimes(1);
  });

  it('copies the access id and the initial password with one button', async () => {
    await render(<UserCreated created={created} onDone={jest.fn()} />);

    await fireEvent.press(screen.getByRole('button', { name: usersText.created.copy }));

    await waitFor(() =>
      expect(Clipboard.setStringAsync).toHaveBeenCalledWith(
        `${usersText.created.accessId}: 482913\n${usersText.created.password}: abcd234567`,
      ),
    );
  });

  it('confirms the copy without relying on colour', async () => {
    await render(<UserCreated created={created} onDone={jest.fn()} />);

    await fireEvent.press(screen.getByRole('button', { name: usersText.created.copy }));

    expect(await screen.findByText(`✓ ${usersText.created.copied}`)).toBeOnTheScreen();
  });

  it('warns when the copy fails', async () => {
    jest.mocked(Clipboard.setStringAsync).mockRejectedValueOnce(new Error('denied'));
    await render(<UserCreated created={created} onDone={jest.fn()} />);

    await fireEvent.press(screen.getByRole('button', { name: usersText.created.copy }));

    expect(await screen.findByText(`⚠ ${usersText.created.copyFailed}`)).toBeOnTheScreen();
  });

  it('titles a password reset differently from a new user', async () => {
    await render(<UserCreated created={created} variant="reset" onDone={jest.fn()} />);

    expect(screen.getByText(usersText.reset.title('Ana'))).toBeOnTheScreen();
    expect(screen.getByLabelText(`${usersText.created.password}: abcd234567`)).toHaveTextContent('abcd234567');
    expect(screen.getByText(new RegExp(usersText.reset.onlyOnce))).toBeOnTheScreen();
  });
});
