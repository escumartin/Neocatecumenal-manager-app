import { render, screen } from '@testing-library/react-native';
import { StatusBadge, type StatusTone } from '../status-badge';

const tones: StatusTone[] = ['success', 'warning', 'destructive', 'neutral'];

describe('status badge', () => {
  it('shows the label as text', async () => {
    await render(<StatusBadge tone="success" label="Approved" />);

    expect(screen.getByText('Approved')).toBeOnTheScreen();
  });

  it('pairs the label with a glyph that is hidden from screen readers', async () => {
    await render(<StatusBadge tone="success" label="Approved" />);

    expect(screen.getByTestId('status-badge-glyph', { includeHiddenElements: true }).props['aria-hidden']).toBe(true);
  });

  it('uses a different glyph for every tone so colour is never the only cue', async () => {
    const glyphs: string[] = [];
    for (const tone of tones) {
      const { unmount } = await render(<StatusBadge tone={tone} label="x" />);
      glyphs.push(String(screen.getByTestId('status-badge-glyph', { includeHiddenElements: true }).props.children));
      await unmount();
    }

    expect(new Set(glyphs).size).toBe(tones.length);
  });

  it.each([
    ['success', 'text-success'],
    ['warning', 'text-warning'],
    ['destructive', 'text-destructive'],
    ['neutral', 'text-muted-foreground'],
  ] as const)('colours the %s tone with the %s token', async (tone, token) => {
    await render(<StatusBadge tone={tone} label="x" />);

    expect(String(screen.getByTestId('status-badge-glyph', { includeHiddenElements: true }).props.className)).toContain(token);
    expect(String(screen.getByText('x').props.className)).toContain(token);
  });
});
