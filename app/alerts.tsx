import { WithRail } from '@/components/nav-rail';
import { AlertsScreen } from '@/screens/alerts-screen';

export default function AlertsScreenRoute() {
  return (
    <WithRail current="alerts">
      <AlertsScreen />
    </WithRail>
  );
}
