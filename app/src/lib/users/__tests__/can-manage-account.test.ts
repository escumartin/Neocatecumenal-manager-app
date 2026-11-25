import { canManageAccount } from '../can-manage-account';

const ownId = '6f1c1f1e-0000-4000-8000-000000000001';
const otherId = '6f1c1f1e-0000-4000-8000-000000000002';

describe('can manage account', () => {
  it('lets a moderator manage a registered user', () => {
    expect(canManageAccount({ userId: ownId, role: 'MODERATOR' }, { id: otherId, role: 'REGISTERED' })).toBe(true);
  });

  it('does not let a moderator manage another moderator or an administrator', () => {
    expect(canManageAccount({ userId: ownId, role: 'MODERATOR' }, { id: otherId, role: 'MODERATOR' })).toBe(false);
    expect(canManageAccount({ userId: ownId, role: 'MODERATOR' }, { id: otherId, role: 'ADMINISTRATOR' })).toBe(false);
  });

  it('lets an administrator manage any other account', () => {
    for (const role of ['REGISTERED', 'MODERATOR', 'ADMINISTRATOR'] as const) {
      expect(canManageAccount({ userId: ownId, role: 'ADMINISTRATOR' }, { id: otherId, role })).toBe(true);
    }
  });

  it('never lets anyone manage the own account', () => {
    expect(canManageAccount({ userId: ownId, role: 'ADMINISTRATOR' }, { id: ownId, role: 'REGISTERED' })).toBe(false);
    expect(canManageAccount({ userId: ownId, role: 'MODERATOR' }, { id: ownId, role: 'REGISTERED' })).toBe(false);
  });

  it('does not let a registered user or an unknown session manage accounts', () => {
    expect(canManageAccount({ userId: ownId, role: 'REGISTERED' }, { id: otherId, role: 'REGISTERED' })).toBe(false);
    expect(canManageAccount(null, { id: otherId, role: 'REGISTERED' })).toBe(false);
  });
});
