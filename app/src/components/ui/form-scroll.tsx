import type { ReactNode } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView } from 'react-native';

type FormScrollProps = { children: ReactNode };

function FormScroll({ children }: FormScrollProps) {
  const scroll = (
    <ScrollView
      className="flex-1 bg-background"
      contentContainerClassName="gap-6 p-6"
      contentInsetAdjustmentBehavior="automatic"
      automaticallyAdjustKeyboardInsets
      keyboardShouldPersistTaps="handled"
    >
      {children}
    </ScrollView>
  );

  if (Platform.OS === 'android') {
    return (
      <KeyboardAvoidingView behavior="padding" className="flex-1 bg-background">
        {scroll}
      </KeyboardAvoidingView>
    );
  }
  return scroll;
}

export { FormScroll };
