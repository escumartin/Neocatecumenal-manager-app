import { Pressable } from 'react-native';
import { Text } from '@/components/ui/text';

type Props = {
  label: string;
  onPress: () => void;
  visibleText?: string;
};

export function HeaderButton({ label, onPress, visibleText }: Props) {
  return (
    <Pressable
      role="button"
      accessibilityLabel={label}
      onPress={onPress}
      hitSlop={8}
      className="min-h-12 min-w-12 items-center justify-center px-2 active:opacity-60"
    >
      <Text className="text-primary">{visibleText ?? label}</Text>
    </Pressable>
  );
}
