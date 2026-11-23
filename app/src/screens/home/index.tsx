import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';

export function Home() {
  return (
    <View className="flex-1 justify-center gap-4 bg-background p-6">
      <Text variant="display">Neocatecumenal Manager</Text>
      <Text className="text-muted-foreground">Comunidades, salones y eventos de tu parroquia.</Text>
      <Button accessibilityLabel="Empezar">
        <Text>Empezar</Text>
      </Button>
    </View>
  );
}
