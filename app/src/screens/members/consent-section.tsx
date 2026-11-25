import { ListSection } from '@/components/ui/list-section';
import { RadioSection } from '@/components/ui/radio-section';
import { SwitchRow } from '@/components/ui/switch-row';
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
        <SwitchRow label={membersText.consent.noticeGiven} value={noticeGiven} onValueChange={onNoticeGiven} />
      </ListSection>
    </>
  );
}
