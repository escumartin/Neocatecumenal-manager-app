import { Platform, TextInput } from 'react-native';
import { cn } from '@/lib/utils';

type InputProps = React.ComponentProps<typeof TextInput> & { invalid?: boolean };

function Input({ className, invalid = false, ...props }: InputProps) {
  const disabled = props.editable === false;
  return (
    <TextInput
      aria-invalid={invalid || undefined}
      className={cn(
        'min-h-13 rounded-md border-field bg-background px-4 font-sans text-body text-foreground',
        invalid ? 'border-destructive' : 'border-input',
        disabled && 'bg-muted text-muted-foreground',
        Platform.select({ web: 'outline-none focus-visible:ring-2 focus-visible:ring-ring' }),
        className,
      )}
      {...props}
    />
  );
}

export { Input };
