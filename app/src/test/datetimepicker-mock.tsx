import type { ComponentProps } from 'react';
import { View } from 'react-native';

export default function DateTimePicker(props: ComponentProps<typeof View>) {
  return <View {...props} />;
}

export const DateTimePickerAndroid = { open: jest.fn(), dismiss: jest.fn() };
