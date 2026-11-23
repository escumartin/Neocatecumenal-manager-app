import { cva, type VariantProps } from 'class-variance-authority';
import { Platform, Pressable } from 'react-native';
import { TextClassContext } from '@/components/ui/text';
import { cn } from '@/lib/utils';

const buttonVariants = cva(
  'min-h-12 flex-row items-center justify-center gap-2 rounded-md px-6 active:opacity-90',
  {
    variants: {
      variant: {
        default: 'bg-primary',
        secondary: 'bg-secondary',
        outline: 'border-2 border-input bg-background',
        ghost: 'bg-transparent active:bg-accent',
        destructive: 'bg-destructive',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
);

const buttonTextVariants = cva('font-sans-semibold text-body', {
  variants: {
    variant: {
      default: 'text-primary-foreground',
      secondary: 'text-secondary-foreground',
      outline: 'text-foreground',
      ghost: 'text-foreground',
      destructive: 'text-destructive-foreground',
    },
  },
  defaultVariants: {
    variant: 'default',
  },
});

type ButtonProps = React.ComponentProps<typeof Pressable> & VariantProps<typeof buttonVariants>;

function Button({ className, variant, ...props }: ButtonProps) {
  return (
    <TextClassContext.Provider value={buttonTextVariants({ variant })}>
      <Pressable
        role="button"
        className={cn(
          props.disabled && 'opacity-60',
          buttonVariants({ variant }),
          Platform.select({ web: 'cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-ring' }),
          className,
        )}
        {...props}
      />
    </TextClassContext.Provider>
  );
}

export { Button, buttonTextVariants, buttonVariants };
export type { ButtonProps };
