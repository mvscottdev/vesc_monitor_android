import { WithRail } from '@/components/nav-rail';
import { SettingsScreen } from '@/screens/settings-screen';

export default function SettingsScreenRoute() {
  return (
    <WithRail current="settings">
      <SettingsScreen />
    </WithRail>
  );
}
