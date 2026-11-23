import { Pressable, Text, View } from 'react-native';

export function Home() {
  return (
    <View className="flex-1 justify-center gap-4 bg-background p-6">
      <Text accessibilityRole="header" className="font-serif text-display text-foreground">
        Neocatecumenal Manager
      </Text>
      <Text className="text-body text-muted-foreground">Comunidades, salones y eventos de tu parroquia.</Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Empezar"
        className="min-h-12 items-center justify-center rounded-md bg-primary px-6"
      >
        <Text className="text-body font-semibold text-primary-foreground">Empezar</Text>
      </Pressable>
    </View>
  );
}
