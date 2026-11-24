import { createdUserHandoff } from '../created-user-handoff';

const created = {
  id: '6f1c1f1e-0000-4000-8000-000000000021',
  accessId: 482913,
  name: 'Test User',
  role: 'REGISTERED' as const,
  communityId: '6f1c1f1e-0000-4000-8000-000000000011',
  passwordChangePending: true,
  initialPassword: 'abcd234567',
};

describe('created user handoff', () => {
  afterEach(() => createdUserHandoff.clear());

  it('keeps the created user until it is cleared', () => {
    createdUserHandoff.set(created);

    expect(createdUserHandoff.peek()).toEqual(created);
    expect(createdUserHandoff.peek()).toEqual(created);
  });

  it('forgets the initial password once cleared', () => {
    createdUserHandoff.set(created);

    createdUserHandoff.clear();

    expect(createdUserHandoff.peek()).toBeNull();
  });

  it('reports a creation unless a password reset was handed off', () => {
    createdUserHandoff.set(created);

    expect(createdUserHandoff.kind()).toBe('created');
  });

  it('reports a password reset when it was handed off as one', () => {
    createdUserHandoff.set(created, 'reset');

    expect(createdUserHandoff.kind()).toBe('reset');
  });

  it('goes back to a creation once cleared', () => {
    createdUserHandoff.set(created, 'reset');

    createdUserHandoff.clear();

    expect(createdUserHandoff.kind()).toBe('created');
  });

  it('hands the created user over once and forgets it', () => {
    createdUserHandoff.set(created, 'reset');

    expect(createdUserHandoff.take()).toEqual({ created, kind: 'reset' });
    expect(createdUserHandoff.take()).toBeNull();
    expect(createdUserHandoff.peek()).toBeNull();
  });

  it('has nothing to hand over when nothing was set', () => {
    expect(createdUserHandoff.take()).toBeNull();
  });
});
