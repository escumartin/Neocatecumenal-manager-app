import { Text } from '@/components/ui/text';

type Props = { message: string | null | undefined };

export function FieldProblem({ message }: Props) {
  if (!message) {
    return null;
  }
  return (
    <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
      {`⚠ ${message}`}
    </Text>
  );
}
