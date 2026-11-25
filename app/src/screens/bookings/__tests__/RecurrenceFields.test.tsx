import { fireEvent, render, screen } from '@testing-library/react-native';
import { RecurrenceFields } from '../recurrence-fields';
import { fontSizeOf, minHeightOf } from '../support/layout-checks';
import { bookingsText } from '@/i18n/es/bookings';

async function renderFields(overrides: Partial<React.ComponentProps<typeof RecurrenceFields>> = {}) {
  const props = {
    enabled: false,
    until: '',
    invalid: false,
    onToggle: jest.fn(),
    onChangeUntil: jest.fn(),
    ...overrides,
  };
  await render(<RecurrenceFields {...props} />);
  return props;
}

describe('recurrence fields', () => {
  it('offers a weekly repetition as an unchecked checkbox', async () => {
    await renderFields();

    const toggle = screen.getByRole('checkbox', { name: bookingsText.recurrence.toggle });

    expect(toggle).toBeOnTheScreen();
    expect(toggle).not.toBeChecked();
  });

  it('hides the end date until the repetition is switched on', async () => {
    await renderFields();

    expect(screen.queryByLabelText(bookingsText.recurrence.until)).not.toBeOnTheScreen();
  });

  it('reports the toggle with the opposite value', async () => {
    const props = await renderFields();

    await fireEvent.press(screen.getByRole('checkbox', { name: bookingsText.recurrence.toggle }));

    expect(props.onToggle).toHaveBeenCalledWith(true);
  });

  it('shows the mandatory end date once the repetition is on', async () => {
    await renderFields({ enabled: true, until: '2030-07-02' });

    expect(screen.getByRole('checkbox', { name: bookingsText.recurrence.toggle })).toBeChecked();
    expect(screen.getByLabelText(bookingsText.recurrence.until).props.value).toBe('2030-07-02');
    expect(screen.getByText(bookingsText.recurrence.hint)).toBeOnTheScreen();
  });

  it('reports the typed end date', async () => {
    const props = await renderFields({ enabled: true });

    await fireEvent.changeText(screen.getByLabelText(bookingsText.recurrence.until), '2030-07-02');

    expect(props.onChangeUntil).toHaveBeenCalledWith('2030-07-02');
  });

  it('marks the end date as invalid when the form says so', async () => {
    await renderFields({ enabled: true, invalid: true });

    expect(screen.getByLabelText(bookingsText.recurrence.until).props['aria-invalid']).toBe(true);
  });

  it('keeps every control at 48 points or more and every text at 18 points or more', async () => {
    await renderFields({ enabled: true });

    expect(minHeightOf(screen.getByRole('checkbox'))).toBeGreaterThanOrEqual(48);
    expect(minHeightOf(screen.getByLabelText(bookingsText.recurrence.until))).toBeGreaterThanOrEqual(48);
    for (const text of screen.getAllByText(/\S/)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });
});
