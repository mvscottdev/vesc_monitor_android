import { WithRail } from '@/components/nav-rail';
import { HistoryScreen } from '@/screens/history-screen';

export default function HistoryScreenRoute() {
  return (
    <WithRail current="history">
      <HistoryScreen />
    </WithRail>
  );
}
