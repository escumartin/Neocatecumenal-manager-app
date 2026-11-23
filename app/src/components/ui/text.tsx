import * as Slot from '@rn-primitives/slot';
import { cva, type VariantProps } from 'class-variance-authority';
import * as React from 'react';
import { Text as RNText, type Role } from 'react-native';
import { cn } from '@/lib/utils';

const textVariants = cva('font-sans text-body text-foreground', {
  variants: {
    variant: {
      default: '',
      display: 'font-serif text-display',
      title: 'font-serif text-title',
      heading: 'font-sans-semibold text-heading',
      strong: 'font-sans-semibold',
      small: 'text-small',
      muted: 'text-small text-muted-foreground',
      caption: 'font-sans-medium text-caption',
    },
  },
  defaultVariants: {
    variant: 'default',
  },
});

type TextVariantProps = VariantProps<typeof textVariants>;

type TextVariant = NonNullable<TextVariantProps['variant']>;

const ROLE: Partial<Record<TextVariant, Role>> = {
  display: 'heading',
  title: 'heading',
  heading: 'heading',
};

const TextClassContext = React.createContext<string | undefined>(undefined);

function Text({
  className,
  asChild = false,
  variant = 'default',
  ...props
}: React.ComponentProps<typeof RNText> &
  TextVariantProps &
  React.RefAttributes<RNText> & {
    asChild?: boolean;
  }) {
  const textClass = React.useContext(TextClassContext);
  const Component = asChild ? Slot.Text : RNText;
  return (
    <Component
      className={cn(textVariants({ variant }), textClass, className)}
      role={variant ? ROLE[variant] : undefined}
      {...props}
    />
  );
}

export { Text, TextClassContext };
