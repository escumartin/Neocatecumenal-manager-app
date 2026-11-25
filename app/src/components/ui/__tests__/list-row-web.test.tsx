import { render, screen } from '@testing-library/react-native';
import { ListRow } from '../list-row';

jest.mock('react-native/Libraries/Components/Pressable/Pressable', () => {
  const { View } = jest.requireActual('react-native');
  return { __esModule: true, default: (props: object) => <View {...props} /> };
});

describe('list row on web', () => {
  it('reports whether a radio row is selected as aria-checked', async () => {
    const { rerender } = await render(<ListRow title="Hall" role="radio" selected={false} onPress={jest.fn()} />);
    expect(screen.getByTestId('list-row').props['aria-checked']).toBe(false);

    await rerender(<ListRow title="Hall" role="radio" selected onPress={jest.fn()} />);
    expect(screen.getByTestId('list-row').props['aria-checked']).toBe(true);
  });

  it('does not set aria-checked on a button row', async () => {
    await render(<ListRow title="Hall" onPress={jest.fn()} />);

    expect(screen.getByTestId('list-row').props['aria-checked']).toBeUndefined();
  });
});
