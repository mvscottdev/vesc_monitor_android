import { WithRail } from '@/components/nav-rail';
import { RideDetailScreen } from '@/screens/ride-detail-screen';

export default function RideDetailScreenRoute() {
  return (
    <WithRail current="history">
      <RideDetailScreen />
    </WithRail>
  );
}
