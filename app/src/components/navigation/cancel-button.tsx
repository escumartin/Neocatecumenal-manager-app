import { useRouter } from 'expo-router';
import { navigationText } from '@/i18n/es/navigation';
import { HeaderButton } from './header-button';

export function CancelButton() {
  const router = useRouter();
  return <HeaderButton label={navigationText.cancel} onPress={() => router.back()} />;
}
