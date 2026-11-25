import type { ReactNode } from 'react';
import { ActivityIndicator, View, useColorScheme } from 'react-native';
import { Button } from '@/components/ui/button';
import { Icon, type IconName } from '@/components/ui/icon';
import { Text } from '@/components/ui/text';
import { themeColorsFor } from '@/lib/theme/colors';

type StateViewProps = {
  kind: 'loading' | 'empty' | 'error';
  message: string;
  icon?: IconName;
  retryLabel?: string;
  onRetry?: () => void;
  children?: ReactNode;
};

function StateView({ kind, message, icon, retryLabel, onRetry, children }: StateViewProps) {
  const colors = themeColorsFor(useColorScheme());

  if (kind === 'loading') {
    return (
      <View className="flex-row items-center justify-center gap-3 p-6">
        <ActivityIndicator testID="state-view-spinner" color={colors.primary} />
        <Text role="status" className="text-muted-foreground">
          {message}
        </Text>
      </View>
    );
  }

  if (kind === 'error') {
    return (
      <View className="gap-3 rounded-lg border-hairline border-destructive bg-card p-4">
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${message}`}
        </Text>
        {onRetry && retryLabel ? (
          <Button variant="outline" accessibilityLabel={retryLabel} onPress={onRetry}>
            <Text>{retryLabel}</Text>
          </Button>
        ) : null}
      </View>
    );
  }

  return (
    <View className="items-center gap-3 p-6">
      {icon ? <Icon name={icon} size={32} testID="state-view-icon" /> : null}
      <Text className="text-center text-muted-foreground">
        {message}
      </Text>
      {children}
    </View>
  );
}

export { StateView };
export type { StateViewProps };
