import { Text } from '@/components/ui/text';

type Props = { message: string | null | undefined; live?: boolean };

export function FieldProblem({ message, live = false }: Props) {
  if (!message) {
    return null;
  }
  return (
    <Text
      {...(live ? {} : { role: 'alert' as const })}
      accessibilityLiveRegion={live ? 'polite' : 'assertive'}
      className="text-destructive"
    >
      {`⚠ ${message}`}
    </Text>
  );
}
