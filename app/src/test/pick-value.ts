import { fireEvent, screen } from '@testing-library/react-native';
import { dateFromValue } from '@/lib/bookings/date-value';

export async function pickValue(label: string, value: string) {
  await fireEvent.press(screen.getByLabelText(label));
  const picker = screen.getByTestId('date-field-picker');
  await fireEvent(picker, 'change', { type: 'set' }, dateFromValue(value, picker.props.mode));
  await fireEvent.press(screen.getByLabelText(label));
}
