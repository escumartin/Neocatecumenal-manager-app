import { Switch, View } from 'react-native';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { RadioSection } from '@/components/ui/radio-section';
import { membersText } from '@/i18n/es/members';

export type ConsentChannelChoice = 'VERBAL' | 'WRITTEN';

type Props = {
  channel: ConsentChannelChoice | null;
  noticeGiven: boolean;
  onChannel: (channel: ConsentChannelChoice) => void;
  onNoticeGiven: (given: boolean) => void;
};

export function ConsentSection({ channel, noticeGiven, onChannel, onNoticeGiven }: Props) {
  return (
    <>
      <RadioSection
        title={membersText.consent.title}
        value={channel}
        onChange={onChannel}
        options={[
          { value: 'VERBAL', label: membersText.consent.channel.VERBAL },
          { value: 'WRITTEN', label: membersText.consent.channel.WRITTEN },
        ]}
      />
      <ListSection>
        <ListRow title={membersText.consent.noticeGiven}>
          <View className="min-h-12 min-w-12 items-start justify-center">
            <Switch
              role="switch"
              accessibilityLabel={membersText.consent.noticeGiven}
              value={noticeGiven}
              onValueChange={onNoticeGiven}
            />
          </View>
        </ListRow>
      </ListSection>
    </>
  );
}
