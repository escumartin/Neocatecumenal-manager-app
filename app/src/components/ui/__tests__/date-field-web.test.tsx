import { fireEvent, render, screen } from '@testing-library/react-native';
import { DateField } from '../date-field.web';

describe('date field on web', () => {
  it('renders a date input labelled for assistive technology', async () => {
    await render(<DateField label="Fecha" mode="date" value="2030-05-11" onChange={jest.fn()} />);

    const input = screen.getByLabelText('Fecha');
    expect(input.props.type).toBe('date');
    expect(input.props.value).toBe('2030-05-11');
  });

  it('renders a time input for a time field', async () => {
    await render(<DateField label="Hora" mode="time" value="18:00" onChange={jest.fn()} />);

    expect(screen.getByLabelText('Hora').props.type).toBe('time');
  });

  it('reports the value typed or picked as it is, in the format of the API', async () => {
    const onChange = jest.fn();
    await render(<DateField label="Fecha" mode="date" value="" onChange={onChange} />);

    await fireEvent(screen.getByLabelText('Fecha'), 'change', { target: { value: '2031-12-03' } });

    expect(onChange).toHaveBeenCalledWith('2031-12-03');
  });

  it('keeps the 48 point target, the field border and the invalid state', async () => {
    await render(<DateField label="Fecha" mode="date" value="" invalid onChange={jest.fn()} />);

    const input = screen.getByLabelText('Fecha');
    expect(input.props.className).toContain('min-h-13');
    expect(input.props.className).toContain('border-field');
    expect(input.props['aria-invalid']).toBe(true);
  });

  it('passes the limits as min and max in the format of the API', async () => {
    await render(
      <DateField
        label="Fecha"
        mode="date"
        value=""
        minimumDate={new Date(2030, 4, 11)}
        maximumDate={new Date(2031, 4, 11)}
        onChange={jest.fn()}
      />,
    );

    const input = screen.getByLabelText('Fecha');
    expect(input.props.min).toBe('2030-05-11');
    expect(input.props.max).toBe('2031-05-11');
  });

  it('describes the field with its hint', async () => {
    await render(<DateField label="Fecha" mode="date" value="" hint="Elige la fecha" onChange={jest.fn()} />);

    expect(screen.getByLabelText('Fecha').props['aria-description']).toBe('Elige la fecha');
  });
});
