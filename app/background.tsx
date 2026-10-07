import { WithRail } from '@/components/nav-rail';
import { BackgroundScreen } from '@/screens/background-screen';

export default function BackgroundScreenRoute() {
  return (
    <WithRail current="settings">
      <BackgroundScreen />
    </WithRail>
  );
}
