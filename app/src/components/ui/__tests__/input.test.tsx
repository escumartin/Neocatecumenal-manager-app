import { render, screen } from '@testing-library/react-native';
import { Input } from '../input';

const tailwindConfig = jest.requireActual('../../../../tailwind.config.js');

function classesOf(label: string): string[] {
  return String(screen.getByLabelText(label).props.className).split(/\s+/);
}

function pixels(value: string): number {
  return Number.parseInt(value, 10);
}

describe('input', () => {
  it('keeps a minimum height of at least 48 points from the spacing scale', async () => {
    await render(<Input accessibilityLabel="Name" />);

    const minHeight = classesOf('Name').find((name) => name.startsWith('min-h-'));
    const step = minHeight?.replace('min-h-', '') ?? '';

    expect(pixels(tailwindConfig.theme.extend.spacing[step])).toBeGreaterThanOrEqual(48);
  });

  it('takes its border width from a token instead of an arbitrary value', async () => {
    await render(<Input accessibilityLabel="Name" />);

    const widthClass = classesOf('Name').find((name) => /^border-(?!input|destructive)/.test(name)) ?? '';

    expect(widthClass).not.toContain('[');
    expect(tailwindConfig.theme.extend.borderWidth[widthClass.replace('border-', '')]).toBeDefined();
  });

  it('dims a disabled field with token colours instead of opacity', async () => {
    await render(<Input accessibilityLabel="Name" editable={false} />);

    expect(classesOf('Name')).not.toContain('opacity-60');
    expect(classesOf('Name')).toContain('bg-muted');
    expect(classesOf('Name')).toContain('text-muted-foreground');
  });

  it('flags an invalid field with aria-invalid and a destructive border', async () => {
    await render(<Input accessibilityLabel="Name" invalid />);

    expect(screen.getByLabelText('Name').props['aria-invalid']).toBe(true);
    expect(classesOf('Name')).toContain('border-destructive');
  });

  it('is not flagged as invalid by default', async () => {
    await render(<Input accessibilityLabel="Name" />);

    expect(screen.getByLabelText('Name').props['aria-invalid']).toBeFalsy();
    expect(classesOf('Name')).not.toContain('border-destructive');
  });
});
