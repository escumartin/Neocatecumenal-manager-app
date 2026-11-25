import { useState } from 'react';
import { Share, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { FormScroll } from '@/components/ui/form-scroll';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useMemberDataExport } from '@/hooks/members/use-member-data-export';
import { membersText } from '@/i18n/es/members';
import type { MemberManagementApi } from '@/lib/api/members-api';
import { dataExportText } from '@/lib/members/data-export-text';

type Props = {
  memberId: string;
  api?: Pick<MemberManagementApi, 'exportData'>;
};

export function MemberDataExportScreen({ memberId, api }: Props) {
  const { status, data, reload } = useMemberDataExport(memberId, api);
  const [shareFailed, setShareFailed] = useState(false);

  if (status === 'loading') {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView kind="loading" message={membersText.dataExport.loading} />
      </View>
    );
  }

  if (status === 'error' || data === null) {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView
          kind="error"
          message={membersText.dataExport.loadError}
          retryLabel={membersText.retry}
          onRetry={reload}
        />
      </View>
    );
  }

  const content = dataExportText(data);

  async function share() {
    setShareFailed(false);
    try {
      await Share.share({ message: content, title: membersText.dataExport.title });
    } catch {
      setShareFailed(true);
    }
  }

  return (
    <FormScroll>
      <Text variant="title">{membersText.dataExport.title}</Text>
      <Text selectable>{content}</Text>
      {shareFailed ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${membersText.dataExport.shareError}`}
        </Text>
      ) : null}
      <Button accessibilityLabel={membersText.dataExport.share} onPress={() => void share()}>
        <Text>{membersText.dataExport.share}</Text>
      </Button>
    </FormScroll>
  );
}
