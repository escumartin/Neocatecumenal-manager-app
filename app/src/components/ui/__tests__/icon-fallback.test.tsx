import { render, screen } from '@testing-library/react-native';
import { StyleSheet } from 'react-native';
import { Icon } from '../icon';

jest.mock('expo-symbols', () => ({
  SymbolView: ({ fallback }: { fallback: unknown }) => fallback,
}));

describe('icon web fallback', () => {
  it('draws the glyph at the requested size', async () => {
    await render(<Icon name="chevron.right" size={16} testID="icon" />);

    const glyph = screen.getByTestId('icon', { includeHiddenElements: true });
    expect(StyleSheet.flatten(glyph.props.style)).toEqual(expect.objectContaining({ fontSize: 16, lineHeight: 16 }));
  });

  it('draws the glyph at the default size when none is given', async () => {
    await render(<Icon name="key" testID="icon" />);

    const glyph = screen.getByTestId('icon', { includeHiddenElements: true });
    expect(StyleSheet.flatten(glyph.props.style)).toEqual(expect.objectContaining({ fontSize: 22, lineHeight: 22 }));
  });
});
