import { ScrollView } from 'react-native';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { useSession } from '@/hooks/session/use-session';
import { accountText } from '@/i18n/es/account';
import { communitiesText } from '@/i18n/es/communities';
import { identity } from '@/i18n/es/identity';
import { usersText } from '@/i18n/es/users';

type Props = {
  onChangePassword: () => void;
};

export function Account({ onChangePassword }: Props) {
  const { me, logOut } = useSession();

  return (
    <ScrollView
      className="flex-1 bg-background"
      contentInsetAdjustmentBehavior="automatic"
      contentContainerClassName="gap-6 p-4"
    >
      {me ? (
        <ListSection>
          <ListRow
            title={me.name}
            subtitle={usersText.roles[me.role]}
            trailing={communitiesText.option(me.communityNumber)}
          />
        </ListSection>
      ) : null}
      <ListSection>
        <ListRow title={accountText.changePassword} icon="key" onPress={onChangePassword} />
        <ListRow title={identity.logout} icon="rectangle.portrait.and.arrow.right" accessory="none" onPress={logOut} />
      </ListSection>
    </ScrollView>
  );
}
