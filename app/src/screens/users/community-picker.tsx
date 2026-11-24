import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
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
      <Text variant="heading">{communitiesText.pickerTitle}</Text>
      {status === 'loading' ? <Text variant="muted">{communitiesText.loading}</Text> : null}
      {status === 'error' ? (
        <View className="gap-3">
          <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
            {`⚠ ${communitiesText.loadError}`}
          </Text>
          <Button variant="outline" accessibilityLabel={communitiesText.retry} onPress={() => void reload()}>
            <Text>{communitiesText.retry}</Text>
          </Button>
        </View>
      ) : null}
      {communities.map((community) => {
        const label = communitiesText.option(community.number);
        const selected = community.id === selectedId;
        return (
          <Button
            key={community.id}
            role="radio"
            variant={selected ? 'default' : 'outline'}
            accessibilityLabel={label}
            aria-checked={selected}
            accessibilityState={{ checked: selected }}
            onPress={() => onSelect(community.id)}
          >
            <Text>{selected ? `✓ ${label}` : label}</Text>
          </Button>
        );
      })}
    </View>
  );
}
