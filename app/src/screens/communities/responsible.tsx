import { useState } from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { FormScroll } from '@/components/ui/form-scroll';
import { RadioSection } from '@/components/ui/radio-section';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useCommunityResponsible } from '@/hooks/communities/use-community-responsible';
import { communitiesText, messageForCommunitiesFailure } from '@/i18n/es/communities';
import type { CommunityDetail, CommunityManagementApi, CommunityResult } from '@/lib/api/communities-api';
import type { Member, MembersApi } from '@/lib/api/members-api';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { fullNameOf } from '@/screens/members/member-name';

type Props = {
  communityId: string;
  api?: CommunityManagementApi;
  membersApi?: MembersApi;
  onSaved: () => void;
};

export function CommunityResponsible({ communityId, api, membersApi, onSaved }: Props) {
  const { status, community, members, reload, designate } = useCommunityResponsible(communityId, api, membersApi);

  if (status === 'loading') {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView kind="loading" message={communitiesText.responsible.loading} />
      </View>
    );
  }

  if (status === 'error' || community === null) {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView
          kind="error"
          message={communitiesText.responsible.loadError}
          retryLabel={communitiesText.retry}
          onRetry={() => void reload()}
        />
      </View>
    );
  }

  return <ResponsibleChoice community={community} members={members} designate={designate} onSaved={onSaved} />;
}

type ChoiceProps = {
  community: CommunityDetail;
  members: Member[];
  designate: (memberId: string) => Promise<CommunityResult>;
  onSaved: () => void;
};

function ResponsibleChoice({ community, members, designate, onSaved }: ChoiceProps) {
  const currentId = community.responsible?.id ?? null;
  const [choice, setChoice] = useState<string | null>(currentId);
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const texts = communitiesText.responsible;

  async function submit() {
    if (choice === null) {
      return;
    }
    setSubmitting(true);
    setMessage(null);
    try {
      const result = await designate(choice);
      if (result.ok) {
        hapticSuccess();
        onSaved();
        return;
      }
      hapticFailure();
      setMessage(messageForCommunitiesFailure(result));
    } catch {
      hapticFailure();
      setMessage(communitiesText.errors.UNKNOWN);
    } finally {
      setSubmitting(false);
    }
  }

  if (members.length === 0) {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView kind="empty" message={texts.noMembers} />
      </View>
    );
  }

  return (
    <FormScroll>
      <Text variant="title">{texts.title}</Text>
      {community.hasResponsible && !community.responsibleIsModerator ? <Text>{texts.notModerator}</Text> : null}
      <RadioSection
        title={texts.choose}
        options={members.map((member) => ({
          value: member.id,
          label: fullNameOf(member),
        }))}
        value={choice}
        onChange={setChoice}
      />
      <Text className="text-muted-foreground">{texts.hint}</Text>
      {message ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${message}`}
        </Text>
      ) : null}
      <Button
        accessibilityLabel={texts.save}
        disabled={submitting || choice === null || choice === currentId}
        onPress={submit}
      >
        <Text>{submitting ? texts.saving : texts.save}</Text>
      </Button>
    </FormScroll>
  );
}
