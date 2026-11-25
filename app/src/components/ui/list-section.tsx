import { Children, type ReactNode } from 'react';
import { View } from 'react-native';
import { Text } from '@/components/ui/text';
import { cn } from '@/lib/utils';

type SectionTextProps = { children: string; className?: string };

function ListSectionHeader({ children, className }: SectionTextProps) {
  return (
    <Text role="heading" className={cn('px-4 text-muted-foreground', className)}>
      {children}
    </Text>
  );
}

function ListSectionFooter({ children, className }: SectionTextProps) {
  return (
    <Text className={cn('px-4 text-muted-foreground', className)}>
      {children}
    </Text>
  );
}

type ListSectionProps = {
  title?: string;
  footer?: string;
  children: ReactNode;
  className?: string;
};

function ListSection({ title, footer, children, className }: ListSectionProps) {
  const cells = Children.toArray(children);
  return (
    <View className={cn('gap-2', className)}>
      {title ? <ListSectionHeader>{title}</ListSectionHeader> : null}
      <View testID="list-section-card" className="overflow-hidden rounded-lg border-hairline border-border bg-card">
        {cells.map((cell, index) => (
          <View
            key={index}
            testID="list-section-cell"
            className={index > 0 ? 'border-t-hairline border-border' : undefined}
          >
            {cell}
          </View>
        ))}
      </View>
      {footer ? <ListSectionFooter>{footer}</ListSectionFooter> : null}
    </View>
  );
}

export { ListSection, ListSectionFooter, ListSectionHeader };
