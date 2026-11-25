import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { useUser } from '@/hooks/users/use-user';
import { useUsers } from '@/hooks/users/use-users';
import { messageForUsersFailure, usersText } from '@/i18n/es/users';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { CreatedUser, User, UsersApi, UsersFailure } from '@/lib/api/users-api';
import { CommunityPicker } from './community-picker';
import { PasswordReset } from './password-reset';

type Role = User['role'];

type Problem = { field: 'name' | null; message: string };

const ROLES: Role[] = ['REGISTERED', 'MODERATOR', 'ADMINISTRATOR'];

type Props = {
  userId?: string;
  api?: UsersApi;
  communitiesApi?: CommunitiesApi;
  onCreated: (created: CreatedUser) => void;
  onSaved: () => void;
  onPasswordReset?: (created: CreatedUser) => void;
};

export function UserEdit({ userId, api, communitiesApi, onCreated, onSaved, onPasswordReset }: Props) {
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
    />
  );
}

type FieldsProps = Omit<Props, 'userId'> & {
  initial: User | null;
  save: ReturnType<typeof useUser>['save'];
};

function UserFields({ initial, api, communitiesApi, save, onCreated, onSaved, onPasswordReset }: FieldsProps) {
  const { user: me, isAdministrator } = useCurrentUser();
  const canResetPassword =
    initial !== null && initial.id !== me?.userId && (isAdministrator || initial.role === 'REGISTERED');
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
      return;
    }
    if (communityId === null) {
      setProblem({ field: null, message: usersText.fieldErrors.COMMUNITY_REQUIRED });
      return;
    }
    setSubmitting(true);
    setProblem(null);
    const input = { name: name.trim(), role, communityId };
    try {
      if (editing) {
        const result = await save(input);
        if (result.ok) {
          onSaved();
        } else {
          setProblem(problemFor(result));
        }
      } else {
        const result = await create(input);
        if (result.ok) {
          onCreated(result.user);
        } else {
          setProblem(problemFor(result));
        }
      }
    } catch {
      setProblem({ field: null, message: usersText.errors.UNKNOWN });
    } finally {
      setSubmitting(false);
    }
  }

  const nameProblem = problem?.field === 'name' ? problem.message : undefined;

  return (
    <ScrollView
      className="flex-1 bg-background"
      contentContainerClassName="gap-6 p-6"
      contentInsetAdjustmentBehavior="automatic"
      keyboardShouldPersistTaps="handled"
    >
      <Text variant="title">{editing ? usersText.form.editTitle : usersText.form.createTitle}</Text>
      <View className="gap-2">
        <Text variant="strong">{usersText.form.name}</Text>
        <Input
          accessibilityLabel={usersText.form.name}
          accessibilityHint={nameProblem}
          invalid={nameProblem !== undefined}
          autoComplete="name"
          maxLength={100}
          value={name}
          onChangeText={setName}
        />
        {nameProblem ? (
          <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
            {`⚠ ${nameProblem}`}
          </Text>
        ) : null}
      </View>
      {isAdministrator ? (
        <View role="radiogroup" accessibilityLabel={usersText.form.role} className="gap-3">
          <Text variant="heading">{usersText.form.role}</Text>
          {ROLES.map((option) => {
            const label = usersText.roles[option];
            const selected = option === role;
            return (
              <Button
                key={option}
                role="radio"
                variant={selected ? 'default' : 'outline'}
                accessibilityLabel={label}
                aria-checked={selected}
                accessibilityState={{ checked: selected }}
                onPress={() => setRole(option)}
              >
                <Text>{selected ? `✓ ${label}` : label}</Text>
              </Button>
            );
          })}
        </View>
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
      {initial && onPasswordReset && canResetPassword ? (
        <PasswordReset user={initial} api={api} onReset={onPasswordReset} />
      ) : null}
    </ScrollView>
  );
}
