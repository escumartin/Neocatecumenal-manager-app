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
    expect(screen.getByLabelText(bookingsText.recurrence.until).props.accessibilityValue).toEqual({ text: '2 de julio de 2030' });
    expect(screen.getByText(bookingsText.recurrence.hint)).toBeOnTheScreen();
  });

  it('reports the picked end date', async () => {
    const props = await renderFields({ enabled: true, until: '2030-06-01' });

    await fireEvent.press(screen.getByLabelText(bookingsText.recurrence.until));
    await fireEvent(screen.getByTestId('date-field-picker'), 'change', { type: 'set' }, new Date(2030, 6, 2));

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

  it('groups the end date in a section with its title and hint', async () => {
    await renderFields({ enabled: true });

    expect(screen.getByRole('heading', { name: bookingsText.recurrence.until })).toBeOnTheScreen();
    expect(screen.getByText(bookingsText.recurrence.hint)).toBeOnTheScreen();
  });

  it('limits the end date from the first date to one year after it and opens on the first date', async () => {
    await renderFields({ enabled: true, firstDate: '2030-05-11' });

    await fireEvent.press(screen.getByLabelText(bookingsText.recurrence.until));

    const picker = screen.getByTestId('date-field-picker');
    expect(picker.props.minimumDate).toEqual(new Date(2030, 4, 11));
    expect(picker.props.maximumDate).toEqual(new Date(2031, 4, 11));
    expect(picker.props.value).toEqual(new Date(2030, 4, 11));
  });

  it('limits the end date from today when there is no first date yet', async () => {
    await renderFields({ enabled: true });

    await fireEvent.press(screen.getByLabelText(bookingsText.recurrence.until));

    const today = new Date();
    const minimum: Date = screen.getByTestId('date-field-picker').props.minimumDate;
    expect([minimum.getFullYear(), minimum.getMonth(), minimum.getDate()]).toEqual([
      today.getFullYear(),
      today.getMonth(),
      today.getDate(),
    ]);
  });
});
