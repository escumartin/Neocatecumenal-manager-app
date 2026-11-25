import type { ReactNode } from 'react';
import { Platform, Pressable, View, useWindowDimensions, type AccessibilityActionEvent, type AccessibilityActionInfo } from 'react-native';
import { Icon, type IconName } from '@/components/ui/icon';
import { Text } from '@/components/ui/text';
import { cn } from '@/lib/utils';

type RowPosition = 'first' | 'middle' | 'last' | 'only';

type ListRowProps = {
  title: string;
  subtitle?: string;
  trailing?: string;
  icon?: IconName;
  destructive?: boolean;
  disabled?: boolean;
  accessory?: 'chevron' | 'none';
  role?: 'button' | 'radio';
  selected?: boolean;
  children?: ReactNode;
  position?: RowPosition;
  onPress?: () => void;
  onLongPress?: () => void;
  accessibilityLabel?: string;
  accessibilityHint?: string;
  accessibilityActions?: AccessibilityActionInfo[];
  onAccessibilityAction?: (event: AccessibilityActionEvent) => void;
};

const STACK_TRAILING_FONT_SCALE = 1.3;

const positionClasses: Record<RowPosition, string> = {
  first: 'rounded-t-lg',
  middle: 'border-t-hairline border-border',
  last: 'rounded-b-lg border-t-hairline border-border',
  only: 'rounded-lg',
};

function rowPosition(index: number, count: number): RowPosition {
  if (count <= 1) {
    return 'only';
  }
  if (index === 0) {
    return 'first';
  }
  return index === count - 1 ? 'last' : 'middle';
}

function ListRow({
  title,
  subtitle,
  trailing,
  icon,
  destructive = false,
  disabled = false,
  accessory = destructive ? 'none' : 'chevron',
  role = 'button',
  selected,
  children,
  position,
  onPress,
  onLongPress,
  accessibilityLabel,
  accessibilityHint,
  accessibilityActions,
  onAccessibilityAction,
}: ListRowProps) {
  const { fontScale } = useWindowDimensions();
  const stackTrailing = fontScale > STACK_TRAILING_FONT_SCALE;
  const pressable = onPress !== undefined;
  const label = accessibilityLabel ?? [title, subtitle, trailing].filter(Boolean).join(', ');
  const classes = cn(
    'min-h-12 flex-row items-center gap-3 bg-card px-4 py-3',
    position ? positionClasses[position] : undefined,
    pressable && 'active:bg-accent',
    pressable && Platform.select({ web: 'cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-ring' }),
  );
  const content = (
    <>
      {icon ? (
        <View testID="list-row-icon" accessible={false}>
          <Icon name={icon} tone="primary" />
        </View>
      ) : null}
      <View testID="list-row-body" className="min-w-0 flex-1">
        <Text variant="strong" className={destructive ? 'text-destructive' : undefined}>
          {title}
        </Text>
        {subtitle ? <Text className="text-muted-foreground">{subtitle}</Text> : null}
        {trailing && stackTrailing ? <Text className="text-muted-foreground">{trailing}</Text> : null}
        {children}
      </View>
      {trailing && !stackTrailing ? <Text className="shrink text-right text-muted-foreground">{trailing}</Text> : null}
      {role === 'radio' && selected ? (
        <Text aria-hidden testID="list-row-check" className="text-primary">
          ✓
        </Text>
      ) : null}
      {pressable && role === 'button' && accessory === 'chevron' ? (
        <View testID="list-row-chevron" accessible={false}>
          <Icon name="chevron.right" size={16} />
        </View>
      ) : null}
    </>
  );

  if (!pressable) {
    return (
      <View
        testID="list-row"
        accessible={children === undefined}
        accessibilityLabel={children === undefined ? label : undefined}
        className={classes}
      >
        {content}
      </View>
    );
  }

  return (
    <Pressable
      testID="list-row"
      role={role}
      aria-checked={role === 'radio' ? (selected ?? false) : undefined}
      disabled={disabled}
      accessibilityState={role === 'radio' ? { checked: selected ?? false, disabled } : { disabled }}
      accessibilityLabel={label}
      accessibilityHint={accessibilityHint}
      accessibilityActions={accessibilityActions}
      onAccessibilityAction={onAccessibilityAction}
      onPress={onPress}
      onLongPress={onLongPress}
      className={classes}
    >
      {content}
    </Pressable>
  );
}

export { ListRow, rowPosition };
export type { ListRowProps, RowPosition };
