import { useState } from 'react';
import { Linking, ScrollView } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { compatibility } from '@/i18n/es/compatibility';

type UpdateRequiredScreenProps = {
  storeUrl?: string;
};

export function UpdateRequiredScreen({ storeUrl }: UpdateRequiredScreenProps) {
  const [openFailed, setOpenFailed] = useState(false);

  function openStore(url: string) {
    setOpenFailed(false);
    Linking.openURL(url).catch(() => setOpenFailed(true));
  }

  return (
    <ScrollView className="flex-1 bg-background" contentContainerClassName="flex-grow justify-center gap-6 p-6">
      <Text variant="display">{compatibility.title}</Text>
      <Text role="alert" accessibilityLiveRegion="assertive">
        {compatibility.message}
      </Text>
      {storeUrl ? (
        <Button accessibilityLabel={compatibility.openStore} onPress={() => openStore(storeUrl)}>
          <Text>{compatibility.openStore}</Text>
        </Button>
      ) : (
        <Text>{compatibility.searchInStore}</Text>
      )}
      {openFailed ? <Text role="alert">{compatibility.openStoreFailed}</Text> : null}
    </ScrollView>
  );
}
