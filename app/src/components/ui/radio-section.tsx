import { View } from 'react-native';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';

type RadioOption<T extends string> = { value: T; label: string };

type RadioSectionProps<T extends string> = {
  title: string;
  options: RadioOption<T>[];
  value: T | null;
  onChange: (value: T) => void;
};

function RadioSection<T extends string>({ title, options, value, onChange }: RadioSectionProps<T>) {
  return (
    <View role="radiogroup" accessibilityLabel={title}>
      <ListSection title={title}>
        {options.map((option) => (
          <ListRow
            key={option.value}
            role="radio"
            title={option.label}
            selected={option.value === value}
            onPress={() => onChange(option.value)}
          />
        ))}
      </ListSection>
    </View>
  );
}

export { RadioSection };
export type { RadioOption };
