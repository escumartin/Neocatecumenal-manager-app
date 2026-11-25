import * as ReactNative from 'react-native';
import { AccessibilityInfo, Platform } from 'react-native';
import { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { DateField } from '../date-field';
import { pickersText } from '@/i18n/es/pickers';
import { minHeightOf } from '@/screens/bookings/support/layout-checks';

jest.mock('react-native/Libraries/ReactNative/RendererProxy', () => ({
  ...jest.requireActual('react-native/Libraries/ReactNative/RendererProxy'),
  findNodeHandle: jest.fn(() => 7),
}));

afterEach(() => {
  jest.clearAllMocks();
  jest.restoreAllMocks();
});

async function renderField(props: Partial<React.ComponentProps<typeof DateField>> = {}) {
  const onChange = jest.fn();
  await render(<DateField label="Fecha" mode="date" value="" onChange={onChange} {...props} />);
  return { onChange };
}

describe('date field on a native platform', () => {
  it('invites to choose when there is no value', async () => {
    await renderField();

    expect(screen.getByRole('button', { name: 'Fecha' })).toBeOnTheScreen();
    expect(screen.getByText(pickersText.choose)).toBeOnTheScreen();
  });

  it('shows a date as day, month and year and exposes it as the accessibility value', async () => {
    await renderField({ value: '2030-05-11' });

    expect(screen.getByText('11/05/2030')).toBeOnTheScreen();
    expect(screen.getByLabelText('Fecha').props.accessibilityValue).toEqual({ text: '11 de mayo de 2030' });
  });

  it('shows a time as typed', async () => {
    await renderField({ label: 'Hora', mode: 'time', value: '18:00' });

    expect(screen.getByText('18:00')).toBeOnTheScreen();
  });

  it('keeps the label, the 48 point target and the field border', async () => {
    await renderField();

    const field = screen.getByLabelText('Fecha');

    expect(minHeightOf(field)).toBeGreaterThanOrEqual(48);
    expect(field.props.className).toContain('border-field');
  });

  it('flags an invalid field with its hint', async () => {
    await renderField({ invalid: true, hint: 'Elige la fecha' });

    const field = screen.getByLabelText('Fecha');
    expect(field.props['aria-invalid']).toBe(true);
    expect(field.props.accessibilityHint).toBe('Elige la fecha');
    expect(field.props.className).toContain('border-destructive');
  });

  it('opens a date picker on the current value when pressed', async () => {
    await renderField({ value: '2030-05-11' });

    await fireEvent.press(screen.getByLabelText('Fecha'));

    const picker = screen.getByTestId('date-field-picker');
    expect(picker.props.mode).toBe('date');
    const shown: Date = picker.props.value;
    expect([shown.getFullYear(), shown.getMonth(), shown.getDate()]).toEqual([2030, 4, 11]);
  });

  it('opens a time picker for a time field', async () => {
    await renderField({ mode: 'time', value: '18:30' });

    await fireEvent.press(screen.getByLabelText('Fecha'));

    const picker = screen.getByTestId('date-field-picker');
    expect(picker.props.mode).toBe('time');
    expect([picker.props.value.getHours(), picker.props.value.getMinutes()]).toEqual([18, 30]);
  });

  it('reports the picked date as AAAA-MM-DD', async () => {
    const { onChange } = await renderField({ value: '2030-05-11' });
    await fireEvent.press(screen.getByLabelText('Fecha'));

    await fireEvent(screen.getByTestId('date-field-picker'), 'change', { type: 'set' }, new Date(2031, 11, 3));

    expect(onChange).toHaveBeenCalledWith('2031-12-03');
  });

  it('reports the picked time as HH:MM', async () => {
    const { onChange } = await renderField({ mode: 'time', value: '18:00' });
    await fireEvent.press(screen.getByLabelText('Fecha'));

    await fireEvent(screen.getByTestId('date-field-picker'), 'change', { type: 'set' }, new Date(2031, 0, 1, 7, 5));

    expect(onChange).toHaveBeenCalledWith('07:05');
  });

  it('does not report anything when it opens on an empty field', async () => {
    const { onChange } = await renderField();

    await fireEvent.press(screen.getByLabelText('Fecha'));

    expect(onChange).not.toHaveBeenCalled();
  });

  it('takes the shown date when done is pressed on an empty field', async () => {
    const { onChange } = await renderField();
    await fireEvent.press(screen.getByLabelText('Fecha'));

    await fireEvent.press(screen.getByRole('button', { name: pickersText.close }));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange.mock.calls[0][0]).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('takes the shown date when the field is pressed again to close an empty field', async () => {
    const { onChange } = await renderField();
    await fireEvent.press(screen.getByLabelText('Fecha'));

    await fireEvent.press(screen.getByLabelText('Fecha'));

    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it('does not overwrite a chosen value when done is pressed', async () => {
    const { onChange } = await renderField({ value: '2030-05-11' });
    await fireEvent.press(screen.getByLabelText('Fecha'));

    await fireEvent.press(screen.getByRole('button', { name: pickersText.close }));

    expect(onChange).not.toHaveBeenCalled();
  });

  it('exposes whether the picker is expanded', async () => {
    await renderField({ value: '2030-05-11' });
    expect(screen.getByLabelText('Fecha').props.accessibilityState).toEqual({ expanded: false });

    await fireEvent.press(screen.getByLabelText('Fecha'));

    expect(screen.getByLabelText('Fecha').props.accessibilityState).toEqual({ expanded: true });
  });

  it('moves the accessibility focus to the picker when it opens', async () => {
    const setFocus = jest.spyOn(AccessibilityInfo, 'setAccessibilityFocus').mockImplementation(() => undefined);
    await renderField({ value: '2030-05-11' });

    await fireEvent.press(screen.getByLabelText('Fecha'));

    expect(setFocus).toHaveBeenCalledWith(7);
  });

  it('passes the date limits to the picker and opens on the minimum when empty', async () => {
    const minimumDate = new Date(2030, 4, 11);
    const maximumDate = new Date(2031, 4, 11);
    await renderField({ minimumDate, maximumDate });

    await fireEvent.press(screen.getByLabelText('Fecha'));

    const picker = screen.getByTestId('date-field-picker');
    expect(picker.props.minimumDate).toBe(minimumDate);
    expect(picker.props.maximumDate).toBe(maximumDate);
    expect(picker.props.value).toEqual(minimumDate);
  });

  it('follows the colour scheme in the picker', async () => {
    jest.spyOn(ReactNative, 'useColorScheme').mockReturnValue('dark');
    await renderField({ value: '2030-05-11' });

    await fireEvent.press(screen.getByLabelText('Fecha'));

    expect(screen.getByTestId('date-field-picker').props.themeVariant).toBe('dark');
  });

  it('closes the picker with the done button', async () => {
    await renderField({ value: '2030-05-11' });
    await fireEvent.press(screen.getByLabelText('Fecha'));

    await fireEvent.press(screen.getByRole('button', { name: pickersText.close }));

    expect(screen.queryByTestId('date-field-picker')).not.toBeOnTheScreen();
  });

  it('closes the picker when the field is pressed again', async () => {
    await renderField({ value: '2030-05-11' });
    await fireEvent.press(screen.getByLabelText('Fecha'));

    await fireEvent.press(screen.getByLabelText('Fecha'));

    expect(screen.queryByTestId('date-field-picker')).not.toBeOnTheScreen();
  });
});

describe('date field on android', () => {
  beforeEach(() => {
    jest.replaceProperty(Platform, 'OS', 'android');
  });

  it('opens the native dialog with the mode and the current value', async () => {
    await renderField({ mode: 'time', value: '18:30' });

    await fireEvent.press(screen.getByLabelText('Fecha'));

    expect(DateTimePickerAndroid.open).toHaveBeenCalledWith(
      expect.objectContaining({ mode: 'time', is24Hour: true, value: expect.any(Date) }),
    );
    expect(screen.queryByTestId('date-field-picker')).not.toBeOnTheScreen();
  });

  it('passes the date limits to the native dialog', async () => {
    const minimumDate = new Date(2030, 4, 11);
    const maximumDate = new Date(2031, 4, 11);
    await renderField({ minimumDate, maximumDate });

    await fireEvent.press(screen.getByLabelText('Fecha'));

    expect(DateTimePickerAndroid.open).toHaveBeenCalledWith(expect.objectContaining({ minimumDate, maximumDate }));
  });

  it('does not write a value when the dialog is opened on an empty field', async () => {
    const { onChange } = await renderField();

    await fireEvent.press(screen.getByLabelText('Fecha'));

    expect(onChange).not.toHaveBeenCalled();
  });

  it('reports the date set in the dialog', async () => {
    const { onChange } = await renderField({ value: '2030-05-11' });
    await fireEvent.press(screen.getByLabelText('Fecha'));
    const { onChange: onDialogChange } = (DateTimePickerAndroid.open as jest.Mock).mock.calls[0][0];

    onDialogChange({ type: 'set' }, new Date(2031, 11, 3));

    expect(onChange).toHaveBeenCalledWith('2031-12-03');
  });

  it('ignores a dismissed dialog', async () => {
    const { onChange } = await renderField({ value: '2030-05-11' });
    await fireEvent.press(screen.getByLabelText('Fecha'));
    const { onChange: onDialogChange } = (DateTimePickerAndroid.open as jest.Mock).mock.calls[0][0];

    onDialogChange({ type: 'dismissed' }, new Date(2031, 11, 3));

    expect(onChange).not.toHaveBeenCalled();
  });
});
