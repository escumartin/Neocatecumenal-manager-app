import { NativeTabs } from 'expo-router/native-tabs';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { navigationText } from '@/i18n/es/navigation';
import { navigationFor, type TabName } from '@/lib/auth/navigation-permissions';

const symbols: Record<
  TabName,
  {
    default: 'calendar' | 'tray.full' | 'gearshape' | 'person.3' | 'person.crop.circle';
    selected: 'calendar' | 'tray.full.fill' | 'gearshape.fill' | 'person.3.fill' | 'person.crop.circle.fill';
  }
> = {
  agenda: { default: 'calendar', selected: 'calendar' },
  reservas: { default: 'tray.full', selected: 'tray.full.fill' },
  gestion: { default: 'gearshape', selected: 'gearshape.fill' },
  comunidad: { default: 'person.3', selected: 'person.3.fill' },
  cuenta: {
    default: 'person.crop.circle',
    selected: 'person.crop.circle.fill',
  },
};

const materialIcons: Record<
  TabName,
  { default: 'calendar_month' | 'inbox' | 'settings' | 'groups' | 'account_circle' }
> = {
  agenda: { default: 'calendar_month' },
  reservas: { default: 'inbox' },
  gestion: { default: 'settings' },
  comunidad: { default: 'groups' },
  cuenta: { default: 'account_circle' },
};

const allTabs: TabName[] = ['agenda', 'reservas', 'gestion', 'comunidad', 'cuenta'];

export default function TabsLayout() {
  const { user } = useCurrentUser();
  const { tabs } = navigationFor(user?.role);

  return (
    <NativeTabs>
      {allTabs.map((tab) => (
        <NativeTabs.Trigger key={tab} name={tab} hidden={!tabs.includes(tab)}>
          <NativeTabs.Trigger.Label>{navigationText.tabs[tab]}</NativeTabs.Trigger.Label>
          <NativeTabs.Trigger.Icon sf={symbols[tab]} md={materialIcons[tab].default} />
        </NativeTabs.Trigger>
      ))}
    </NativeTabs>
  );
}
