import { WithRail } from '@/components/nav-rail';
import { StorageScreen } from '@/screens/storage-screen';

export default function StorageScreenRoute() {
  return (
    <WithRail current="settings">
      <StorageScreen />
    </WithRail>
  );
}
