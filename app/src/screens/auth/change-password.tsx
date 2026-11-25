import { useRef, useState, type ComponentRef } from 'react';
import { TextInput } from 'react-native';
import { Button } from '@/components/ui/button';
import { FormField } from '@/components/ui/form-field';
import { FormScroll } from '@/components/ui/form-scroll';
import { Input } from '@/components/ui/input';
import { ListSection } from '@/components/ui/list-section';
import { Text } from '@/components/ui/text';
import { useSession } from '@/hooks/session/use-session';
import { identity } from '@/i18n/es/identity';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';

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
  const newPasswordRef = useRef<ComponentRef<typeof TextInput>>(null);
  const [problem, setProblem] = useState<Problem | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    if (currentPassword.length === 0) {
      setProblem({ field: 'currentPassword', message: text.fieldErrors.REQUIRED });
      hapticFailure();
      return;
    }
    if (newPassword.length === 0) {
      setProblem({ field: 'newPassword', message: text.fieldErrors.REQUIRED });
      hapticFailure();
      return;
    }
    setSubmitting(true);
    setProblem(null);
    let result;
    try {
      result = await changePassword(currentPassword, newPassword);
    } catch {
      setProblem({ field: null, message: identity.errors.UNKNOWN_ACTION });
      hapticFailure();
      return;
    } finally {
      setSubmitting(false);
    }
    if (result.ok) {
      hapticSuccess();
      onDone();
      return;
    }
    hapticFailure();
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
    <FormScroll>
      <Text variant="display">{text.title}</Text>
      <Text variant="muted">{text.intro}</Text>
      <ListSection title={text.currentLabel}>
        <FormField>
          <Input
            accessibilityLabel={text.currentLabel}
            accessibilityHint={fieldMessage('currentPassword')}
            secureTextEntry={!visible}
            autoComplete="current-password"
            autoCapitalize="none"
            autoCorrect={false}
            maxLength={MAX_PASSWORD_LENGTH}
            returnKeyType="next"
            submitBehavior="submit"
            value={currentPassword}
            onChangeText={setCurrentPassword}
            onSubmitEditing={() => newPasswordRef.current?.focus()}
          />
          {alert('currentPassword')}
        </FormField>
      </ListSection>
      <ListSection title={text.newLabel}>
        <FormField>
          <Input
            ref={newPasswordRef}
            accessibilityLabel={text.newLabel}
            accessibilityHint={fieldMessage('newPassword')}
            secureTextEntry={!visible}
            autoComplete="new-password"
            autoCapitalize="none"
            autoCorrect={false}
            maxLength={MAX_PASSWORD_LENGTH}
            returnKeyType="done"
            value={newPassword}
            onChangeText={setNewPassword}
            onSubmitEditing={submit}
          />
          {alert('newPassword')}
        </FormField>
      </ListSection>
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
    </FormScroll>
  );
}
