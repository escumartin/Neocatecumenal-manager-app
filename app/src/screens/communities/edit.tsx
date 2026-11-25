import { useState } from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { FormField } from '@/components/ui/form-field';
import { FormScroll } from '@/components/ui/form-scroll';
import { Input } from '@/components/ui/input';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useCommunity } from '@/hooks/communities/use-community';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { useCommunitySave } from '@/hooks/communities/use-community-save';
import { communitiesText, messageForCommunitiesFailure } from '@/i18n/es/communities';
import type { CommunitiesApi, CommunityDetail, CommunityManagementApi } from '@/lib/api/communities-api';
import { parseCommunityNumber } from '@/lib/communities/parse-community-number';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { CommunityActions } from './actions';

type Api = CommunitiesApi & CommunityManagementApi;

type Props = {
  communityId?: string;
  api?: Api;
  onSaved: () => void;
  onDeleted?: () => void;
  onChooseResponsible?: (communityId: string) => void;
};

type Problem = { field: 'number' | 'name' | null; message: string };

export function CommunityEdit({ communityId, api, onSaved, onDeleted, onChooseResponsible }: Props) {
  if (communityId) {
    return (
      <ExistingCommunity
        communityId={communityId}
        api={api}
        onSaved={onSaved}
        onDeleted={onDeleted}
        onChooseResponsible={onChooseResponsible}
      />
    );
  }
  return <CommunityFields initial={null} api={api} onSaved={onSaved} />;
}

type ExistingProps = Omit<Props, 'communityId'> & { communityId: string };

function ExistingCommunity({ communityId, api, onSaved, onDeleted, onChooseResponsible }: ExistingProps) {
  const { status, community, reload, refresh, deactivate, reactivate, remove } = useCommunity(communityId, api);
  useReloadOnFocus(refresh);

  if (status === 'loading') {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView kind="loading" message={communitiesText.file.loading} />
      </View>
    );
  }

  if (status === 'error' || community === null) {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView
          kind="error"
          message={communitiesText.file.loadError}
          retryLabel={communitiesText.retry}
          onRetry={() => void reload()}
        />
      </View>
    );
  }

  return (
    <CommunityFields initial={community} api={api} onSaved={onSaved} onChooseResponsible={onChooseResponsible}>
      <CommunityActions
        community={community}
        deactivate={deactivate}
        reactivate={reactivate}
        remove={remove}
        onDeleted={onDeleted ?? onSaved}
      />
    </CommunityFields>
  );
}

type FieldsProps = {
  initial: CommunityDetail | null;
  api?: Api;
  onSaved: () => void;
  onChooseResponsible?: (communityId: string) => void;
  children?: React.ReactNode;
};

function responsibleName(community: CommunityDetail): string | null {
  return community.responsible
    ? [community.responsible.givenName, community.responsible.surnames].filter(Boolean).join(' ')
    : null;
}

function CommunityFields({ initial, api, onSaved, onChooseResponsible, children }: FieldsProps) {
  const { save } = useCommunitySave(initial?.id, api);
  const [number, setNumber] = useState(initial ? String(initial.number) : '');
  const [name, setName] = useState(initial?.name ?? '');
  const [problem, setProblem] = useState<Problem | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const editing = initial !== null;

  function fail(next: Problem) {
    setProblem(next);
    hapticFailure();
  }

  async function submit() {
    const parsed = parseCommunityNumber(number);
    if (!parsed.ok) {
      fail({ field: 'number', message: communitiesText.fieldErrors[parsed.code] });
      return;
    }
    const trimmedName = name.trim();
    setSubmitting(true);
    setProblem(null);
    try {
      const result = await save({ number: parsed.value, ...(trimmedName ? { name: trimmedName } : {}) });
      if (result.ok) {
        hapticSuccess();
        onSaved();
        return;
      }
      const reported = result.fieldErrors?.[0]?.field;
      fail({ field: reported === 'number' || reported === 'name' ? reported : null, message: messageForCommunitiesFailure(result) });
    } catch {
      fail({ field: null, message: communitiesText.errors.UNKNOWN });
    } finally {
      setSubmitting(false);
    }
  }

  const numberProblem = problem?.field === 'number' ? problem.message : undefined;
  const nameProblem = problem?.field === 'name' ? problem.message : undefined;

  return (
    <FormScroll>
      <Text variant="title">{editing ? communitiesText.form.editTitle : communitiesText.form.createTitle}</Text>
      {initial?.hasResponsible === false ? <Text>{communitiesText.list.noResponsible}</Text> : null}
      {initial?.hasResponsible && !initial.responsibleIsModerator ? (
        <Text>{communitiesText.responsible.notModerator}</Text>
      ) : null}
      {initial?.status === 'DEACTIVATED' ? <Text>{communitiesText.file.statusDeactivated}</Text> : null}
      <ListSection title={communitiesText.form.number}>
        <FormField description={communitiesText.form.numberHint}>
          <Input
            accessibilityLabel={communitiesText.form.number}
            accessibilityHint={numberProblem}
            invalid={numberProblem !== undefined}
            keyboardType="number-pad"
            returnKeyType="next"
            value={number}
            onChangeText={setNumber}
          />
          {numberProblem ? (
            <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
              {`⚠ ${numberProblem}`}
            </Text>
          ) : null}
        </FormField>
      </ListSection>
      <ListSection title={communitiesText.form.name}>
        <FormField description={communitiesText.form.nameHint}>
          <Input
            accessibilityLabel={communitiesText.form.name}
            accessibilityHint={nameProblem}
            invalid={nameProblem !== undefined}
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
      {problem && problem.field === null ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${problem.message}`}
        </Text>
      ) : null}
      <Button
        accessibilityLabel={editing ? communitiesText.form.save : communitiesText.form.create}
        disabled={submitting}
        onPress={submit}
      >
        <Text>
          {submitting
            ? communitiesText.form.saving
            : editing
              ? communitiesText.form.save
              : communitiesText.form.create}
        </Text>
      </Button>
      {initial && onChooseResponsible ? (
        <ListSection>
          <ListRow
            title={initial.hasResponsible ? communitiesText.responsible.changeRow : communitiesText.responsible.designateRow}
            subtitle={responsibleName(initial) ?? undefined}
            onPress={() => onChooseResponsible(initial.id)}
          />
        </ListSection>
      ) : null}
      {children}
    </FormScroll>
  );
}
