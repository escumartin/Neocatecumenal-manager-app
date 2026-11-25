import { fireEvent, render, screen } from '@testing-library/react-native';
import { RadioSection } from '../radio-section';

const options = [
  { value: 'a', label: 'Alfa' },
  { value: 'b', label: 'Beta' },
];

async function renderSection(value: string | null = null) {
  const onChange = jest.fn();
  await render(<RadioSection title="Letra" options={options} value={value} onChange={onChange} />);
  return { onChange };
}

describe('radio section', () => {
  it('shows the title as a section header and every option as a radio row', async () => {
    await renderSection();

    expect(screen.getByRole('heading', { name: 'Letra' })).toBeOnTheScreen();
    expect(screen.getByLabelText('Letra').props.role).toBe('radiogroup');
    expect(screen.getByRole('radio', { name: 'Alfa' })).toBeOnTheScreen();
    expect(screen.getByRole('radio', { name: 'Beta' })).toBeOnTheScreen();
  });

  it('marks only the chosen option as checked', async () => {
    await renderSection('b');

    expect(screen.getByRole('radio', { name: 'Beta' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Alfa' })).not.toBeChecked();
  });

  it('reports the value of the pressed option', async () => {
    const { onChange } = await renderSection();

    await fireEvent.press(screen.getByRole('radio', { name: 'Beta' }));

    expect(onChange).toHaveBeenCalledWith('b');
  });
});
