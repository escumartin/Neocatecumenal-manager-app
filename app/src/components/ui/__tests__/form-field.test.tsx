import { render, screen } from '@testing-library/react-native';
import { Text } from 'react-native';
import { FormField } from '../form-field';

describe('form field', () => {
  it('shows the label above its control and an optional description', async () => {
    await render(
      <FormField label="Nombre" description="Sin datos personales">
        <Text>control</Text>
      </FormField>,
    );

    expect(screen.getByText('Nombre')).toBeOnTheScreen();
    expect(screen.getByText('Sin datos personales')).toBeOnTheScreen();
    expect(screen.getByText('control')).toBeOnTheScreen();
  });

  it('omits the label when the section header already names the field', async () => {
    await render(
      <FormField>
        <Text>control</Text>
      </FormField>,
    );

    expect(screen.getByText('control')).toBeOnTheScreen();
    expect(screen.queryByText('Nombre')).not.toBeOnTheScreen();
  });
});
