import { View } from 'react-native';
import { Text } from '@/components/ui/text';

type StatusTone = 'success' | 'warning' | 'destructive' | 'neutral';

const toneStyle: Record<StatusTone, { glyph: string; text: string }> = {
  success: { glyph: '✓', text: 'text-success' },
  warning: { glyph: '◷', text: 'text-warning' },
  destructive: { glyph: '✕', text: 'text-destructive' },
  neutral: { glyph: '–', text: 'text-muted-foreground' },
};

type StatusBadgeProps = { tone: StatusTone; label: string };

function StatusBadge({ tone, label }: StatusBadgeProps) {
  const style = toneStyle[tone];
  return (
    <View className="flex-row items-center gap-1">
      <Text aria-hidden testID="status-badge-glyph" variant="strong" className={style.text}>
        {style.glyph}
      </Text>
      <Text variant="strong" className={style.text}>
        {label}
      </Text>
    </View>
  );
}

export { StatusBadge };
export type { StatusTone };
