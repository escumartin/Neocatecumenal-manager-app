import { View } from 'react-native';
import { Text } from '@/components/ui/text';
import { membersText } from '@/i18n/es/members';

export function ContactNotice() {
  return (
    <View className="gap-3">
      <Text variant="heading">{membersText.contact.notice.title}</Text>
      {membersText.contact.notice.paragraphs.map((paragraph) => (
        <Text key={paragraph}>{paragraph}</Text>
      ))}
    </View>
  );
}
