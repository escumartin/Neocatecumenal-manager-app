import * as AlertDialogPrimitive from '@rn-primitives/alert-dialog';
import { Platform, View } from 'react-native';
import { cn } from '@/lib/utils';

const AlertDialog = AlertDialogPrimitive.Root;
const AlertDialogCancel = AlertDialogPrimitive.Cancel;

function AlertDialogContent({ className, children, ...props }: AlertDialogPrimitive.ContentProps) {
  return (
    <AlertDialogPrimitive.Portal>
      <View
        className={cn(
          'absolute inset-0 items-center justify-center p-6',
          Platform.select({ web: 'fixed z-50' }),
        )}
      >
        <AlertDialogPrimitive.Overlay className="absolute inset-0 bg-foreground opacity-50" />
        <AlertDialogPrimitive.Content
          className={cn('w-full max-w-lg gap-4 rounded-lg border-2 border-destructive bg-background p-6', className)}
          {...props}
        >
          {children}
        </AlertDialogPrimitive.Content>
      </View>
    </AlertDialogPrimitive.Portal>
  );
}

function AlertDialogTitle({ className, ...props }: AlertDialogPrimitive.TitleProps) {
  return (
    <AlertDialogPrimitive.Title
      className={cn('font-sans-semibold text-heading text-foreground', className)}
      {...props}
    />
  );
}

function AlertDialogDescription({ className, ...props }: AlertDialogPrimitive.DescriptionProps) {
  return (
    <AlertDialogPrimitive.Description className={cn('font-sans text-body text-foreground', className)} {...props} />
  );
}

export { AlertDialog, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogTitle };
