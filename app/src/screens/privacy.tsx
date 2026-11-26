import { ScrollView } from 'react-native';
import { Text } from '@/components/ui/text';
import { privacy } from '@/i18n/es/privacy';

export function PrivacyPolicy() {
  return (
    <ScrollView className="flex-1 bg-background" contentContainerClassName="gap-6 p-6">
      <Text variant="display">{privacy.title}</Text>
      {privacy.paragraphs.map((paragraph) => (
        <Text key={paragraph}>{paragraph}</Text>
      ))}
    </ScrollView>
  );
}
