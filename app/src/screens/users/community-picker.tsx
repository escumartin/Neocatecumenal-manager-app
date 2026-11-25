import { View } from 'react-native';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { StateView } from '@/components/ui/state-view';
import { useCommunities } from '@/hooks/communities/use-communities';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { communitiesText } from '@/i18n/es/communities';
import type { CommunitiesApi } from '@/lib/api/communities-api';

type Props = {
  selectedId: string | null;
  onSelect: (communityId: string) => void;
  api?: CommunitiesApi;
};

export function CommunityPicker({ selectedId, onSelect, api }: Props) {
  const { canChooseCommunity } = useCurrentUser();
  const { status, communities, reload } = useCommunities(api, canChooseCommunity);

  if (!canChooseCommunity) {
    return null;
  }

  return (
    <View role="radiogroup" accessibilityLabel={communitiesText.pickerTitle} className="gap-3">
      {status === 'loading' ? <StateView kind="loading" message={communitiesText.loading} /> : null}
      {status === 'error' ? (
        <StateView
          kind="error"
          message={communitiesText.loadError}
          retryLabel={communitiesText.retry}
          onRetry={() => void reload()}
        />
      ) : null}
      {communities.length > 0 ? (
        <ListSection title={communitiesText.pickerTitle}>
          {communities.map((community) => (
            <ListRow
              key={community.id}
              role="radio"
              title={communitiesText.option(community.number)}
              selected={community.id === selectedId}
              onPress={() => onSelect(community.id)}
            />
          ))}
        </ListSection>
      ) : null}
    </View>
  );
}
