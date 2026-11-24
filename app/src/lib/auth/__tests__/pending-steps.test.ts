import { routeForPendingSteps, stepForCode } from '../pending-steps';

describe('pending steps', () => {
  it('has no route when nothing is pending', () => {
    expect(routeForPendingSteps([])).toBeNull();
  });

  it('routes to the privacy notice when it is pending', () => {
    expect(routeForPendingSteps(['PRIVACY_NOTICE'])).toBe('/privacy-notice');
  });

  it('routes to the password change when only that is pending', () => {
    expect(routeForPendingSteps(['PASSWORD_CHANGE'])).toBe('/change-password');
  });

  it('puts the privacy notice before the password change whatever the order', () => {
    expect(routeForPendingSteps(['PASSWORD_CHANGE', 'PRIVACY_NOTICE'])).toBe('/privacy-notice');
  });

  it.each([
    ['PRIVACY_NOTICE_PENDING', 'PRIVACY_NOTICE'],
    ['PRIVACY_NOTICE_OUTDATED', 'PRIVACY_NOTICE'],
    ['PASSWORD_CHANGE_REQUIRED', 'PASSWORD_CHANGE'],
  ] as const)('maps the server code %s to the step %s', (code, step) => {
    expect(stepForCode(code)).toBe(step);
  });
});
