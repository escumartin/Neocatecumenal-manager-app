import type { ReactNode } from 'react';
import { View } from 'react-native';
import { Text } from '@/components/ui/text';

type FormFieldProps = {
  label?: string;
  description?: string;
  children: ReactNode;
};

function FormField({ label, description, children }: FormFieldProps) {
  return (
    <View className="gap-2 p-4">
      {label ? <Text variant="strong">{label}</Text> : null}
      {description ? <Text>{description}</Text> : null}
      {children}
    </View>
  );
}

export { FormField };
