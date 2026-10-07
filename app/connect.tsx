import { WithRail } from '@/components/nav-rail';
import { ConnectScreen } from '@/screens/connect-screen';

export default function ConnectRoute() {
  return (
    <WithRail current={null}>
      <ConnectScreen />
    </WithRail>
  );
}
