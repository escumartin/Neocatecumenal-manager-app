import { Linking, ScrollView, View } from 'react-native';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useMember } from '@/hooks/members/use-member';
import { membersText } from '@/i18n/es/members';
import type { Member, MemberManagementApi } from '@/lib/api/members-api';
import { mailLink, smsLink, telLink } from '@/lib/members/contact-links';
import { fullNameOf } from '@/screens/members/member-name';

type Props = {
  memberId: string;
  api?: Pick<MemberManagementApi, 'get'>;
};

function open(url: string) {
  Linking.openURL(url).catch(() => undefined);
}

export function MemberCardScreen({ memberId, api }: Props) {
  const { status, member, reload } = useMember(memberId, api);

  if (status === 'loading') {
    return (
      <View className="flex-1 bg-background p-4">
        <StateView kind="loading" message={membersText.directory.cardLoading} />
      </View>
    );
  }

  if (status === 'unavailable') {
    return (
      <View className="flex-1 bg-background p-4">
        <StateView kind="empty" icon="person.2" message={membersText.directory.cardUnavailable} />
      </View>
    );
  }

  if (status === 'error' || member === null) {
    return (
      <View className="flex-1 bg-background p-4">
        <StateView
          kind="error"
          message={membersText.directory.cardLoadError}
          retryLabel={membersText.retry}
          onRetry={() => void reload()}
        />
      </View>
    );
  }

  return <Card member={member} />;
}

function Card({ member }: { member: Member }) {
  const name = fullNameOf(member);
  const text = membersText.directory;
  const hasContact = Boolean(member.mobile || member.landline || member.email);

  return (
    <ScrollView
      className="flex-1 bg-background"
      contentContainerClassName="gap-4 p-4"
      contentInsetAdjustmentBehavior="automatic"
    >
      <View className="gap-1">
        <Text variant="title">{name}</Text>
        {member.isResponsible ? <Text className="text-muted-foreground">{membersText.list.responsible}</Text> : null}
      </View>
      {member.mobile ? (
        <ListSection title={text.mobile}>
          <ListRow
            title={text.actionCall}
            subtitle={member.mobile}
            accessory="none"
            accessibilityLabel={text.callMobile(name)}
            onPress={() => open(telLink(member.mobile ?? ''))}
          />
          <ListRow
            title={text.actionText}
            accessory="none"
            accessibilityLabel={text.textMobile(name)}
            onPress={() => open(smsLink(member.mobile ?? ''))}
          />
        </ListSection>
      ) : null}
      {member.landline ? (
        <ListSection title={text.landline}>
          <ListRow
            title={text.actionCall}
            subtitle={member.landline}
            accessory="none"
            accessibilityLabel={text.callLandline(name)}
            onPress={() => open(telLink(member.landline ?? ''))}
          />
        </ListSection>
      ) : null}
      {member.email ? (
        <ListSection title={text.email}>
          <ListRow
            title={text.actionWrite}
            subtitle={member.email}
            accessory="none"
            accessibilityLabel={text.writeEmail(name)}
            onPress={() => open(mailLink(member.email ?? ''))}
          />
        </ListSection>
      ) : null}
      {hasContact ? null : <StateView kind="empty" icon="person.2" message={text.noContact} />}
    </ScrollView>
  );
}
