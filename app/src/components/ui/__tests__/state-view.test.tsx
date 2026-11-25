import { fireEvent, render, screen } from '@testing-library/react-native';
import { Text } from '../text';
import { StateView } from '../state-view';

describe('state view', () => {
  it('shows a spinner with the loading message as a status', async () => {
    await render(<StateView kind="loading" message="Loading users" />);

    expect(screen.getByRole('status')).toHaveTextContent('Loading users');
    expect(screen.getByTestId('state-view-spinner')).toBeOnTheScreen();
  });

  it('shows an icon and the message when empty', async () => {
    await render(<StateView kind="empty" message="No users yet" icon="person.2" />);

    expect(screen.getByText('No users yet')).toBeOnTheScreen();
    expect(screen.getByTestId('state-view-icon')).toBeOnTheScreen();
    expect(screen.queryByTestId('state-view-spinner')).not.toBeOnTheScreen();
  });

  it('shows the actions given below the empty message', async () => {
    await render(
      <StateView kind="empty" message="No requests">
        <Text>Create one</Text>
      </StateView>,
    );

    expect(screen.getByText('Create one')).toBeOnTheScreen();
  });

  it('announces an error as an alert with a warning mark and the message', async () => {
    await render(<StateView kind="error" message="Could not load" retryLabel="Retry" onRetry={jest.fn()} />);

    expect(screen.getByRole('alert')).toHaveTextContent('⚠ Could not load');
  });

  it('offers a retry button on an error', async () => {
    const onRetry = jest.fn();
    await render(<StateView kind="error" message="Could not load" retryLabel="Retry" onRetry={onRetry} />);

    await fireEvent.press(screen.getByRole('button', { name: 'Retry' }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('shows no retry button without a retry handler', async () => {
    await render(<StateView kind="error" message="Could not load" />);

    expect(screen.queryByRole('button')).not.toBeOnTheScreen();
  });

  it.each(['loading', 'empty'] as const)('writes the %s message at body size', async (kind) => {
    await render(<StateView kind={kind} message="Message" />);

    const classes = String(screen.getByText('Message').props.className).split(/\s+/);
    expect(classes).not.toContain('text-small');
    expect(classes).toContain('text-body');
  });
});
