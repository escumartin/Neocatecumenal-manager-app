import { Pressable, Switch } from 'react-native';
import { Text } from '@/components/ui/text';

type SwitchRowProps = {
  label: string;
  value: boolean;
  disabled?: boolean;
  onValueChange: (value: boolean) => void;
};

export function SwitchRow({ label, value, disabled = false, onValueChange }: SwitchRowProps) {
  return (
    <Pressable
      role="switch"
      accessibilityLabel={label}
      accessibilityState={{ checked: value, disabled }}
      disabled={disabled}
      onPress={() => onValueChange(!value)}
      className="min-h-12 flex-row items-center gap-3 bg-card px-4 py-3 active:bg-accent"
    >
      <Text variant="strong" className="min-w-0 flex-1">
        {label}
      </Text>
      <Switch accessible={false} value={value} disabled={disabled} onValueChange={onValueChange} />
    </Pressable>
  );
}
