import { useState } from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { FormField } from '@/components/ui/form-field';
import { FormScroll } from '@/components/ui/form-scroll';
import { Input } from '@/components/ui/input';
import { ListSection } from '@/components/ui/list-section';
import { RadioSection } from '@/components/ui/radio-section';
import { Text } from '@/components/ui/text';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { useUser } from '@/hooks/users/use-user';
import { useUsers } from '@/hooks/users/use-users';
import { messageForUsersFailure, usersText } from '@/i18n/es/users';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { CreatedUser, User, UsersApi, UsersFailure } from '@/lib/api/users-api';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { canManageAccount } from '@/lib/users/can-manage-account';
import { CommunityPicker } from './community-picker';
import { DeleteUser } from './delete-user';
import { PasswordReset } from './password-reset';

type Role = User['role'];

type Problem = { field: 'name' | null; message: string };

const ROLE_OPTIONS = (['REGISTERED', 'MODERATOR', 'ADMINISTRATOR'] as Role[]).map((value) => ({
  value,
  label: usersText.roles[value],
}));

type Props = {
  userId?: string;
  api?: UsersApi;
  communitiesApi?: CommunitiesApi;
  onCreated: (created: CreatedUser) => void;
  onSaved: () => void;
  onPasswordReset?: (created: CreatedUser) => void;
  onDeleted?: () => void;
};

export function UserEdit({ userId, api, communitiesApi, onCreated, onSaved, onPasswordReset, onDeleted }: Props) {
  const { status, user, save } = useUser(userId, api);

  if (status === 'loading') {
    return (
      <View className="flex-1 bg-background p-6">
        <Text role="status" variant="muted">
          {usersText.loading}
        </Text>
      </View>
    );
  }

  if (status === 'error') {
    return (
      <View className="flex-1 bg-background p-6">
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${usersText.loadOneError}`}
        </Text>
      </View>
    );
  }

  return (
    <UserFields
      initial={user}
      api={api}
      communitiesApi={communitiesApi}
      save={save}
      onCreated={onCreated}
      onSaved={onSaved}
      onPasswordReset={onPasswordReset}
      onDeleted={onDeleted}
    />
  );
}

type FieldsProps = Omit<Props, 'userId'> & {
  initial: User | null;
  save: ReturnType<typeof useUser>['save'];
};

function UserFields({ initial, api, communitiesApi, save, onCreated, onSaved, onPasswordReset, onDeleted }: FieldsProps) {
  const { user: me, isAdministrator } = useCurrentUser();
  const canManage = initial !== null && canManageAccount(me, initial);
  const { create } = useUsers(null, api, false);
  const [name, setName] = useState(initial?.name ?? '');
  const [role, setRole] = useState<Role>(initial?.role ?? 'REGISTERED');
  const [chosenCommunityId, setCommunityId] = useState<string | null>(null);
  const communityId = chosenCommunityId ?? initial?.communityId ?? me?.communityId ?? null;
  const [problem, setProblem] = useState<Problem | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const editing = initial !== null;

  function problemFor(failure: UsersFailure): Problem {
    return {
      field: failure.fieldErrors?.[0]?.field === 'name' ? 'name' : null,
      message: messageForUsersFailure(failure),
    };
  }

  async function submit() {
    if (name.trim().length === 0) {
      setProblem({ field: 'name', message: usersText.fieldErrors.REQUIRED });
      hapticFailure();
      return;
    }
    if (communityId === null) {
      setProblem({ field: null, message: usersText.fieldErrors.COMMUNITY_REQUIRED });
      hapticFailure();
      return;
    }
    setSubmitting(true);
    setProblem(null);
    const input = { name: name.trim(), role, communityId };
    try {
      if (editing) {
        const result = await save(input);
        if (result.ok) {
          hapticSuccess();
          onSaved();
        } else {
          setProblem(problemFor(result));
          hapticFailure();
        }
      } else {
        const result = await create(input);
        if (result.ok) {
          hapticSuccess();
          onCreated(result.user);
        } else {
          setProblem(problemFor(result));
          hapticFailure();
        }
      }
    } catch {
      setProblem({ field: null, message: usersText.errors.UNKNOWN });
      hapticFailure();
    } finally {
      setSubmitting(false);
    }
  }

  const nameProblem = problem?.field === 'name' ? problem.message : undefined;

  return (
    <FormScroll>
      <Text variant="title">{editing ? usersText.form.editTitle : usersText.form.createTitle}</Text>
      <ListSection title={usersText.form.name}>
        <FormField>
          <Input
            accessibilityLabel={usersText.form.name}
            accessibilityHint={nameProblem}
            invalid={nameProblem !== undefined}
            autoComplete="name"
            maxLength={100}
            returnKeyType="done"
            value={name}
            onChangeText={setName}
            onSubmitEditing={submit}
          />
          {nameProblem ? (
            <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
              {`⚠ ${nameProblem}`}
            </Text>
          ) : null}
        </FormField>
      </ListSection>
      {isAdministrator ? (
        <RadioSection title={usersText.form.role} options={ROLE_OPTIONS} value={role} onChange={setRole} />
      ) : null}
      <CommunityPicker selectedId={communityId} onSelect={setCommunityId} api={communitiesApi} />
      {problem && problem.field === null ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${problem.message}`}
        </Text>
      ) : null}
      <Button
        accessibilityLabel={editing ? usersText.form.save : usersText.form.create}
        disabled={submitting}
        onPress={submit}
      >
        <Text>{submitting ? usersText.form.saving : editing ? usersText.form.save : usersText.form.create}</Text>
      </Button>
      {initial && onPasswordReset && canManage ? (
        <PasswordReset user={initial} api={api} onReset={onPasswordReset} />
      ) : null}
      {initial && onDeleted && canManage ? (
        <DeleteUser user={initial} api={api} onDeleted={onDeleted} />
      ) : null}
    </FormScroll>
  );
}
