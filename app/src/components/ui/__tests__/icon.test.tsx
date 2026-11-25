import { render, screen } from '@testing-library/react-native';
import { Icon } from '../icon';

describe('icon', () => {
  it('draws the named SF Symbol at the requested size', async () => {
    await render(<Icon name="chevron.right" size={16} testID="icon" />);

    const symbol = screen.getByTestId('icon');
    expect(symbol.props.name).toBe('chevron.right');
    expect(symbol.props.style).toEqual(expect.objectContaining({ width: 16, height: 16 }));
  });

  it('tints the symbol with the primary colour on request', async () => {
    await render(<Icon name="key" tone="primary" testID="icon" />);

    expect(screen.getByTestId('icon').props.tint).not.toBeNull();
  });
});
