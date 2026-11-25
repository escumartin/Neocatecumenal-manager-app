import { fireEvent, render, screen } from '@testing-library/react-native';
import { minHeightOf } from '@/screens/bookings/support/layout-checks';
import { SwitchRow } from '../switch-row';

describe('switch row', () => {
  it('is a single switch named after the label and reflecting its state', async () => {
    await render(<SwitchRow label="Share mobile" value onValueChange={jest.fn()} />);

    expect(screen.getAllByRole('switch')).toHaveLength(1);
    expect(screen.getByRole('switch', { name: 'Share mobile' })).toBeChecked();
  });

  it('toggles when any part of the row is pressed', async () => {
    const onValueChange = jest.fn();
    await render(<SwitchRow label="Share mobile" value={false} onValueChange={onValueChange} />);

    await fireEvent.press(screen.getByRole('switch', { name: 'Share mobile' }));

    expect(onValueChange).toHaveBeenCalledWith(true);
  });

  it('toggles back when it is on', async () => {
    const onValueChange = jest.fn();
    await render(<SwitchRow label="Share mobile" value onValueChange={onValueChange} />);

    await fireEvent.press(screen.getByRole('switch', { name: 'Share mobile' }));

    expect(onValueChange).toHaveBeenCalledWith(false);
  });

  it('does not toggle and reports disabled when locked', async () => {
    const onValueChange = jest.fn();
    await render(<SwitchRow label="Share mobile" value disabled onValueChange={onValueChange} />);

    const row = screen.getByRole('switch', { name: 'Share mobile' });
    await fireEvent.press(row);

    expect(row).toBeDisabled();
    expect(onValueChange).not.toHaveBeenCalled();
  });

  it('keeps the inner native switch out of the accessibility tree and a 48px target', async () => {
    await render(<SwitchRow label="Share mobile" value onValueChange={jest.fn()} />);

    expect(screen.getByRole('switch', { name: 'Share mobile' }).props.className).toContain('min-h-12');
    expect(minHeightOf(screen.getByRole('switch', { name: 'Share mobile' }))).toBeGreaterThanOrEqual(48);
    expect(screen.queryAllByRole('switch', { includeHiddenElements: true })).toHaveLength(1);
  });
});
