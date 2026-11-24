import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import { useSession } from '@/hooks/session/use-session';
import { identity } from '@/i18n/es/identity';

const MAX_PASSWORD_LENGTH = 64;

type FieldName = 'currentPassword' | 'newPassword';

type Problem = { field: FieldName | null; message: string };

type Props = {
  onDone: () => void;
  onCancel?: () => void;
};

export function ChangePassword({ onDone, onCancel }: Props) {
  const { me, changePassword } = useSession();
  const required = me?.pendingSteps.includes('PASSWORD_CHANGE') ?? false;
  const text = identity.changePassword;
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [visible, setVisible] = useState(false);
  const [problem, setProblem] = useState<Problem | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    if (currentPassword.length === 0) {
      setProblem({ field: 'currentPassword', message: text.fieldErrors.REQUIRED });
      return;
    }
    if (newPassword.length === 0) {
      setProblem({ field: 'newPassword', message: text.fieldErrors.REQUIRED });
      return;
    }
    setSubmitting(true);
    setProblem(null);
    let result;
    try {
      result = await changePassword(currentPassword, newPassword);
    } catch {
      setProblem({ field: null, message: identity.errors.UNKNOWN_ACTION });
      return;
    } finally {
      setSubmitting(false);
    }
    if (result.ok) {
      onDone();
      return;
    }
    const fieldError = result.fieldErrors?.[0];
    const message = fieldError && text.fieldErrors[fieldError.code];
    if (fieldError && message) {
      setProblem({ field: fieldError.field === 'currentPassword' ? 'currentPassword' : 'newPassword', message });
      return;
    }
    setProblem({ field: null, message: identity.errors.UNKNOWN_ACTION });
  }

  function fieldMessage(field: FieldName) {
    return problem?.field === field ? problem.message : undefined;
  }

  function alert(field: FieldName | null) {
    return problem && problem.field === field ? (
      <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
        {`⚠ ${problem.message}`}
      </Text>
    ) : null;
  }

  return (
    <ScrollView
      className="flex-1 bg-background"
      contentContainerClassName="gap-6 p-6"
      keyboardShouldPersistTaps="handled"
    >
      <Text variant="display">{text.title}</Text>
      <Text variant="muted">{text.intro}</Text>
      <View className="gap-2">
        <Text variant="strong">{text.currentLabel}</Text>
        <Input
          accessibilityLabel={text.currentLabel}
          accessibilityHint={fieldMessage('currentPassword')}
          secureTextEntry={!visible}
          autoComplete="current-password"
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={MAX_PASSWORD_LENGTH}
          value={currentPassword}
          onChangeText={setCurrentPassword}
        />
        {alert('currentPassword')}
      </View>
      <View className="gap-2">
        <Text variant="strong">{text.newLabel}</Text>
        <Input
          accessibilityLabel={text.newLabel}
          accessibilityHint={fieldMessage('newPassword')}
          secureTextEntry={!visible}
          autoComplete="new-password"
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={MAX_PASSWORD_LENGTH}
          value={newPassword}
          onChangeText={setNewPassword}
          onSubmitEditing={submit}
        />
        {alert('newPassword')}
      </View>
      <Button
        variant="ghost"
        accessibilityLabel={visible ? text.hide : text.show}
        onPress={() => setVisible((current) => !current)}
      >
        <Text>{visible ? text.hide : text.show}</Text>
      </Button>
      {alert(null)}
      <Button accessibilityLabel={text.submit} disabled={submitting} onPress={submit}>
        <Text>{submitting ? text.submitting : text.submit}</Text>
      </Button>
      {onCancel && !required ? (
        <Button variant="outline" accessibilityLabel={text.cancel} onPress={onCancel}>
          <Text>{text.cancel}</Text>
        </Button>
      ) : null}
    </ScrollView>
  );
}
