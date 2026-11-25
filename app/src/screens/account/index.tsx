import { ScrollView, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
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
      contentContainerClassName="gap-4 p-4"
    >
      {me ? (
        <View className="gap-1">
          <Text variant="title">{me.name}</Text>
          <Text variant="muted">{usersText.roles[me.role]}</Text>
          <Text variant="muted">{communitiesText.option(me.communityNumber)}</Text>
        </View>
      ) : null}
      <Button variant="outline" accessibilityLabel={accountText.changePassword} onPress={onChangePassword}>
        <Text>{accountText.changePassword}</Text>
      </Button>
      <Button variant="outline" accessibilityLabel={identity.logout} onPress={logOut}>
        <Text>{identity.logout}</Text>
      </Button>
    </ScrollView>
  );
}
