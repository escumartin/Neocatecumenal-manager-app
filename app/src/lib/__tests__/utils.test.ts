import { cn } from '../utils';

describe('cn', () => {
  it('keeps the type scale size next to a text colour', () => {
    expect(cn('text-body text-foreground')).toBe('text-body text-foreground');
  });

  it('keeps the field border width next to a border colour', () => {
    expect(cn('border-field border-input')).toBe('border-field border-input');
  });

  it('lets a later colour override an earlier one', () => {
    expect(cn('text-foreground', 'text-destructive')).toBe('text-destructive');
  });

  it('keeps the hairline top border next to a border colour', () => {
    expect(cn('border-t-hairline border-border')).toBe('border-t-hairline border-border');
  });
});
