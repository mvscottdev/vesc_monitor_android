import { failureCard } from '../failure';

describe('failureCard', () => {
  it('has nothing to show after a disconnect the rider asked for', () => {
    expect(failureCard('user_disconnect')).toBeNull();
    expect(failureCard(null)).toBeNull();
  });

  it('sends Bluetooth and permission failures to the right settings', () => {
    expect(failureCard('bluetooth_off')?.action).toBe('bluetooth');
    expect(failureCard('permission_connect')?.action).toBe('settings');
    expect(failureCard('module_not_found')?.action).toBe('retry');
  });

  it('falls back to a generic card for an unknown code', () => {
    expect(failureCard('something_new')?.title).toBe('Connection failed');
  });
});
