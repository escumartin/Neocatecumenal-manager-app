import { ListSection } from '@/components/ui/list-section';
import { RadioSection } from '@/components/ui/radio-section';
import { SwitchRow } from '@/components/ui/switch-row';
import { Text } from '@/components/ui/text';
import { membersText } from '@/i18n/es/members';
import { ContactNotice } from './contact-notice';

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
      <Text>{membersText.consent.readNotice}</Text>
      <ContactNotice />
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
