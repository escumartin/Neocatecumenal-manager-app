import { fireEvent, render, screen, within } from '@testing-library/react-native';
import { Dimensions } from 'react-native';
import { Text } from '../text';
import { ListRow } from '../list-row';

const tailwindConfig = jest.requireActual('../../../../tailwind.config.js');

function classesOf(testID: string): string[] {
  return String(screen.getByTestId(testID).props.className).split(/\s+/);
}

function useFontScale(fontScale: number) {
  jest.spyOn(Dimensions, 'get').mockReturnValue({ width: 390, height: 844, scale: 3, fontScale });
}

afterEach(() => {
  jest.restoreAllMocks();
});

describe('list row', () => {
  it('shows the title, the subtitle and the trailing value', async () => {
    await render(<ListRow title="Ana" subtitle="Registered" trailing="482913" />);

    expect(screen.getByText('Ana')).toBeOnTheScreen();
    expect(screen.getByText('Registered')).toBeOnTheScreen();
    expect(screen.getByText('482913')).toBeOnTheScreen();
  });

  it('is a button that handles a press as a whole', async () => {
    const onPress = jest.fn();
    await render(<ListRow title="Ana" subtitle="Registered" onPress={onPress} />);

    await fireEvent.press(screen.getByRole('button', { name: 'Ana, Registered' }));

    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it('handles a long press', async () => {
    const onLongPress = jest.fn();
    await render(<ListRow title="Ana" onPress={jest.fn()} onLongPress={onLongPress} />);

    await fireEvent(screen.getByRole('button', { name: 'Ana' }), 'longPress');

    expect(onLongPress).toHaveBeenCalledTimes(1);
  });

  it('joins title, subtitle and trailing value into the accessible name', async () => {
    await render(<ListRow title="Ana" subtitle="Registered" trailing="482913" onPress={jest.fn()} />);

    expect(screen.getByRole('button', { name: 'Ana, Registered, 482913' })).toBeOnTheScreen();
  });

  it('prefers an explicit accessibility label and passes the hint and the actions on', async () => {
    const onAccessibilityAction = jest.fn();
    await render(
      <ListRow
        title="Ana"
        onPress={jest.fn()}
        accessibilityLabel="Edit Ana"
        accessibilityHint="Hold to delete"
        accessibilityActions={[{ name: 'delete', label: 'Delete Ana' }]}
        onAccessibilityAction={onAccessibilityAction}
      />,
    );

    const row = screen.getByRole('button', { name: 'Edit Ana' });
    expect(row.props.accessibilityHint).toBe('Hold to delete');
    await fireEvent(row, 'accessibilityAction', { nativeEvent: { actionName: 'delete' } });

    expect(onAccessibilityAction).toHaveBeenCalledTimes(1);
  });

  it('is not a button without a press handler', async () => {
    await render(<ListRow title="Role" trailing="Moderator" />);

    expect(screen.queryByRole('button')).not.toBeOnTheScreen();
    expect(screen.getByText('Role')).toBeOnTheScreen();
  });

  it('shows a chevron only when the row can be pressed', async () => {
    const { rerender } = await render(<ListRow title="Ana" onPress={jest.fn()} />);
    expect(screen.getByTestId('list-row-chevron')).toBeOnTheScreen();

    await rerender(<ListRow title="Ana" />);
    expect(screen.queryByTestId('list-row-chevron')).not.toBeOnTheScreen();
  });

  it('shows the leading symbol when given', async () => {
    const { rerender } = await render(<ListRow title="Users" icon="person.2" onPress={jest.fn()} />);
    expect(screen.getByTestId('list-row-icon')).toBeOnTheScreen();

    await rerender(<ListRow title="Users" onPress={jest.fn()} />);
    expect(screen.queryByTestId('list-row-icon')).not.toBeOnTheScreen();
  });

  it('keeps a minimum height of at least 48 points from the spacing scale', async () => {
    await render(<ListRow title="Ana" onPress={jest.fn()} />);

    const minHeight = classesOf('list-row').find((name) => name.startsWith('min-h-'));
    const step = minHeight?.replace('min-h-', '') ?? '';

    expect(Number.parseInt(tailwindConfig.theme.extend.spacing[step], 10)).toBeGreaterThanOrEqual(48);
  });

  it('draws a hairline separator on every row but the first', async () => {
    const { rerender } = await render(<ListRow title="Ana" position="first" />);
    expect(classesOf('list-row')).not.toContain('border-t-hairline');

    await rerender(<ListRow title="Ana" position="middle" />);
    expect(classesOf('list-row')).toContain('border-t-hairline');
  });

  it('rounds the outer corners of the first and last rows of a group', async () => {
    const { rerender } = await render(<ListRow title="Ana" position="first" />);
    expect(classesOf('list-row')).toContain('rounded-t-lg');
    expect(classesOf('list-row')).not.toContain('rounded-b-lg');

    await rerender(<ListRow title="Ana" position="last" />);
    expect(classesOf('list-row')).toContain('rounded-b-lg');

    await rerender(<ListRow title="Ana" position="only" />);
    expect(classesOf('list-row')).toContain('rounded-lg');
  });

  it('shows the title in the destructive colour when flagged', async () => {
    await render(<ListRow title="Delete" destructive onPress={jest.fn()} />);

    expect(String(screen.getByText('Delete').props.className)).toContain('text-destructive');
  });

  it('shows content given below the title', async () => {
    await render(
      <ListRow title="Status">
        <Text>Approved</Text>
      </ListRow>,
    );

    expect(screen.getByText('Approved')).toBeOnTheScreen();
  });

  it('keeps the subtitle and the trailing value at body size', async () => {
    await render(<ListRow title="Ana" subtitle="Registered" trailing="482913" />);

    for (const value of ['Registered', '482913']) {
      const classes = String(screen.getByText(value).props.className).split(/\s+/);
      expect(classes).not.toContain('text-small');
      expect(classes).toContain('text-muted-foreground');
    }
  });

  it('can be a radio that reports whether it is selected', async () => {
    const onPress = jest.fn();
    const { rerender } = await render(<ListRow title="Hall" role="radio" selected={false} onPress={onPress} />);
    expect(screen.getByRole('radio', { name: 'Hall' }).props.accessibilityState).toEqual(expect.objectContaining({ checked: false }));
    expect(screen.queryByTestId('list-row-chevron')).not.toBeOnTheScreen();
    expect(screen.queryByTestId('list-row-check')).not.toBeOnTheScreen();

    await rerender(<ListRow title="Hall" role="radio" selected onPress={onPress} />);

    expect(screen.getByRole('radio', { name: 'Hall' }).props.accessibilityState).toEqual(expect.objectContaining({ checked: true }));
    expect(screen.getByTestId('list-row-check', { includeHiddenElements: true })).toBeOnTheScreen();
    await fireEvent.press(screen.getByRole('radio', { name: 'Hall' }));
    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it('lets a long trailing value shrink and aligns it to the right', async () => {
    useFontScale(1);
    await render(<ListRow title="Ana" trailing="Administrator" />);

    const classes = String(screen.getByText('Administrator').props.className).split(/\s+/);
    expect(classes).toContain('shrink');
    expect(classes).toContain('text-right');
  });

  it('keeps the trailing value beside the title at the default font scale', async () => {
    useFontScale(1);
    await render(<ListRow title="Ana" subtitle="Registered" trailing="Administrator" />);

    expect(within(screen.getByTestId('list-row-body')).queryByText('Administrator')).not.toBeOnTheScreen();
  });

  it('stacks the trailing value under the subtitle at a large font scale', async () => {
    useFontScale(2);
    await render(<ListRow title="Ana" subtitle="Registered" trailing="Administrator" />);

    expect(within(screen.getByTestId('list-row-body')).getByText('Administrator')).toBeOnTheScreen();
  });

  it('shows a chevron on a pressable row by default and none on a destructive one', async () => {
    const { rerender } = await render(<ListRow title="Ana" onPress={jest.fn()} />);
    expect(screen.getByTestId('list-row-chevron')).toBeOnTheScreen();

    await rerender(<ListRow title="Delete" destructive onPress={jest.fn()} />);
    expect(screen.queryByTestId('list-row-chevron')).not.toBeOnTheScreen();
  });

  it('lets the accessory be chosen explicitly', async () => {
    const { rerender } = await render(<ListRow title="Log out" accessory="none" onPress={jest.fn()} />);
    expect(screen.queryByTestId('list-row-chevron')).not.toBeOnTheScreen();

    await rerender(<ListRow title="Delete" destructive accessory="chevron" onPress={jest.fn()} />);
    expect(screen.getByTestId('list-row-chevron')).toBeOnTheScreen();
  });

  it('ignores presses while disabled', async () => {
    const onPress = jest.fn();
    await render(<ListRow title="Delete" disabled onPress={onPress} />);

    await fireEvent.press(screen.getByRole('button', { name: 'Delete' }));

    expect(onPress).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Delete' }).props.accessibilityState).toEqual(
      expect.objectContaining({ disabled: true }),
    );
  });

  it('lets screen readers reach custom content instead of grouping it into the label', async () => {
    await render(
      <ListRow title="Status">
        <Text>Approved</Text>
      </ListRow>,
    );

    expect(screen.getByTestId('list-row').props.accessible).toBe(false);
  });
});
