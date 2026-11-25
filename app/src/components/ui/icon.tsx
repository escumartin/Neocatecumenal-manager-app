import { SymbolView, type SFSymbol } from 'expo-symbols';
import { useColorScheme } from 'react-native';
import { Text } from '@/components/ui/text';
import { themeColorsFor } from '@/lib/theme/colors';

const glyphs = {
  'chevron.right': '›',
  'person.2': '☺',
  'person.3': '☻',
  'door.left.hand.open': '▭',
  'key': '⚷',
  'rectangle.portrait.and.arrow.right': '⇥',
  'tray': '▤',
  'calendar': '▦',
} as const satisfies Partial<Record<SFSymbol, string>>;

type IconName = keyof typeof glyphs;

type IconProps = {
  name: IconName;
  size?: number;
  tone?: 'default' | 'primary';
  testID?: string;
};

function Icon({ name, size = 22, tone = 'default', testID }: IconProps) {
  const colors = themeColorsFor(useColorScheme());
  const tint = tone === 'primary' ? colors.primary : colors.text;
  return (
    <SymbolView
      testID={testID}
      name={name}
      size={size}
      tintColor={tint}
      fallback={
        <Text
          aria-hidden
          accessible={false}
          testID={testID}
          className="text-foreground"
          style={{ fontSize: size, lineHeight: size }}
        >
          {glyphs[name]}
        </Text>
      }
    />
  );
}

export { Icon };
export type { IconName };
