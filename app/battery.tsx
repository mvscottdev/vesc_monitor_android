import { WithRail } from '@/components/nav-rail';
import { BatteryScreen } from '@/screens/battery-screen';

export default function BatteryScreenRoute() {
  return (
    <WithRail current="vehicle">
      <BatteryScreen />
    </WithRail>
  );
}
